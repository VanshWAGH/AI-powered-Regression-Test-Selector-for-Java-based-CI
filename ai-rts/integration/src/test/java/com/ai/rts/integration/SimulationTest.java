package com.ai.rts.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai.rts.core.model.RecommendationModels.FeatureVector;
import com.ai.rts.core.service.RecommendationEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SimulationTest {
    @Test
    void simulatesHundredPrHistorySelection() {
        RecommendationEngine engine = new RecommendationEngine();
        List<FeatureVector> vectors = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            vectors.add(new FeatureVector("T" + i, "TestClass" + i, "testMethod", new double[12], 3000));
        }
        Map<String, Double> scores = vectors.stream().collect(java.util.stream.Collectors.toMap(
                FeatureVector::testId, v -> Math.max(0.01, 1.0 - (v.testId().hashCode() & 1023) / 1024.0)));
        var result = engine.recommend(vectors, scores);
        assertTrue(result.recommendedSubset().size() <= 300);
        assertTrue(result.recommendedSubset().size() >= 100);
    }
}
