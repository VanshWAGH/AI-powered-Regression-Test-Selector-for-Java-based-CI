package com.ai.rts.core.service.github;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitHubPullFilesClient {
    private static final Logger log = LoggerFactory.getLogger(GitHubPullFilesClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GitHubPullFilesClient(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public List<GitHubPullFile> fetchAllPullFiles(GitHubRepoId repoId, int prNumber, String githubTokenOrNull) {
        List<GitHubPullFile> out = new ArrayList<>();
        int page = 1;
        while (true) {
            URI uri = URI.create("https://api.github.com/repos/" + repoId.owner() + "/" + repoId.repo()
                    + "/pulls/" + prNumber + "/files?per_page=100&page=" + page);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .GET()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "ai-rts");
            if (githubTokenOrNull != null && !githubTokenOrNull.isBlank()) {
                builder.header("Authorization", "Bearer " + githubTokenOrNull.trim());
            }

            HttpResponse<String> response;
            try {
                response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("GitHub API request failed", e);
            }

            if (response.statusCode() == 404) {
                log.warn("GitHub PR not found: {}/{} #{}", repoId.owner(), repoId.repo(), prNumber);
                return List.of();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("GitHub API error status=" + response.statusCode() + " body=" + response.body());
            }

            List<GitHubPullFile> pageItems;
            try {
                pageItems = objectMapper.readValue(response.body(), new TypeReference<>() {});
            } catch (IOException e) {
                throw new RuntimeException("Failed to parse GitHub API response JSON", e);
            }

            if (pageItems.isEmpty()) {
                break;
            }
            out.addAll(pageItems);
            page++;
        }
        return out;
    }
}

