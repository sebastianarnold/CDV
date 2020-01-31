package de.datexis.cdv.train;

import de.datexis.annotator.Annotator;
import de.datexis.common.CommandLineParser;
import de.datexis.common.Configuration;
import de.datexis.common.Resource;
import de.datexis.common.WordHelpers;
import de.datexis.encoder.impl.BloomEncoder;
import de.datexis.encoder.impl.FastTextEncoder;
import de.datexis.heatmap.encoder.AspectEncoder;
import de.datexis.heatmap.encoder.EntityEncoder;
import de.datexis.heatmap.index.AspectIndex;
import de.datexis.heatmap.index.EntityIndex;
import de.datexis.heatmap.index.QueryIndex;
import de.datexis.retrieval.encoder.LSTMSentenceAnnotator;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.deeplearning4j.api.storage.StatsStorage;
import org.deeplearning4j.ui.api.UIServer;
import org.deeplearning4j.ui.stats.StatsListener;
import org.deeplearning4j.ui.storage.InMemoryStatsStorage;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.impl.LossMultiLabel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;

/**
 * WWW2020: Train Sentence Embeddings
 * @author Sebastian Arnold <sarnold@beuth-hochschule.de>
 */
public class TrainSentenceEmbedding {
  
  protected final static Logger log = LoggerFactory.getLogger(TrainSentenceEmbedding.class);
  
  protected WordHelpers.Language lang;
  // input encoders
  protected FastTextEncoder inputEncoder;
  // target decoders
  protected BloomEncoder targetEncoder;
  
  public static void main(String[] args) throws IOException, ParseException {
    final TrainingParams params = new TrainingParams();
    final CommandLineParser parser = new CommandLineParser(params);
    if(args.length > 0) parser.parse(args);
    new TrainSentenceEmbedding().trainSentenceEmbedding(params);
  }
  
  protected static class TrainingParams implements CommandLineParser.Options {
  
    protected String inputPath = "/mnt/datasets/Heatmap/wd_disease_entity_context_shuffled_clean.tsv";
    protected String datasetName = "en_disease";
    protected String wordEmbedding = "/mnt/models/FastText/en_disease_skipgram.bin";
    protected String outputPath = "/mnt/results/";
    protected String modelName = "+ft-lstm-context+128+5ep";
    protected boolean trainingUI = true;
    protected boolean tokenizedInput = true;
    protected boolean entityModel = true;
    protected int epochs = 2;
  
    @Override
    public void setParams(CommandLine parse) {
      inputPath = parse.getOptionValue("i");
      datasetName = parse.getOptionValue("d");
      modelName = parse.getOptionValue("m");
      wordEmbedding = parse.getOptionValue("w");
      tokenizedInput = parse.hasOption("t");
      trainingUI = parse.hasOption("u");
      entityModel = !parse.hasOption("a");
      outputPath = parse.getOptionValue("o", Configuration.getProperty("de.datexis.path.results"));
    }
  
    @Override
    public Options setUpCliOptions() {
      Options op = new Options();
      op.addRequiredOption("i", "input path", true, "path to the WikiSection training dataset");
      op.addRequiredOption("d", "dataset name", true, "name of the data set, e.g. en_disease");
      op.addRequiredOption("m", "model name", true, "model name");
      op.addOption("w", "word embedding path", true, "path to a pretrained word embedding");
      op.addOption("o", "output path", true, "path to create the output folder in");
      op.addOption("t", "tokenized", false, "use if input is tokenized");
      op.addOption("u", "ui", false, "enable training UI");
      op.addOption("a", "aspect", false, "train aspect model");
      return op;
    }
  
  }
  
  public void trainSentenceEmbedding(TrainingParams params) throws IOException {
  
    Nd4j.setDefaultDataTypes(DataType.FLOAT, DataType.FLOAT);
    
    Resource train = Resource.fromFile(params.inputPath);
    Resource output = Resource.fromDirectory(params.outputPath);
    
    Resource embeddingModel = params.wordEmbedding == null ? null : Resource.fromFile(params.wordEmbedding);
  
    lang = params.datasetName.startsWith("de_") ? WordHelpers.Language.DE : WordHelpers.Language.EN;
  
    if(embeddingModel != null) {
      initializeFastTextEmbedding(embeddingModel);
    }
    
    // Create Bloom encoders for training target (Entity ID or Heading BOW)
    targetEncoder = params.entityModel ?
      new EntityEncoder(1024, lang) :
      new AspectEncoder(1024, lang, 5);
    targetEncoder.trainModel(train);
    
    // --- build model ---------------------------------------------------------
    LSTMSentenceAnnotator embedding = new LSTMSentenceAnnotator.Builder()
      .withId(params.entityModel ? "ENC-E" : "ENC-A")
      .withInputEncoders(params.modelName, inputEncoder)
      .withTargetEncoder(targetEncoder)
      .withLossFunction(new LossMultiLabel(), Activation.SIGMOID)
      .withModelParams(128, 128)
      //.withStopWords(Collections.emptySet())
      .withTrainingParams(0.001, 0.5, -1, 128, params.epochs) // ca 5000 batches
      .withDataset(params.datasetName, lang)
      //.enableTrainingUI(params.trainingUI)
      .build();
  
    if(params.trainingUI) {
      StatsStorage stats = new InMemoryStatsStorage();
      embedding.getTagger().getNN().addListeners(new StatsListener(stats, 1));
      UIServer.getInstance().attach(stats);
      UIServer.getInstance().enableRemoteListener(stats, true);
    }
    
    boolean success = false;
    try {
      Resource outputPath = output.resolve(embedding.getTagger().getName());
      // train embedding model
      embedding.trainModel(train);
      saveModel(embedding, outputPath);
      // generate embedding vectors
      QueryIndex index = params.entityModel ?
        new EntityIndex(embedding.asEncoder()) :
        new AspectIndex(embedding.asEncoder());
      WordHelpers wh = new WordHelpers(lang);
      index.encodeIndexFromSentences(train, Collections.emptySet(), params.tokenizedInput);
      saveIndex(index, outputPath, params.entityModel ? "entity" : "aspect");
      success = true;
    } catch(Throwable e) {
      e.printStackTrace();
    } finally {
      //if(params.trainingUI) UIServer.getInstance().stop();
      System.exit(success ? 0 : 1);
    }
  
  }
  
  private void initializeFastTextEmbedding(Resource wordEmbedding) throws IOException {
    if(wordEmbedding != null) {
      inputEncoder = new FastTextEncoder();
      inputEncoder.loadModelAsReference(wordEmbedding);
    }
  }
  
  private void saveModel(Annotator annotator, Resource outputPath) throws IOException {
    outputPath.toFile().mkdirs();
    annotator.writeModel(outputPath);
    annotator.writeTrainLog(outputPath);
    annotator.writeTestLog(outputPath);
    log.info("model written to {}", outputPath.toString());
  }
  
  private void saveIndex(QueryIndex index, Resource outputPath, String name) throws IOException {
    outputPath.toFile().mkdirs();
    index.saveModel(outputPath, name + ".index");
    index.writeVectors(outputPath, name);
    log.info("index written to {}", outputPath.toString());
  }
  
}