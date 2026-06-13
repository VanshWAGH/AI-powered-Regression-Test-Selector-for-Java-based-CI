package com.ai.rts.core.ml;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.ai.rts.core.model.RecommendationModels.FeatureVector;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

/**
 * Loads an ONNX classifier and scores tests. Supports classpath:, file:, and Spring {@link Resource} paths.
 */
public final class OnnxModelRunner implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(OnnxModelRunner.class);

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final String inputName;
    private final String probabilityOutputName;

    public OnnxModelRunner(Resource modelResource) throws Exception {
        this.environment = OrtEnvironment.getEnvironment();
        byte[] modelBytes = readAllBytes(modelResource);
        this.session = environment.createSession(modelBytes, new OrtSession.SessionOptions());
        this.inputName = session.getInputNames().iterator().next();
        this.probabilityOutputName = resolveProbabilityOutput(session);
        log.info("ONNX model loaded; spec={} input={} probOutput={}",
                FeatureSpec.VERSION, inputName, probabilityOutputName);
    }

    public boolean isReady() {
        return session != null;
    }

    public Map<String, Double> score(List<FeatureVector> vectors) throws OrtException {
        if (vectors.isEmpty()) {
            return Map.of();
        }
        int rows = vectors.size();
        float[] flat = new float[rows * FeatureSpec.FEATURE_COUNT];
        for (int i = 0; i < rows; i++) {
            double[] values = vectors.get(i).values();
            if (values.length != FeatureSpec.FEATURE_COUNT) {
                throw new IllegalArgumentException("Expected " + FeatureSpec.FEATURE_COUNT + " features, got " + values.length);
            }
            int offset = i * FeatureSpec.FEATURE_COUNT;
            for (int j = 0; j < FeatureSpec.FEATURE_COUNT; j++) {
                flat[offset + j] = (float) values[j];
            }
        }

        long[] shape = new long[] {rows, FeatureSpec.FEATURE_COUNT};
        try (OnnxTensor input = OnnxTensor.createTensor(environment, FloatBuffer.wrap(flat), shape);
                OrtSession.Result outputs = session.run(Map.of(inputName, input))) {
            float[][] probabilities = readPositiveClassProbabilities(outputs);
            Map<String, Double> scores = new LinkedHashMap<>(rows);
            double confidenceSum = 0.0;
            for (int i = 0; i < rows; i++) {
                double p = clampProbability(probabilities[i]);
                scores.put(vectors.get(i).testId(), p);
                confidenceSum += p;
            }
            double meanConfidence = confidenceSum / rows;
            log.info("ONNX inference complete: tests={} meanConfidence={}", rows, String.format("%.4f", meanConfidence));
            return scores;
        }
    }

    private static String resolveProbabilityOutput(OrtSession session) {
        Optional<String> prob = session.getOutputNames().stream()
                .filter(name -> name.toLowerCase().contains("prob"))
                .findFirst();
        if (prob.isPresent()) {
            return prob.get();
        }
        return session.getOutputNames().iterator().next();
    }

    private float[][] readPositiveClassProbabilities(OrtSession.Result outputs) throws OrtException {
        OnnxValue value = outputs.get(probabilityOutputName)
                .orElseThrow(() -> new OrtException("Missing ONNX output: " + probabilityOutputName));
        Object raw = value.getValue();
        if (raw instanceof List<?> list) {
            float[][] matrix = new float[list.size()][];
            for (int i = 0; i < list.size(); i++) {
                matrix[i] = positiveProbabilitiesFromZipMapEntry(list.get(i));
            }
            return matrix;
        }
        if (raw instanceof Map<?, ?> map) {
            return new float[][] {positiveProbabilitiesFromZipMapEntry(map)};
        }
        return toRowMatrix(raw);
    }

    private static float[] positiveProbabilitiesFromZipMapEntry(Object entry) {
        if (entry instanceof Map<?, ?> map) {
            Object positive = map.get(1L);
            if (positive == null) {
                positive = map.get(1);
            }
            if (positive == null) {
                var it = map.values().iterator();
                if (it.hasNext()) {
                    it.next();
                }
                positive = it.hasNext() ? it.next() : 0.0f;
            }
            return new float[] {toFloat(positive), toFloat(positive)};
        }
        if (entry instanceof float[] arr) {
            return arr;
        }
        if (entry instanceof double[] arr) {
            float[] out = new float[arr.length];
            for (int i = 0; i < arr.length; i++) {
                out[i] = (float) arr[i];
            }
            return out;
        }
        throw new IllegalStateException("Unsupported ZipMap entry type: " + entry.getClass());
    }

    private static float toFloat(Object value) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        return 0.0f;
    }

    private static float[][] toRowMatrix(Object raw) {
        if (raw instanceof float[][] matrix) {
            return matrix;
        }
        if (raw instanceof float[] vector) {
            float[][] out = new float[vector.length][1];
            for (int i = 0; i < vector.length; i++) {
                out[i][0] = vector[i];
            }
            return out;
        }
        if (raw instanceof double[][] matrix) {
            float[][] out = new float[matrix.length][matrix[0].length];
            for (int i = 0; i < matrix.length; i++) {
                for (int j = 0; j < matrix[i].length; j++) {
                    out[i][j] = (float) matrix[i][j];
                }
            }
            return out;
        }
        throw new IllegalStateException("Unsupported ONNX probability output type: " + raw.getClass());
    }

    private static double clampProbability(float[] row) {
        float p = row.length > 1 ? row[1] : row[0];
        if (p < 0.0f) {
            return 0.0;
        }
        if (p > 1.0f) {
            return 0.999;
        }
        return p;
    }

    private static byte[] readAllBytes(Resource resource) throws Exception {
        if (resource.isFile()) {
            return Files.readAllBytes(resource.getFile().toPath());
        }
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        }
    }

    /** For tests: load from filesystem path. */
    public static OnnxModelRunner fromPath(Path path) throws Exception {
        return new OnnxModelRunner(new org.springframework.core.io.FileSystemResource(path));
    }

    @Override
    public void close() throws OrtException {
        if (session != null) {
            session.close();
        }
    }
}
