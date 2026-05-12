package com.ai.rts.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class RecommendationDtos {
    private RecommendationDtos() {}

    public record RecommendRequest(
            @NotBlank String repoUrl,
            @NotNull @Min(1) Integer prNumber,
            @NotNull @Min(1) Integer testHistoryDays) {}

    public record RankedTestDto(String testId, String className, String methodName, double riskScore, long estimatedTime) {}

    public record MetricsDto(String reduction, String estimatedTime) {}

    public record RecommendResponse(List<RankedTestDto> rankedTests, List<RankedTestDto> recommendedSubset, MetricsDto metrics) {}
}
