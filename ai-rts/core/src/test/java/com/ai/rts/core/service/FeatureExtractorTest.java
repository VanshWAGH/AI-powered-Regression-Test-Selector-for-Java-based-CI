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
        List<TestMetadata> metadata = List.of(new TestMetadata("A", "m1", "critical", "integration", 1200));
        List<TestRun> runs = List.of(
                new TestRun("A#m1", "FAILED", 1000, Instant.now(), "12"),
                new TestRun("A#m1", "PASSED", 900, Instant.now(), "13"));
        List<CodeChange> changes = List.of(new CodeChange("src/A.java", 5, 2, "x"));

        var vectors = extractor.buildFeatures(metadata, runs, changes);
        assertEquals(1, vectors.size());
        assertEquals(12, vectors.get(0).values().length);
        assertTrue(vectors.get(0).values()[0] > 0.0);
    }
}
