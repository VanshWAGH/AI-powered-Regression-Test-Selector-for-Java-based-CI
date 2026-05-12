package com.ai.rts.core.service.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class AllureResultParser {
    private final ObjectMapper objectMapper;

    public AllureResultParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<AllureTestCaseResult> parseResultJson(String allureResultJson) {
        if (allureResultJson == null || allureResultJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(allureResultJson);
            String fullName = text(root, "fullName");
            if (fullName == null || fullName.isBlank()) {
                fullName = text(root, "name");
            }
            String status = text(root, "status");
            if (status == null) status = "unknown";
            long duration = root.path("time").path("duration").asLong(0L);
            long start = root.path("time").path("start").asLong(0L);
            Instant timestamp = start > 0 ? Instant.ofEpochMilli(start) : Instant.now();

            String className = "UnknownClass";
            String methodName = "unknownTest";
            if (fullName != null && fullName.contains("#")) {
                String[] parts = fullName.split("#", 2);
                className = parts[0];
                methodName = parts[1];
            }

            List<AllureTestCaseResult> out = new ArrayList<>(1);
            out.add(new AllureTestCaseResult(className, methodName, mapStatus(status), duration, timestamp));
            return out;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Allure result JSON", e);
        }
    }

    private static String mapStatus(String allureStatus) {
        String s = allureStatus.toUpperCase();
        return switch (s) {
            case "PASSED" -> "PASSED";
            case "FAILED", "BROKEN" -> "FAILED";
            case "SKIPPED" -> "SKIPPED";
            default -> "UNKNOWN";
        };
    }

    private static String text(JsonNode root, String field) {
        JsonNode n = root.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }
}

