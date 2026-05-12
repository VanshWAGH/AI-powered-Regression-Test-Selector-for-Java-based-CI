package com.ai.rts.api.controller;

import com.ai.rts.core.service.TestHistoryIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TestHistoryController {
    private final TestHistoryIngestionService ingestionService;

    public TestHistoryController(TestHistoryIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/{repoId}/{prId}/history/ingest")
    @Operation(summary = "Ingest historical JUnit XML and/or Allure results into DB")
    public ResponseEntity<IngestResponse> ingest(
            @PathVariable("repoId") String repoId,
            @PathVariable("prId") String prId,
            @Valid @RequestBody IngestRequest request) {
        Instant ts = request.timestamp() == null ? Instant.now() : request.timestamp();
        var summary = ingestionService.ingest(prId, ts, request.junitXmlDocuments(), request.allureResultJsonDocuments());
        return ResponseEntity.ok(new IngestResponse(summary.testRunsInserted(), summary.metadataUpserted()));
    }

    public record IngestRequest(
            Instant timestamp,
            List<@NotBlank String> junitXmlDocuments,
            List<@NotBlank String> allureResultJsonDocuments
    ) {}

    public record IngestResponse(int testRunsInserted, int metadataUpserted) {}
}

