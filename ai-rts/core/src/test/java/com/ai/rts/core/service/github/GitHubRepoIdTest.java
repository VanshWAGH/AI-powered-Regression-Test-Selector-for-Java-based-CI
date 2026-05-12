package com.ai.rts.core.service.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GitHubRepoIdTest {
    @Test
    void parsesHttpsRepoUrl() {
        GitHubRepoId id = GitHubRepoId.fromRepoUrl("https://github.com/owner/repo");
        assertEquals("owner", id.owner());
        assertEquals("repo", id.repo());
    }

    @Test
    void parsesGitSuffix() {
        GitHubRepoId id = GitHubRepoId.fromRepoUrl("https://github.com/owner/repo.git");
        assertEquals("repo", id.repo());
    }

    @Test
    void rejectsNonGithubHost() {
        assertThrows(IllegalArgumentException.class, () -> GitHubRepoId.fromRepoUrl("https://example.com/a/b"));
    }
}

