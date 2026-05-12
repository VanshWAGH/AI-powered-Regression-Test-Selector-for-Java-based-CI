package com.ai.rts.core.service;

import com.ai.rts.core.domain.CodeChange;
import com.ai.rts.core.service.github.GitHubPullFilesClient;
import com.ai.rts.core.service.github.GitHubRepoId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GitCloneService {
    private static final Logger log = LoggerFactory.getLogger(GitCloneService.class);

    private final GitHubPullFilesClient gitHubPullFilesClient;
    private final String githubToken;

    public GitCloneService(@Value("${github.token:}") String githubToken) {
        this.githubToken = githubToken;
        this.gitHubPullFilesClient = new GitHubPullFilesClient(HttpClient.newHttpClient(), new ObjectMapper());
    }

    public List<CodeChange> extractDiff(String repoUrl, Integer prNumber) {
        try {
            GitHubRepoId repoId = GitHubRepoId.fromRepoUrl(repoUrl);
            var files = gitHubPullFilesClient.fetchAllPullFiles(repoId, prNumber, githubToken);
            return files.stream()
                    .map(f -> new CodeChange(f.filename(), f.additions(), f.deletions(), ""))
                    .toList();
        } catch (Exception ex) {
            log.warn("Unable to fetch PR diff for repo={} pr={}, fallback to safe default.", repoUrl, prNumber, ex);
            return List.of();
        }
    }
}
