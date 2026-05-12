package com.ai.rts.core.service;

import com.ai.rts.core.domain.TestMetadata;
import com.ai.rts.core.domain.TestRun;
import com.ai.rts.core.repository.TestMetadataRepository;
import com.ai.rts.core.repository.TestRunRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TestHistoryService {
    private final TestRunRepository testRunRepository;
    private final TestMetadataRepository testMetadataRepository;

    public TestHistoryService(TestRunRepository testRunRepository, TestMetadataRepository testMetadataRepository) {
        this.testRunRepository = testRunRepository;
        this.testMetadataRepository = testMetadataRepository;
    }

    public List<TestRun> loadRecentRuns(int days) {
        return testRunRepository.findByTimestampAfter(Instant.now().minus(days, ChronoUnit.DAYS));
    }

    public List<TestMetadata> loadMetadata() {
        return testMetadataRepository.findAll();
    }
}
