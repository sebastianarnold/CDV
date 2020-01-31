package de.datexis.reader;


import com.google.common.collect.Lists;
import de.datexis.cdv.reader.MedQuADReader;
import de.datexis.cdv.retrieval.EntityAspectQueryAnnotation;
import de.datexis.common.Resource;
import de.datexis.model.*;
import de.datexis.retrieval.model.RelevanceResult;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MedQuADReaderTest {
  
  public MedQuADReaderTest() {
  }
  
  @Test
  public void testMedQuADDataset() throws IOException {
    Resource dataset = Resource.fromJAR("testdata/2_GARD_QA/0002717.xml");
    Dataset data = new MedQuADReader().read(dataset);
    
    assertEquals(1, data.countDocuments());
    Document doc = data.getDocument(0).get();
    assertEquals("GARD_0002717", doc.getId());
    assertEquals("GARD", (String) doc.getType());
    assertEquals("https://rarediseases.info.nih.gov/gard/288/hallermann-streiff-syndrome", doc.getSource());
    assertEquals("Hallermann-Streiff syndrome", doc.getTitle());
  
    // plain text documents
    assertTrue(doc.getText().startsWith("Hallermann-Streiff syndrome is a rare, congenital condition"));
    assertEquals(101, doc.countSentences());
    
    // section annotations
    assertEquals(6, doc.countAnnotations());
    
    // questions
    assertEquals(6,data.countQueries());
    List<Query> queries = Lists.newArrayList( data.getQueries());
    Query q1 = queries.get(0); // data.getQuery("GARD_0002717-1");
    // assertEquals("GARD_0002717-1", q1.getId()); // original IDs are not assigned because there might be duplicates
    assertEquals("What is (are) Hallermann-Streiff syndrome ?", q1.getText());
    
    // question annotation?
    assertEquals("information", q1.getAnnotation(EntityAspectQueryAnnotation.class).getAspect());
    assertEquals("Hallermann-Streiff syndrome", q1.getAnnotation(EntityAspectQueryAnnotation.class).getEntity());
    
    // answers
    assertEquals(1, q1.getResults(Annotation.Source.GOLD).size());
    RelevanceResult r1 = q1.streamResults(Annotation.Source.GOLD, RelevanceResult.class).findFirst().get();
    assertEquals(doc, r1.getDocumentRef());
    assertTrue(r1.isRelevant());
    assertEquals(doc.getSentence(0).getBegin(), r1.getBegin());
    assertEquals(doc.getSentence(3).getEnd(), r1.getEnd());
    assertTrue(doc.getText(r1).startsWith("Hallermann-Streiff syndrome is a rare, congenital condition"));
  
    Query q6 = queries.get(5); // data.getQuery("GARD_0002717-6");
    // assertEquals("GARD_0002717-6", q6.getId()); // original IDs are not assigned because there might be duplicates
    assertEquals("What are the treatments for Hallermann-Streiff syndrome ?", q6.getText());
    assertEquals("treatment", q6.getAnnotation(EntityAspectQueryAnnotation.class).getAspect());
    assertEquals("Hallermann-Streiff syndrome", q6.getAnnotation(EntityAspectQueryAnnotation.class).getEntity());
    assertEquals(1, q6.getResults(Annotation.Source.GOLD).size());
    Result r6 = q6.streamResults(Annotation.Source.GOLD).findFirst().get();
    assertEquals(doc, r6.getDocumentRef());
    assertEquals(doc.getEnd(), r6.getEnd());
    assertTrue(doc.getText(r6).startsWith("How might Hallermann-Streiff syndrome be treated? Treatment for"));
    
  }
  
}
