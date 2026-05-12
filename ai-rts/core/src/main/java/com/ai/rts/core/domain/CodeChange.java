package com.ai.rts.core.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "code_changes")
public class CodeChange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String filePath;
    private int linesAdded;
    private int linesRemoved;
    private String methodsTouched;

    public CodeChange() {}

    public CodeChange(String filePath, int linesAdded, int linesRemoved, String methodsTouched) {
        this.filePath = filePath;
        this.linesAdded = linesAdded;
        this.linesRemoved = linesRemoved;
        this.methodsTouched = methodsTouched;
    }

    public Long getId() { return id; }
    public String getFilePath() { return filePath; }
    public int getLinesAdded() { return linesAdded; }
    public int getLinesRemoved() { return linesRemoved; }
    public String getMethodsTouched() { return methodsTouched; }
}
