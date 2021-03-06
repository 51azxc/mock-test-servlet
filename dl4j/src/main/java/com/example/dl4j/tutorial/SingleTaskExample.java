package com.example.dl4j.tutorial;

import com.example.dl4j.utilities.DataUtilities;
import org.datavec.api.records.reader.SequenceRecordReader;
import org.datavec.api.records.reader.impl.csv.CSVSequenceRecordReader;
import org.datavec.api.split.NumberedFileInputSplit;
import org.deeplearning4j.datasets.datavec.RecordReaderMultiDataSetIterator;
import org.deeplearning4j.datasets.datavec.SequenceRecordReaderDataSetIterator;
import org.deeplearning4j.eval.ROC;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.GradientNormalization;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.LSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.DataSet;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SingleTaskExample {
    public static void main(String[] args) throws Exception {
        // 下载数据
        String DATA_URL = "https://bpstore1.blob.core.windows.net/tutorials/instacart.tar.gz";
        String DATA_PATH = Paths.get(System.getProperty("java.io.tmpdir"), "dl4j_instacart").toString();
        String localFilePath = Paths.get(DATA_PATH, "instacart.tar.gz").toString();
        if (DataUtilities.downloadFile(DATA_URL, localFilePath)) {
            System.out.println("download file from: " + DATA_URL);
        }

        Path dataPath = Paths.get(DATA_PATH, "instacart");
        if (Files.notExists(dataPath, LinkOption.NOFOLLOW_LINKS)) {
            DataUtilities.extractTarGz(localFilePath, DATA_PATH);
        }

        // 配置数据集
        String featureBaseDir = Paths.get(dataPath.toString(), "features").toString();
        String targetsBaseDir = Paths.get(dataPath.toString(), "breakfast").toString();

        SequenceRecordReader trainFeatures = new CSVSequenceRecordReader(1);
        trainFeatures.initialize(
                new NumberedFileInputSplit(featureBaseDir + "/%d.csv", 1, 4000));
        SequenceRecordReader trainLabels = new CSVSequenceRecordReader(1);
        trainLabels.initialize(
                new NumberedFileInputSplit(targetsBaseDir + "/%d.csv", 1, 4000));
        DataSetIterator train = new SequenceRecordReaderDataSetIterator(trainFeatures, trainLabels, 32,
                2, false, SequenceRecordReaderDataSetIterator.AlignmentMode.ALIGN_END);

        SequenceRecordReader testFeatures = new CSVSequenceRecordReader(1);
        testFeatures.initialize(
                new NumberedFileInputSplit(featureBaseDir + "/%d.csv", 4001, 5000));
        SequenceRecordReader testLabels = new CSVSequenceRecordReader(1);
        testLabels.initialize(
                new NumberedFileInputSplit(targetsBaseDir + "/%d.csv", 4001, 5000));
        DataSetIterator test = new SequenceRecordReaderDataSetIterator(testFeatures, testLabels, 32,
                2, false, SequenceRecordReaderDataSetIterator.AlignmentMode.ALIGN_END);

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT).seed(12345)
                //.dropOut(0.25)
                .weightInit(WeightInit.XAVIER)
                .updater(new Adam())
                .list()
                .layer(0, new LSTM.Builder()
                        .activation(Activation.TANH)
                        .gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue)
                        .gradientNormalizationThreshold(10)
                        .nIn(134)
                        .nOut(150)
                        .build())
                .layer(1, new RnnOutputLayer.Builder(LossFunctions.LossFunction.XENT)
                        .activation(Activation.SOFTMAX)
                        .nIn(150)
                        .nOut(2)
                        .build())
                .pretrain(false).backprop(true)
                .build();

        MultiLayerNetwork model = new MultiLayerNetwork(conf);
        model.init();

        for (int i = 1; i <= 5; i++) {
            System.out.println("Epoch " + i);
            model.fit(train);
            train.reset();
        }

        ROC roc = new ROC(100);
        test.reset();

        while(test.hasNext()){
            DataSet next = test.next();
            INDArray features =  next.getFeatures();
            INDArray output = model.output(features);
            roc.evalTimeSeries(next.getLabels(), output);
        }

        System.out.println(roc.calculateAUC());
    }
}
