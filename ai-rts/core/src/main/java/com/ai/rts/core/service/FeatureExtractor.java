package com.ai.rts.core.service;

import com.ai.rts.core.domain.CodeChange;
import com.ai.rts.core.domain.TestMetadata;
import com.ai.rts.core.domain.TestRun;
import com.ai.rts.core.model.RecommendationModels.FeatureVector;
import java.util.ArrayList;
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
            double flakiness = history.isEmpty() ? 0.0 : (double) Math.min(failures, passes) / history.size();
            double tagCritical = item.getTags() != null && item.getTags().contains("critical") ? 1.0 : 0.0;
            double integrationWeight = "integration".equalsIgnoreCase(item.getType()) ? 1.0 : 0.2;

            double[] features = new double[] {
                failRate, flakiness, item.getAvgDuration(), history.size(), failures, passes,
                changedFiles, totalLinesAdded, totalLinesRemoved, tagCritical, integrationWeight, 1.0
            };
            vectors.add(new FeatureVector(testId, item.getClassName(), item.getMethodName(), features, item.getAvgDuration()));
        }
        return vectors;
    }
}
