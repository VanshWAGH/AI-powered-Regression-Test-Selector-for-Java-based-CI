package com.ai.rts.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai.rts.core.domain.CodeChange;
import com.ai.rts.core.domain.TestMetadata;
import com.ai.rts.core.domain.TestRun;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeatureExtractorTest {
    @Test
    void buildsTwelveFeaturesPerTest() {
        FeatureExtractor extractor = new FeatureExtractor();
        List<TestMetadata> metadata = List.of(new TestMetadata("repo", "com.example.A", "m1", "critical", "integration", 1200));
        List<TestRun> runs = List.of(
                new TestRun("repo", "com.example.A#m1", "FAILED", 1000, Instant.now(), "12"),
                new TestRun("repo", "com.example.A#m1", "PASSED", 900, Instant.now(), "13"));
        List<CodeChange> changes = List.of(new CodeChange("src/main/java/com/example/A.java", 5, 2, "x"));

        var vectors = extractor.buildFeatures(metadata, runs, changes);
        assertEquals(1, vectors.size());
        assertEquals(12, vectors.get(0).values().length);
        assertTrue(vectors.get(0).values()[0] > 0.0);
        assertTrue(vectors.get(0).values()[11] > 0.0);
    }

    @Test
    void packageOverlapDetectsChangedPackage() {
        double overlap = FeatureExtractor.packageOverlap(
                "com.example.service.OrderServiceTest",
                List.of(new CodeChange("src/main/java/com/example/service/OrderService.java", 1, 0, "")));
        assertEquals(1.0, overlap);
    }

    @Test
    void flakinessCountsOutcomeTransitions() {
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2024-01-02T00:00:00Z");
        Instant t2 = Instant.parse("2024-01-03T00:00:00Z");
        List<TestRun> runs = List.of(
                new TestRun("r", "T#m", "FAILED", 1, t0, "1"),
                new TestRun("r", "T#m", "PASSED", 1, t1, "2"),
                new TestRun("r", "T#m", "FAILED", 1, t2, "3"));
        assertEquals(1.0, FeatureExtractor.flakinessScore(runs));
    }
}
