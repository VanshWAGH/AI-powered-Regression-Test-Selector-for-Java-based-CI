package com.ai.rts.core.repository;

import com.ai.rts.core.domain.CodeChange;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeChangeRepository extends JpaRepository<CodeChange, Long> {
    List<CodeChange> findByFilePathContaining(String packageFragment);
}
