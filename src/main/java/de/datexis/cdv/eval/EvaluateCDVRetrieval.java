package de.datexis.cdv.eval;

import de.datexis.annotator.AnnotatorFactory;
import de.datexis.common.ObjectSerializer;
import de.datexis.common.Resource;
import de.datexis.heatmap.HeatmapAnnotator;
import de.datexis.heatmap.eval.CDVErrorAnalysis;
import de.datexis.heatmap.index.AspectIndex;
import de.datexis.heatmap.index.AspectIndexBuilder;
import de.datexis.heatmap.index.EntityIndex;
import de.datexis.heatmap.index.PassageIndex;
import de.datexis.heatmap.reader.MatchZooReader;
import de.datexis.heatmap.retrieval.QueryRunner;
import de.datexis.model.Dataset;
import de.datexis.retrieval.eval.RetrievalEvaluation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * WWW2020: Use pre-trained CDV model for retrieval
 * @author Sebastian Arnold <sarnold@beuth-hochschule.de>
 */
public class EvaluateCDVRetrieval {

  protected final static Logger log = LoggerFactory.getLogger(EvaluateCDVRetrieval.class);
  
  String multiTaskModelDir = "/home/sarnold/Projekte/CDV/Models/191001_1713_CDV-EA@wd_disease+ft-sent+512-256-128+weightdecay+nobalance+50ep_20191001";
  String entityModelDir = "/home/sarnold/TeXoo/Evaluation/CDV2019/190903_1634_CDV-E@wd_disease+ft-sent+ft-lstm+huber+20ep_20190903";
  String aspectModelDir = "/home/sarnold/TeXoo/Evaluation/CDV2019/190904_1635_CDV-A@wd_disease+ft-sent+ft-heading+huber+balance+description+20ep_20190904";
  String encoderDir = "/home/sarnold/Library/Models/FastText";
  String datasetName = "WikiSection";
  String datasetDir = "/home/sarnold/Library/Datasets/Heatmap/MatchZoo/" + datasetName + "-queries-test.json";

  public static void main(String[] args) throws IOException {
    try {
      EvaluateCDVRetrieval eval = new EvaluateCDVRetrieval();
      if(eval.multiTaskModelDir != null) eval.evalMultiTaskRetrieval();
      else eval.evalSingleTaskRetrieval();
    } finally {
    }
  }
  
  public EvaluateCDVRetrieval() {}
  
  public void evalSingleTaskRetrieval() throws IOException {
    
    Resource datasetPath = Resource.fromDirectory(datasetDir);
    Resource entityModelPath = Resource.fromDirectory(entityModelDir);
    Resource aspectModelPath = Resource.fromDirectory(aspectModelDir);
    Resource embeddingPath = Resource.fromDirectory(encoderDir);
    
    // --- load data ---------------------------------------------------------------------------------------------------
    Dataset corpus = ObjectSerializer.readFromJSON(datasetPath, Dataset.class);
    
    // --- load model --------------------------------------------------------------------------------------------------
    HeatmapAnnotator entityAnnotator = (HeatmapAnnotator) AnnotatorFactory.loadAnnotator(entityModelPath, embeddingPath);
    HeatmapAnnotator aspectAnnotator = (HeatmapAnnotator) AnnotatorFactory.loadAnnotator(aspectModelPath, embeddingPath);
    EntityIndex entityIndex = (EntityIndex) entityAnnotator.getEntityEncoder();
    AspectIndex aspectIndex = AspectIndexBuilder.buildAspectIndex(aspectAnnotator.getAspectEncoder(), datasetName);
  
    // entityIndex.clear(); // use fallback encoding for everything
    
    // --- annotate ----------------------------------------------------------------------------------------------------
    entityAnnotator.annotateDocuments(corpus.getDocuments());
    aspectAnnotator.annotateDocuments(corpus.getDocuments());
    
    // --- query ----------------------------------------------------------------------------------------------------
    QueryRunner runner = new QueryRunner(corpus, entityIndex, aspectIndex, QueryRunner.Strategy.PASSAGE_RANK);
    MatchZooReader.addCandidateSamples(corpus, PassageIndex.NUM_CANDIDATES); // adds 64 candidates to be comparable with MatchZoo models
    runner.retrieveAllQueries(QueryRunner.Candidates.GIVEN);
  
    // --- evaluate ----------------------------------------------------------------------------------------------------
    RetrievalEvaluation eval = new RetrievalEvaluation(corpus.getName());
    eval.evaluateQueries(corpus);
  
    aspectAnnotator.getTagger().appendTestLog(eval.printEvaluationStats());
  
    Resource outputPath = aspectModelPath.resolve("eval-passage-ranking-" + datasetName);
    outputPath.toFile().mkdirs();
    aspectAnnotator.writeTestLog(outputPath);
    
    eval.printEvaluationStats();
    
  }
  
  public void evalMultiTaskRetrieval() throws IOException {
    
    Resource datasetPath = Resource.fromDirectory(datasetDir);
    Resource cdvModelPath = Resource.fromDirectory(multiTaskModelDir);
    Resource embeddingPath = Resource.fromDirectory(encoderDir);
    
    // --- load data ---------------------------------------------------------------------------------------------------
    Dataset corpus = ObjectSerializer.readFromJSON(datasetPath, Dataset.class);
    
    // --- load model --------------------------------------------------------------------------------------------------
    HeatmapAnnotator cdv = (HeatmapAnnotator) AnnotatorFactory.loadAnnotator(cdvModelPath, embeddingPath);
    EntityIndex entityIndex = (EntityIndex) cdv.getEntityEncoder();
    AspectIndex aspectIndex = AspectIndexBuilder.buildAspectIndex(cdv.getAspectEncoder(), datasetName);
  
    // entityIndex.clear(); // use fallback encoding for everything
    
    // --- annotate ----------------------------------------------------------------------------------------------------
    cdv.getTagger().setMaxWordsPerSentence(-1);
    cdv.getTagger().setMaxTimeSeriesLength(-1);
    cdv.getTagger().setBatchSize(16);
    cdv.annotateDocuments(corpus.getDocuments());
    
    // --- query ----------------------------------------------------------------------------------------------------
    //corpus.setQueries(corpus.getQueries().stream().limit(128).collect(Collectors.toList()));
    QueryRunner runner = new QueryRunner(corpus, entityIndex, aspectIndex, QueryRunner.Strategy.PASSAGE_RANK);
    MatchZooReader.addCandidateSamples(corpus, PassageIndex.NUM_CANDIDATES); // adds 64 candidates to be comparable with MatchZoo models
    runner.retrieveAllQueries(QueryRunner.Candidates.GIVEN);
    
    // --- evaluate ----------------------------------------------------------------------------------------------------
    RetrievalEvaluation eval = new RetrievalEvaluation(corpus.getName());
    eval.evaluateQueries(corpus);
  
    cdv.getTagger().appendTestLog(eval.printEvaluationStats());
  
    Resource outputPath = cdvModelPath.resolve("eval-passage-ranking-" + datasetName);
    outputPath.toFile().mkdirs();
    
    //CDVErrorAnalysis.evaluateFalsePredictions(corpus.getQueries(), corpus, entityIndex, aspectIndex, outputPath);
    CDVErrorAnalysis.evaluateErrorStatistics(corpus.getQueries(), corpus, entityIndex, aspectIndex, outputPath);
    CDVErrorAnalysis.evaluateSourcePerformance(corpus.getQueries(), corpus, entityIndex, aspectIndex, outputPath);
    cdv.writeTestLog(outputPath);
    
    // TODO mean query time
    
  }
  
}