package com.ai.rts.core.service;

import com.ai.rts.core.model.RecommendationModels.FeatureVector;
import com.ai.rts.core.model.RecommendationModels.RankedTest;
import com.ai.rts.core.model.RecommendationModels.RecommendationResult;
import com.ai.rts.core.model.RecommendationModels.SelectionMetrics;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RecommendationEngine {
    private static final Logger log = LoggerFactory.getLogger(RecommendationEngine.class);
    private static final double TARGET_SUBSET_RATIO = 0.4d;
    private static final long MAX_SUBSET_DURATION_MS = Duration.ofMinutes(10).toMillis();

    public RecommendationResult recommend(List<FeatureVector> vectors, Map<String, Double> scores) {
        List<RankedTest> ranked = vectors.stream()
                .map(v -> new RankedTest(v.testId(), v.className(), v.methodName(), scores.getOrDefault(v.testId(), 0.0), v.estimatedMs()))
                .sorted(Comparator.comparingDouble(RankedTest::riskScore).reversed())
                .toList();

        List<RankedTest> subset = new ArrayList<>();
        long runningMs = 0L;
        int maxByCount = Math.max(1, (int) Math.ceil(ranked.size() * TARGET_SUBSET_RATIO));
        for (RankedTest candidate : ranked) {
            if (subset.size() >= maxByCount || runningMs + candidate.estimatedTime() > MAX_SUBSET_DURATION_MS) {
                break;
            }
            subset.add(candidate);
            runningMs += candidate.estimatedTime();
        }
        double reduction = ranked.isEmpty() ? 0.0 : 1.0 - ((double) subset.size() / ranked.size());
        SelectionMetrics metrics = new SelectionMetrics(reduction, toHumanReadable(runningMs));
        log.info("Generated {} ranked tests and {} subset tests", ranked.size(), subset.size());
        return new RecommendationResult(ranked, subset, metrics);
    }

    public RecommendationResult fallbackRunAll(List<FeatureVector> vectors) {
        List<RankedTest> all = vectors.stream()
                .map(v -> new RankedTest(v.testId(), v.className(), v.methodName(), 1.0, v.estimatedMs()))
                .toList();
        long duration = all.stream().mapToLong(RankedTest::estimatedTime).sum();
        return new RecommendationResult(all, all, new SelectionMetrics(0.0, toHumanReadable(duration)));
    }

    private String toHumanReadable(long milliseconds) {
        long minutes = milliseconds / 60000;
        long seconds = (milliseconds % 60000) / 1000;
        return minutes + "m" + seconds + "s";
    }
}
