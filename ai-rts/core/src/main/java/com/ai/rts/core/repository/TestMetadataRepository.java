package com.ai.rts.core.repository;

import com.ai.rts.core.domain.TestMetadata;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestMetadataRepository extends JpaRepository<TestMetadata, Long> {
    List<TestMetadata> findByType(String type);
    Optional<TestMetadata> findByClassNameAndMethodName(String className, String methodName);
}
