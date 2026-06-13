package com.ai.rts.core.repository;

import com.ai.rts.core.domain.TestRun;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestRunRepository extends JpaRepository<TestRun, Long> {
    List<TestRun> findByRepoIdAndTimestampAfter(String repoId, Instant from);
    List<TestRun> findByRepoIdAndTestId(String repoId, String testId);
}
