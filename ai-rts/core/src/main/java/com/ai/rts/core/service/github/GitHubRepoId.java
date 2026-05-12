package com.ai.rts.core.service.github;

import java.net.URI;
import java.util.Objects;

public record GitHubRepoId(String owner, String repo) {
    public GitHubRepoId {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(repo, "repo");
    }

    public static GitHubRepoId fromRepoUrl(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new IllegalArgumentException("repoUrl must not be blank");
        }
        URI uri = URI.create(repoUrl.trim());
        String host = uri.getHost();
        if (host == null || !host.equalsIgnoreCase("github.com")) {
            throw new IllegalArgumentException("Only github.com URLs are supported for MVP: " + repoUrl);
        }
        String path = uri.getPath();
        if (path == null) {
            throw new IllegalArgumentException("Invalid GitHub URL path: " + repoUrl);
        }
        String[] parts = path.split("/");
        if (parts.length < 3) {
            throw new IllegalArgumentException("GitHub repo URL must be /{owner}/{repo}: " + repoUrl);
        }
        String owner = parts[1];
        String repo = parts[2];
        if (repo.endsWith(".git")) {
            repo = repo.substring(0, repo.length() - 4);
        }
        return new GitHubRepoId(owner, repo);
    }
}

