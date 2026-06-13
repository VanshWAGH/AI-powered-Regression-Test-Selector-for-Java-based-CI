package com.ai.rts.core.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai.rts.core.model.RecommendationModels.FeatureVector;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class OnnxModelRunnerTest {
    @Test
    void scoresTestsFromBundledModel() throws Exception {
        try (OnnxModelRunner runner = new OnnxModelRunner(new ClassPathResource("models/rts-v1.onnx"))) {
            double[] features = new double[FeatureSpec.FEATURE_COUNT];
            features[FeatureSpec.RECENCY_FAIL_RATE] = 0.9;
            features[FeatureSpec.PACKAGE_OVERLAP] = 0.8;
            features[FeatureSpec.TAG_CRITICAL] = 1.0;
            List<FeatureVector> vectors = List.of(new FeatureVector("A#m", "A", "m", features, 1000));

            Map<String, Double> scores = runner.score(vectors);
            assertEquals(1, scores.size());
            assertTrue(scores.get("A#m") > 0.5);
        }
    }

    @Test
    void loadsFromFilesystemPath() throws Exception {
        Path path = Path.of("src/test/resources/models/rts-v1.onnx");
        if (!path.toFile().exists()) {
            path = Path.of("core/src/test/resources/models/rts-v1.onnx");
        }
        try (OnnxModelRunner runner = OnnxModelRunner.fromPath(path)) {
            assertTrue(runner.isReady());
        }
    }
}
