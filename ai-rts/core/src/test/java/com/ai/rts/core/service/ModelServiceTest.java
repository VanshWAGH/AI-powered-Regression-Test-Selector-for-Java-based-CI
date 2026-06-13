package com.ai.rts.core.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai.rts.core.model.RecommendationModels.FeatureVector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class ModelServiceTest {
    @Test
    void usesOnnxWhenModelPresent() {
        ModelService service = new ModelService(
                new SimpleMeterRegistry(),
                new DefaultResourceLoader(),
                "classpath:models/rts-v1.onnx",
                true);
        service.loadModel();

        double[] hot = new double[12];
        hot[0] = 0.95;
        hot[11] = 0.9;
        hot[9] = 1.0;
        Map<String, Double> scores = service.score(List.of(new FeatureVector("H#m", "H", "m", hot, 500)));
        assertTrue(service.isOnnxActive());
        assertTrue(scores.get("H#m") > 0.4);
    }

    @Test
    void fallsBackWhenModelMissing() {
        ModelService service = new ModelService(
                new SimpleMeterRegistry(),
                new DefaultResourceLoader(),
                "classpath:models/does-not-exist.onnx",
                true);
        service.loadModel();

        Map<String, Double> scores = service.score(List.of(new FeatureVector("X#m", "X", "m", new double[12], 100)));
        assertFalse(service.isOnnxActive());
        assertTrue(scores.containsKey("X#m"));
    }
}
