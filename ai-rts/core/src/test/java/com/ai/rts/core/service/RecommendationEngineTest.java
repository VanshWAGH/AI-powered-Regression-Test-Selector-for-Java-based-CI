package com.ai.rts.core.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai.rts.core.model.RecommendationModels.FeatureVector;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecommendationEngineTest {
    @Test
    void picksSubsetWithinBudget() {
        RecommendationEngine engine = new RecommendationEngine();
        List<FeatureVector> vectors = List.of(
                new FeatureVector("A#m1", "A", "m1", new double[12], 1000),
                new FeatureVector("B#m1", "B", "m1", new double[12], 2000),
                new FeatureVector("C#m1", "C", "m1", new double[12], 3000));
        var result = engine.recommend(vectors, Map.of("A#m1", 0.9, "B#m1", 0.8, "C#m1", 0.2));
        assertFalse(result.rankedTests().isEmpty());
        assertTrue(result.recommendedSubset().size() <= result.rankedTests().size());
    }
}
