package com.ai.rts.core.service.ingest;

import java.time.Instant;

public record AllureTestCaseResult(
        String className,
        String methodName,
        String status,
        long durationMs,
        Instant timestamp
) {}

