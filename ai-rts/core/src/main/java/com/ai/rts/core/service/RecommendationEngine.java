package com.ai.rts.core.service;

import com.ai.rts.core.model.RecommendationModels.FeatureVector;
import com.ai.rts.core.model.RecommendationModels.RankedTest;
import com.ai.rts.core.model.RecommendationModels.RecommendationResult;
import com.ai.rts.core.model.RecommendationModels.SelectionMetrics;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RecommendationEngine {
    private static final Logger log = LoggerFactory.getLogger(RecommendationEngine.class);
    private static final double TARGET_SUBSET_RATIO = 0.4d;
    private static final long MAX_SUBSET_DURATION_MS = Duration.ofMinutes(10).toMillis();
    private static final int CRITICAL_TAG_INDEX = 9;

    public RecommendationResult recommend(List<FeatureVector> vectors, Map<String, Double> scores) {
        List<RankedTest> ranked = vectors.stream()
                .map(v -> new RankedTest(v.testId(), v.className(), v.methodName(), scores.getOrDefault(v.testId(), 0.0), v.estimatedMs()))
                .sorted(Comparator.comparingDouble(RankedTest::riskScore).reversed())
                .toList();

        Set<String> criticalIds = criticalTestIds(vectors);
        List<RankedTest> subset = new ArrayList<>();
        Set<String> selected = new HashSet<>();
        long runningMs = 0L;

        for (RankedTest candidate : ranked) {
            if (!criticalIds.contains(candidate.testId())) {
                continue;
            }
            subset.add(candidate);
            selected.add(candidate.testId());
            runningMs += candidate.estimatedTime();
        }

        int maxByCount = Math.max(subset.size(), Math.max(1, (int) Math.ceil(ranked.size() * TARGET_SUBSET_RATIO)));
        for (RankedTest candidate : ranked) {
            if (selected.contains(candidate.testId())) {
                continue;
            }
            if (subset.size() >= maxByCount || runningMs + candidate.estimatedTime() > MAX_SUBSET_DURATION_MS) {
                break;
            }
            subset.add(candidate);
            selected.add(candidate.testId());
            runningMs += candidate.estimatedTime();
        }

        double reduction = ranked.isEmpty() ? 0.0 : 1.0 - ((double) subset.size() / ranked.size());
        SelectionMetrics metrics = new SelectionMetrics(reduction, toHumanReadable(runningMs));
        log.info("Generated {} ranked tests, {} subset tests ({} critical always-included)",
                ranked.size(), subset.size(), criticalIds.size());
        return new RecommendationResult(ranked, subset, metrics);
    }

    public RecommendationResult fallbackRunAll(List<FeatureVector> vectors) {
        List<RankedTest> all = vectors.stream()
                .map(v -> new RankedTest(v.testId(), v.className(), v.methodName(), 1.0, v.estimatedMs()))
                .toList();
        long duration = all.stream().mapToLong(RankedTest::estimatedTime).sum();
        return new RecommendationResult(all, all, new SelectionMetrics(0.0, toHumanReadable(duration)));
    }

    private Set<String> criticalTestIds(List<FeatureVector> vectors) {
        Set<String> ids = new HashSet<>();
        for (FeatureVector vector : vectors) {
            double[] f = vector.values();
            if (f.length > CRITICAL_TAG_INDEX && f[CRITICAL_TAG_INDEX] >= 1.0) {
                ids.add(vector.testId());
            }
        }
        return ids;
    }

    private String toHumanReadable(long milliseconds) {
        if (milliseconds < 1000) {
            return milliseconds + "ms";
        }
        long minutes = milliseconds / 60000;
        long seconds = (milliseconds % 60000) / 1000;
        if (minutes == 0) {
            return seconds + "s";
        }
        return minutes + "m" + seconds + "s";
    }
}
