package com.ai.rts.core.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "test_runs")
public class TestRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String testId;
    private String result;
    private long duration;
    private Instant timestamp;
    private String prId;

    public TestRun() {}

    public TestRun(String testId, String result, long duration, Instant timestamp, String prId) {
        this.testId = testId;
        this.result = result;
        this.duration = duration;
        this.timestamp = timestamp;
        this.prId = prId;
    }

    public Long getId() { return id; }
    public String getTestId() { return testId; }
    public String getResult() { return result; }
    public long getDuration() { return duration; }
    public Instant getTimestamp() { return timestamp; }
    public String getPrId() { return prId; }
}
