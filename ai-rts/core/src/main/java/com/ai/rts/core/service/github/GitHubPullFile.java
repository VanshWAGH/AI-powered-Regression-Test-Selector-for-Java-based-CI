package com.ai.rts.core.service.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPullFile(
        String filename,
        int additions,
        int deletions,
        int changes
) {}

