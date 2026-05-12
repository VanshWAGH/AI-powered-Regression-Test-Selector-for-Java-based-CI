package com.ai.rts.core.service.ingest;

public record JunitTestCaseResult(
        String className,
        String methodName,
        String status,
        long durationMs
) {}

