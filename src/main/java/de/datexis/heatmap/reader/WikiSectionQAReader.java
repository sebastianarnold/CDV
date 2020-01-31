package de.datexis.heatmap.reader;

import de.datexis.common.ObjectSerializer;
import de.datexis.common.Resource;
import de.datexis.heatmap.model.AspectAnnotation;
import de.datexis.heatmap.model.EntityAnnotation;
import de.datexis.heatmap.model.EntityAspectAnnotation;
import de.datexis.heatmap.retrieval.EntityAspectQueryAnnotation;
import de.datexis.model.*;
import de.datexis.model.impl.PassageAnnotation;
import de.datexis.preprocess.DocumentFactory;
import de.datexis.retrieval.model.RelevanceResult;
import de.datexis.retrieval.preprocess.WikipediaUrlPreprocessor;
import de.datexis.sector.model.SectionAnnotation;
import de.datexis.sector.model.WikiDocument;
import de.datexis.sector.reader.WikiSectionReader;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author Sebastian Arnold <sarnold@beuth-hochschule.de>
 */
public class WikiSectionQAReader extends WikiSectionReader {
  
  protected final Logger log = LoggerFactory.getLogger(getClass());
  
  /** Map of all UMLS URL->QID and name->URI to map to a different ID scheme (e.g. Wikidata) */
  Map<String, String> idMap = null;
  
  Map<String, String> questions = new TreeMap<>();
  
  public WikiSectionQAReader() {
    questions.put("information", "What is");
    questions.put("treatment", "What are treatments for");
    questions.put("symptom", "What are the symptoms of");
    questions.put("epidemiology", "What is the epidemiology of");
    questions.put("diagnosis", "How to diagnose");
    questions.put("cause", "What are causes of");
    questions.put("mechanism", "What is the mechanism of");
    questions.put("research", "What research exists about");
    questions.put("history", "What is the history of");
    questions.put("classification", "How to classify");
    questions.put("prognosis", "What is the prognosis of");
    questions.put("pathophysiology", "What is the pathophysiology of");
    questions.put("infection", "What are possible infections for");
    questions.put("fauna", "How are animals affected by");
    questions.put("prevention", "How to prevent");
    questions.put("screening", "What screening methods exist for");
    questions.put("etymology", "What is the etymology of");
    questions.put("medication", "What are medication for");
    questions.put("complication", "What are complications of");
    questions.put("pathology", "What is the pathology of");
    questions.put("genetics", "How do genetic factors affect");
    questions.put("tomography", "What tomography methods exist for");
    questions.put("management", "What are possible managements for");
    questions.put("geography", "What are geographical aspects of");
    questions.put("culture", "What are cultural aspects of");
    questions.put("risk", "What are risks for");
    questions.put("surgery", "What surgery methods exist for");
    questions.put("other", "What else is known about");
  }
  
  /**
   * Load a TSV file that contains mapping of UMLS CUI page to Wikidata IDs.
   */
  public WikiSectionQAReader withIDMapping(Resource file) throws IOException {
    List<String> mapping = FileUtils.readLines(file.toFile(), "UTF-8");
    idMap = new ConcurrentHashMap<>(mapping.size());
    mapping.stream()
      .map(s -> s.split("\\t"))
      .forEach(s -> idMap.put(WikipediaUrlPreprocessor.cleanWikiPageTitle(s[0]), s[1]));
    return this;
  }
  
  @Override
  public Dataset read(Resource path) throws IOException {
    Dataset corpus = new Dataset("WikiSection");
    List<WikiDocument> docs = readWikiDocumentsFromJSON(path);
    for(WikiDocument wiki : docs) {
      // find out Wikidata QIds
      String qid = idMap.get(WikipediaUrlPreprocessor.cleanWikiPageTitle(wiki.getId()));
      if(qid == null) {
        // There are two document in the dataset that we don't use:
        // Tinea - is a copy of Dermatophytosis (nowadays a redirect)
        // Etiology - is not a disease
        log.error("could not find Qid for {}", wiki.getId());
        continue;
      }
      // merge abstract in front
      String abstractText = wiki.getAbstract();
      if(abstractText == null) abstractText = "";
      int offset = abstractText.length();
      Document doc = DocumentFactory.fromText(abstractText + wiki.getText(), DocumentFactory.Newlines.KEEP);
      doc.setTitle(wiki.getTitle());
      doc.setType("en_disease");
      doc.setId(qid);
      String lastLabel = "information";
      int count = 0;
      
      EntityAspectAnnotation ann = new EntityAspectAnnotation(Annotation.Source.GOLD);
      ann.setBegin(doc.getBegin());
      ann.setEntity(wiki.getTitle());
      ann.setEntityId(qid);
      if(!lastLabel.equals("other"))
        ann.setAspect(lastLabel);
      ann.setId(qid + "-" + count++);
  
      Query query = Query.create(questions.get(lastLabel) + " " + wiki.getTitle() + "?");
      EntityAspectQueryAnnotation queryAnn = new EntityAspectQueryAnnotation(wiki.getTitle(), lastLabel);
      queryAnn.setEntityId(qid);
      query.addAnnotation(queryAnn);
      
      // make each passage an EntityAspectAnnotation
      for(SectionAnnotation sec : wiki.getAnnotations(SectionAnnotation.class)) {
        String label = sec.getSectionLabel().replace("disease.", "");
        // merge passages with same label
        if(!label.equals(lastLabel)) {
          // end last label here
          ann.setEnd(sec.getBegin() + offset);
          RelevanceResult resultAnnotation = new RelevanceResult(Annotation.Source.GOLD, doc, ann.getBegin(), ann.getEnd());
          resultAnnotation.setRelevance(1);
          resultAnnotation.setId(ann.getId());
          resultAnnotation.setDocumentRef(doc);
          if(ann.getLength() >= 10) {
            query.addResult(resultAnnotation);
            doc.addAnnotation(ann);
          }
          if(!lastLabel.equals("other") && query.getResults().size() > 0)
            corpus.addQuery(query);
          lastLabel = label;
          
          // start new query and nnotation
          
          ann = new EntityAspectAnnotation(Annotation.Source.GOLD);
          ann.setBegin(sec.getBegin() + offset);
          ann.setEntity(wiki.getTitle());
          ann.setEntityId(qid);
          if(!label.equals("other"))
            ann.setAspect(label);
          ann.setId(qid + "-" + count++);
          
          query = Query.create(questions.get(label) + " " + wiki.getTitle() + "?");
          queryAnn = new EntityAspectQueryAnnotation(wiki.getTitle(), label);
          queryAnn.setEntityId(qid);
          query.addAnnotation(queryAnn);
          
        }
      }
      // end last label here
      ann.setEnd(doc.getEnd());
      RelevanceResult resultAnnotation = new RelevanceResult(Annotation.Source.GOLD, doc, ann.getBegin(), ann.getEnd());
      resultAnnotation.setRelevance(1);
      resultAnnotation.setId(ann.getId());
      resultAnnotation.setDocumentRef(doc);
      if(ann.getLength() >= 10) {
        query.addResult(resultAnnotation);
        doc.addAnnotation(ann);
      }
      if(!lastLabel.equals("other") && query.getResults().size() > 0)
        corpus.addQuery(query);
      
      corpus.addDocument(doc);
      
    }
    return corpus;
  }
  
  public Dataset readWikidata(Resource path, boolean generateNegativeSamples) throws IOException {
    Dataset result = new Dataset();
    Iterator<Document> it = ObjectSerializer.readJSONDocumentIterable(path);
    while(it.hasNext()) {
      Document doc = it.next();
      String qid = idMap.get(WikipediaUrlPreprocessor.cleanWikiPageTitle(doc.getId()));
      // clean up annotations so that we have a stream of passages
      for(EntityAnnotation ann : doc.getAnnotations(Annotation.Source.GOLD, EntityAnnotation.class)) {
        doc.removeAnnotation(ann);
      }
      int count = 0;
      int cursor = 0;
      List<AspectAnnotation> anns = doc.streamAnnotations(Annotation.Source.GOLD, AspectAnnotation.class)
        .sorted()
        .collect(Collectors.toList());
      for(AspectAnnotation ann : anns) {
        // don't skip subsections
        //if(ann.getBegin() < cursor) continue;
        cursor = ann.getEnd();
        ann.setId(qid + "-" + count++);
        String label = ann.getLabel().replace(";", " ").toLowerCase();
        if(label.equals("abstract")) label = "information";
        Query query = Query.create(doc.getTitle() + " ; " + label);
        EntityAspectQueryAnnotation queryAnn = new EntityAspectQueryAnnotation(doc.getTitle(), label);
        queryAnn.setEntityId(qid);
        query.addAnnotation(queryAnn);
        RelevanceResult resultAnnotation = new RelevanceResult(Annotation.Source.GOLD, doc, ann.getBegin(), ann.getEnd());
        resultAnnotation.setRelevance(1);
        resultAnnotation.setId(ann.getId());
        resultAnnotation.setDocumentRef(doc);
        query.addResult(resultAnnotation);
        result.addQuery(query);
      }
      result.addDocument(doc);
    }
    if(generateNegativeSamples) {
      Random random = new Random();
      for(Query query : result.getQueries()) {
        // fill up with up to 10 random candidate passages
        Result matched = query.getResults().get(0);
        while(query.getResults().size() < 10) {
          de.datexis.model.Document doc = result.getRandomDocument().get();
          List<PassageAnnotation> anns = doc.streamAnnotations(Annotation.Source.GOLD, PassageAnnotation.class, true).collect(Collectors.toList());
          int idx = random.nextInt(anns.size());
          PassageAnnotation ann = anns.get(idx);
          if(ann.getId().equals(matched.getId())) continue; // already contained
          RelevanceResult resultAnnotation = new RelevanceResult(Annotation.Source.SAMPLED, doc, ann.getBegin(), ann.getEnd());
          resultAnnotation.setRelevance(0);
          resultAnnotation.setId(ann.getId());
          resultAnnotation.setDocumentRef(doc);
          query.addResult(resultAnnotation);
        }
      }
    }
    return result;
  }
  
}
