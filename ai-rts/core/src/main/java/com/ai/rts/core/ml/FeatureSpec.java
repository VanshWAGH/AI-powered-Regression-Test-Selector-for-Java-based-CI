package com.ai.rts.core.ml;

/**
 * Stable feature ordering for ONNX inference. Bump {@link #VERSION} when the vector layout changes.
 */
public final class FeatureSpec {
    public static final String VERSION = "v1";
    public static final int FEATURE_COUNT = 12;

    /** Index constants (must match {@link com.ai.rts.core.service.FeatureExtractor}). */
    public static final int RECENCY_FAIL_RATE = 0;
    public static final int FLAKINESS = 1;
    public static final int AVG_DURATION_MS = 2;
    public static final int HISTORY_COUNT = 3;
    public static final int FAILURE_COUNT = 4;
    public static final int PASS_COUNT = 5;
    public static final int CHANGED_FILES = 6;
    public static final int LINES_ADDED = 7;
    public static final int LINES_REMOVED = 8;
    public static final int TAG_CRITICAL = 9;
    public static final int INTEGRATION_WEIGHT = 10;
    public static final int PACKAGE_OVERLAP = 11;

    private FeatureSpec() {}
}
