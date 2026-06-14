package com.ai.rts.api.controller;

import com.ai.rts.core.service.TestHistoryIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
            @Valid @RequestBody @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(
                            name = "sampleSurefire",
                            value = """
                                    {
                                      "timestamp": null,
                                      "junitXmlDocuments": [
                                        "<?xml version=\\"1.0\\" encoding=\\"UTF-8\\"?><testsuite name=\\"Demo\\"><testcase classname=\\"com.demo.DemoTest\\" name=\\"alwaysPass\\" time=\\"0.01\\"/></testsuite>"
                                      ],
                                      "allureResultJsonDocuments": []
                                    }
                                    """)))
            IngestRequest request) {
        Instant ts = request.timestamp() == null ? Instant.now() : request.timestamp();
        List<String> junit = request.junitXmlDocuments() == null ? List.of() : request.junitXmlDocuments();
        List<String> allure = request.allureResultJsonDocuments() == null ? List.of() : request.allureResultJsonDocuments();
        var summary = ingestionService.ingest(repoId, prId, ts, junit, allure);
        return ResponseEntity.ok(new IngestResponse(summary.testRunsInserted(), summary.metadataUpserted()));
    }

    public record IngestRequest(
            Instant timestamp,
            List<@NotBlank String> junitXmlDocuments,
            List<@NotBlank String> allureResultJsonDocuments
    ) {}

    public record IngestResponse(int testRunsInserted, int metadataUpserted) {}
}

