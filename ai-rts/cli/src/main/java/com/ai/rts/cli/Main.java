package com.ai.rts.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * CI-friendly CLI.
 * <ul>
 *   <li>{@code --ingest}: read real Surefire/Allure files from disk and POST to {@code /history/ingest}.</li>
 *   <li>With {@code --api-url} and {@code --repo-url}: calls the recommend API and prints a Surefire line.</li>
 * </ul>
 * On recommend failure, falls back to {@code mvn test} (full suite) so CI never blocks on selector errors.
 */
public class Main {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public static void main(String[] args) {
        Map<String, String> argMap = parse(args);
        if ("true".equalsIgnoreCase(argMap.getOrDefault("--ingest", ""))) {
            int code = IngestCommand.run(argMap);
            System.exit(code);
        }

        String repoDir = argMap.getOrDefault("--repo-dir", ".");
        String prId = argMap.getOrDefault("--pr-id", "0");
        String outputFormat = argMap.getOrDefault("--output-format", "surefire");
        String apiUrl = argMap.get("--api-url");
        String repoUrl = argMap.get("--repo-url");
        String repoId = argMap.get("--repo-id");
        String apiToken = firstNonBlank(argMap.get("--api-token"), System.getenv("AI_RTS_API_TOKEN"));
        int testHistoryDays = parseInt(argMap.get("--test-history-days"), 30);

        if (apiUrl != null && !apiUrl.isBlank() && repoUrl != null && !repoUrl.isBlank() && !"0".equals(prId)) {
            try {
                String slug = repoId != null && !repoId.isBlank() ? repoId : inferRepoSlug(repoUrl);
                String line = fetchSurefireLine(apiUrl, slug, prId, repoUrl, testHistoryDays, apiToken);
                if ("surefire".equalsIgnoreCase(outputFormat)) {
                    System.out.println(line);
                } else {
                    System.out.println("repo=" + repoDir + ", pr=" + prId + ", cmd=" + line);
                }
                return;
            } catch (Exception e) {
                System.err.println(
                        "[ai-rts-cli] recommend failed, falling back to full suite: "
                                + IngestCommand.formatCliError(e, apiUrl));
            }
        }

        fallbackLocal(repoDir, prId, outputFormat);
    }

    static String fetchSurefireLine(String apiBase, String repoId, String prId, String repoUrl, int testHistoryDays, String apiToken)
            throws Exception {
        String base = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        URI uri = URI.create(base + "/api/v1/" + repoId + "/" + prId + "/recommend");
        String body = MAPPER.createObjectNode()
                .put("repoUrl", repoUrl)
                .put("prNumber", Integer.parseInt(prId))
                .put("testHistoryDays", testHistoryDays)
                .toString();

        HttpRequest.Builder rb = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (apiToken != null && !apiToken.isBlank()) {
            rb.header("Authorization", "Bearer " + apiToken);
        }
        HttpResponse<String> resp = HTTP.send(rb.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + resp.statusCode() + ": " + resp.body());
        }

        JsonNode root = MAPPER.readTree(resp.body());
        JsonNode subset = root.path("recommendedSubset");
        if (!subset.isArray() || subset.isEmpty()) {
            return "mvn test";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode t : subset) {
            String c = t.path("className").asText("");
            String m = t.path("methodName").asText("");
            if (c.isEmpty() || m.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(c).append('#').append(m);
        }
        if (sb.isEmpty()) {
            return "mvn test";
        }
        return "mvn test -Dtest=" + sb;
    }

    static String inferRepoSlug(String repoUrl) {
        String trimmed = repoUrl.trim().replaceAll("/+$", "");
        int slash = trimmed.lastIndexOf('/');
        if (slash < 0 || slash >= trimmed.length() - 1) {
            return "repo";
        }
        String last = trimmed.substring(slash + 1);
        if (last.endsWith(".git")) {
            last = last.substring(0, last.length() - 4);
        }
        return last.isEmpty() ? "repo" : last;
    }

    private static void fallbackLocal(String repoDir, String prId, String outputFormat) {
        if ("surefire".equalsIgnoreCase(outputFormat)) {
            System.out.println("mvn test");
        } else {
            System.out.println("repo=" + repoDir + ", pr=" + prId + ", cmd=mvn test");
        }
    }

    private static int parseInt(String s, int def) {
        if (s == null || s.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    /**
     * Supports {@code --key=value} and {@code --key value} (recommended on Windows PowerShell
     * so line breaks do not leave orphaned {@code --flags} that PowerShell parses as operators).
     */
    static Map<String, String> parse(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg == null || !arg.startsWith("--")) {
                continue;
            }
            if (arg.contains("=")) {
                String[] split = arg.split("=", 2);
                map.put(split[0], split[1]);
                continue;
            }
            String key = arg;
            if (i + 1 < args.length) {
                String next = args[i + 1];
                if (!next.startsWith("--")) {
                    map.put(key, next);
                    i++;
                    continue;
                }
            }
            map.put(key, "true");
        }
        return map;
    }
}
