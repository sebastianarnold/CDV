package de.datexis.heatmap.reader;

import de.datexis.common.Resource;
import de.datexis.model.Annotation;
import de.datexis.model.Document;
import de.datexis.model.impl.PassageAnnotation;
import de.datexis.preprocess.DocumentFactory;
import de.datexis.reader.RawTextDatasetReader;
import org.apache.commons.io.LineIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads text files with raw text and optional "========== Heading" segmentation.
 * @author Sebastian Arnold <sarnold@beuth-hochschule.de>
 */
public class SegmentedTextDatasetReader extends RawTextDatasetReader {
  
  protected final Logger log = LoggerFactory.getLogger(getClass());
  
  protected Pattern SECTION_PATTERN = Pattern.compile("^==========(.+)?$");
  
  /**
   * Read a single Document from file.
   */
  @Override
  public Document readDocumentFromFile(Resource file) throws IOException {
    try(InputStream in = file.getInputStream()) {
      
      CharsetDecoder utf8 = StandardCharsets.UTF_8.newDecoder();
      BufferedReader br = new BufferedReader(new InputStreamReader(in, utf8));
      Iterator<String> it = new LineIterator(br);
      String line;
      StringBuilder text = new StringBuilder();
      PassageAnnotation ann = new PassageAnnotation(Annotation.Source.GOLD);
      String heading = "";
  
      Document doc = new Document();
      doc.setId(file.getFileName());
      doc.setSource(file.toString());
      doc.setType(file.getPath().getParent().getFileName().toString());
  
      if(useFirstSentenceAsTitle && it.hasNext()) {
        line = it.next();
        doc.setTitle(line.trim());
      }
      
      while(it.hasNext()) {
  
        line = it.next();
        Matcher matcher = SECTION_PATTERN.matcher(line);
  
        if(line.startsWith("==========") && matcher.matches()) {
          // end the current section
          String sectionText = text.toString();
          if(sectionText.trim().length() > 0) {
            addToDocument(sectionText, heading, doc);
          }
          // start new section
          text = new StringBuilder();
          heading = matcher.group(1) != null ? matcher.group(1).trim() : "";
        } else {
          if(!line.trim().isEmpty()) {
            text.append(line).append("\n");
          }
        }
        
      }
  
      // end the last section
      String sectionText = text.toString();
      if(sectionText.trim().length() > 0) {
        addToDocument(sectionText, heading, doc);
      }
        
      return doc;
      
    }
  }
  
  private void addToDocument(String text, String heading, Document doc) {
    if(text.trim().length() == 0) return;
    //Document section = DocumentFactory.fromText(text, DocumentFactory.Newlines.DISCARD);
    Document section = isTokenized ?
      DocumentFactory.fromTokenizedText(text) :
      DocumentFactory.fromText(text, DocumentFactory.Newlines.KEEP);
    doc.append(section); // will assign correct begin/end
    PassageAnnotation ann = new PassageAnnotation(Annotation.Source.GOLD);
    ann.setLabel(heading);
    ann.setBegin(section.getBegin());
    ann.setEnd(section.getEnd());
    doc.addAnnotation(ann);
  }
  
}
