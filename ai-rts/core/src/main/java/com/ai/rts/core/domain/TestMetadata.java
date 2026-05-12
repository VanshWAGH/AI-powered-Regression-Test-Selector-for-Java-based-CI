package com.ai.rts.core.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "test_metadata")
public class TestMetadata {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String className;
    private String methodName;
    private String tags;
    private String type;
    private long avgDuration;

    public TestMetadata() {}

    public TestMetadata(String className, String methodName, String tags, String type, long avgDuration) {
        this.className = className;
        this.methodName = methodName;
        this.tags = tags;
        this.type = type;
        this.avgDuration = avgDuration;
    }

    public Long getId() { return id; }
    public String getClassName() { return className; }
    public String getMethodName() { return methodName; }
    public String getTags() { return tags; }
    public String getType() { return type; }
    public long getAvgDuration() { return avgDuration; }

    public void setTags(String tags) { this.tags = tags; }
    public void setType(String type) { this.type = type; }
    public void setAvgDuration(long avgDuration) { this.avgDuration = avgDuration; }
}
