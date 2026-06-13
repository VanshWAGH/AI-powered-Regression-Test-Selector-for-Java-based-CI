package com.ai.rts.core.service;

import com.ai.rts.core.domain.CodeChange;
import com.ai.rts.core.domain.TestMetadata;
import com.ai.rts.core.domain.TestRun;
import com.ai.rts.core.model.RecommendationModels.FeatureVector;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class FeatureExtractor {
    public List<FeatureVector> buildFeatures(List<TestMetadata> metadata, List<TestRun> runs, List<CodeChange> changes) {
        Map<String, List<TestRun>> runsByTest = runs.stream().collect(Collectors.groupingBy(TestRun::getTestId));
        int changedFiles = changes.size();
        int totalLinesAdded = changes.stream().mapToInt(CodeChange::getLinesAdded).sum();
        int totalLinesRemoved = changes.stream().mapToInt(CodeChange::getLinesRemoved).sum();

        List<FeatureVector> vectors = new ArrayList<>();
        for (TestMetadata item : metadata) {
            String testId = item.getClassName() + "#" + item.getMethodName();
            List<TestRun> history = runsByTest.getOrDefault(testId, List.of());
            long failures = history.stream().filter(r -> "FAILED".equalsIgnoreCase(r.getResult())).count();
            long passes = history.stream().filter(r -> "PASSED".equalsIgnoreCase(r.getResult())).count();
            double failRate = history.isEmpty() ? 0.0 : (double) failures / history.size();
            double recencyFailRate = recencyWeightedFailRate(history);
            double flakiness = flakinessScore(history);
            double tagCritical = item.getTags() != null && item.getTags().contains("critical") ? 1.0 : 0.0;
            double integrationWeight = "integration".equalsIgnoreCase(item.getType()) ? 1.0 : 0.2;
            double packageOverlap = packageOverlap(item.getClassName(), changes);

            double[] features = new double[] {
                recencyFailRate, flakiness, item.getAvgDuration(), history.size(), failures, passes,
                changedFiles, totalLinesAdded, totalLinesRemoved, tagCritical, integrationWeight, packageOverlap
            };
            vectors.add(new FeatureVector(testId, item.getClassName(), item.getMethodName(), features, item.getAvgDuration()));
        }
        return vectors;
    }

    static double recencyWeightedFailRate(List<TestRun> history) {
        if (history.isEmpty()) {
            return 0.0;
        }
        List<TestRun> recent = history.stream()
                .sorted(Comparator.comparing(TestRun::getTimestamp).reversed())
                .limit(10)
                .toList();
        double weightSum = 0.0;
        double failSum = 0.0;
        for (int i = 0; i < recent.size(); i++) {
            double weight = 1.0 / (i + 1);
            weightSum += weight;
            if ("FAILED".equalsIgnoreCase(recent.get(i).getResult())) {
                failSum += weight;
            }
        }
        return weightSum == 0.0 ? 0.0 : failSum / weightSum;
    }

    static double flakinessScore(List<TestRun> history) {
        if (history.size() < 2) {
            return 0.0;
        }
        List<TestRun> sorted = history.stream().sorted(Comparator.comparing(TestRun::getTimestamp)).toList();
        int transitions = 0;
        for (int i = 1; i < sorted.size(); i++) {
            boolean prevFail = "FAILED".equalsIgnoreCase(sorted.get(i - 1).getResult());
            boolean currFail = "FAILED".equalsIgnoreCase(sorted.get(i).getResult());
            if (prevFail != currFail) {
                transitions++;
            }
        }
        return (double) transitions / (sorted.size() - 1);
    }

    static double packageOverlap(String className, List<CodeChange> changes) {
        if (className == null || className.isBlank() || changes.isEmpty()) {
            return 0.0;
        }
        String pkg = className.contains(".") ? className.substring(0, className.lastIndexOf('.')) : className;
        String pkgPath = pkg.replace('.', '/');
        String classFile = className.replace('.', '/') + ".java";
        long hits = changes.stream()
                .filter(c -> {
                    String path = c.getFilePath() == null ? "" : c.getFilePath();
                    return path.contains(pkgPath) || path.contains(classFile);
                })
                .count();
        return Math.min(1.0, (double) hits / changes.size());
    }
}
