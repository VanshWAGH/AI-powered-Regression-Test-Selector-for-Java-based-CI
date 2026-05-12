package com.ai.rts.core.model;

import java.util.List;

public final class RecommendationModels {
    private RecommendationModels() {}

    public record FeatureVector(String testId, String className, String methodName, double[] values, long estimatedMs) {}
    public record RankedTest(String testId, String className, String methodName, double riskScore, long estimatedTime) {}
    public record SelectionMetrics(double reduction, String estimatedTime) {}
    public record RecommendationResult(List<RankedTest> rankedTests, List<RankedTest> recommendedSubset, SelectionMetrics metrics) {}
}
