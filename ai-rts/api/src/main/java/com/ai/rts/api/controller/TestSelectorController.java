package com.ai.rts.api.controller;

import com.ai.rts.api.dto.RecommendationDtos.MetricsDto;
import com.ai.rts.api.dto.RecommendationDtos.RankedTestDto;
import com.ai.rts.api.dto.RecommendationDtos.RecommendRequest;
import com.ai.rts.api.dto.RecommendationDtos.RecommendResponse;
import com.ai.rts.core.domain.TestMetadata;
import com.ai.rts.core.model.RecommendationModels.FeatureVector;
import com.ai.rts.core.model.RecommendationModels.RecommendationResult;
import com.ai.rts.core.service.FeatureExtractor;
import com.ai.rts.core.service.GitCloneService;
import com.ai.rts.core.service.ModelService;
import com.ai.rts.core.service.RecommendationEngine;
import com.ai.rts.core.service.TestHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TestSelectorController {
    private static final Logger log = LoggerFactory.getLogger(TestSelectorController.class);
    private final GitCloneService gitCloneService;
    private final TestHistoryService testHistoryService;
    private final FeatureExtractor featureExtractor;
    private final ModelService modelService;
    private final RecommendationEngine recommendationEngine;

    public TestSelectorController(
            GitCloneService gitCloneService,
            TestHistoryService testHistoryService,
            FeatureExtractor featureExtractor,
            ModelService modelService,
            RecommendationEngine recommendationEngine) {
        this.gitCloneService = gitCloneService;
        this.testHistoryService = testHistoryService;
        this.featureExtractor = featureExtractor;
        this.modelService = modelService;
        this.recommendationEngine = recommendationEngine;
    }

    @PostMapping("/{repoId}/{prId}/recommend")
    @Operation(summary = "Recommend ranked tests for a GitHub PR")
    public ResponseEntity<RecommendResponse> recommend(
            @PathVariable("repoId") String repoId,
            @PathVariable("prId") String prId,
            @Valid @RequestBody RecommendRequest request) {
        long started = System.nanoTime();
        List<TestMetadata> metadata = testHistoryService.loadMetadata(repoId);
        List<FeatureVector> vectors = featureExtractor.buildFeatures(
                metadata,
                testHistoryService.loadRecentRuns(repoId, request.testHistoryDays()),
                gitCloneService.extractDiff(request.repoUrl(), request.prNumber()));

        RecommendationResult result = vectors.isEmpty()
                ? recommendationEngine.fallbackRunAll(List.of())
                : recommendationEngine.recommend(vectors, modelService.score(vectors));

        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        log.info("Recommend repoId={} prId={} tests={} subset={} latencyMs={}",
                repoId, prId, result.rankedTests().size(), result.recommendedSubset().size(), elapsedMs);

        List<RankedTestDto> ranked = result.rankedTests().stream()
                .map(t -> new RankedTestDto(t.testId(), t.className(), t.methodName(), t.riskScore(), t.estimatedTime()))
                .toList();
        List<RankedTestDto> subset = result.recommendedSubset().stream()
                .map(t -> new RankedTestDto(t.testId(), t.className(), t.methodName(), t.riskScore(), t.estimatedTime()))
                .toList();
        MetricsDto metrics = new MetricsDto((int) (result.metrics().reduction() * 100) + "%", result.metrics().estimatedTime());
        return ResponseEntity.ok(new RecommendResponse(ranked, subset, metrics));
    }
}
