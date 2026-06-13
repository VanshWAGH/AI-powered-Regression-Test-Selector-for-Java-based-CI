package com.ai.rts.core.service;

import com.ai.rts.core.domain.TestMetadata;
import com.ai.rts.core.domain.TestRun;
import com.ai.rts.core.repository.TestMetadataRepository;
import com.ai.rts.core.repository.TestRunRepository;
import com.ai.rts.core.service.ingest.AllureResultParser;
import com.ai.rts.core.service.ingest.AllureTestCaseResult;
import com.ai.rts.core.service.ingest.JunitTestCaseResult;
import com.ai.rts.core.service.ingest.JunitXmlParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestHistoryIngestionService {
    private final TestRunRepository testRunRepository;
    private final TestMetadataRepository testMetadataRepository;
    private final JunitXmlParser junitXmlParser;
    private final AllureResultParser allureResultParser;

    public TestHistoryIngestionService(TestRunRepository testRunRepository, TestMetadataRepository testMetadataRepository) {
        this.testRunRepository = testRunRepository;
        this.testMetadataRepository = testMetadataRepository;
        this.junitXmlParser = new JunitXmlParser();
        this.allureResultParser = new AllureResultParser(new ObjectMapper());
    }

    @Transactional
    public IngestionSummary ingest(String repoId, String prId, Instant timestamp, List<String> junitXmlDocuments, List<String> allureResultJsonDocuments) {
        int runsInserted = 0;
        int metadataUpserts = 0;

        if (junitXmlDocuments != null) {
            for (String xml : junitXmlDocuments) {
                for (JunitTestCaseResult tc : junitXmlParser.parse(xml)) {
                    String testId = tc.className() + "#" + tc.methodName();
                    testRunRepository.save(new TestRun(repoId, testId, tc.status(), tc.durationMs(), timestamp, prId));
                    runsInserted++;
                    metadataUpserts += upsertMetadata(repoId, tc.className(), tc.methodName(), tc.durationMs());
                }
            }
        }

        if (allureResultJsonDocuments != null) {
            for (String json : allureResultJsonDocuments) {
                for (AllureTestCaseResult tc : allureResultParser.parseResultJson(json)) {
                    String testId = tc.className() + "#" + tc.methodName();
                    testRunRepository.save(new TestRun(repoId, testId, tc.status(), tc.durationMs(), tc.timestamp(), prId));
                    runsInserted++;
                    metadataUpserts += upsertMetadata(repoId, tc.className(), tc.methodName(), tc.durationMs());
                }
            }
        }

        return new IngestionSummary(runsInserted, metadataUpserts);
    }

    private int upsertMetadata(String repoId, String className, String methodName, long durationMs) {
        TestMetadata md = testMetadataRepository.findByRepoIdAndClassNameAndMethodName(repoId, className, methodName)
                .orElseGet(() -> new TestMetadata(repoId, className, methodName, "", "unit", durationMs));

        if (md.getId() == null) {
            testMetadataRepository.save(md);
            return 1;
        }

        // update avg duration using a simple smoothing strategy
        long existing = md.getAvgDuration();
        long updated = existing == 0 ? durationMs : Math.round(existing * 0.7d + durationMs * 0.3d);
        md.setAvgDuration(updated);
        testMetadataRepository.save(md);
        return 0;
    }

    public record IngestionSummary(int testRunsInserted, int metadataUpserted) {}
}

