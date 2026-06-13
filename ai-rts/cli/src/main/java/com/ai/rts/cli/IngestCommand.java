package com.ai.rts.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reads real JUnit Surefire XML (and optional Allure result JSON) from disk and POSTs them to
 * {@code POST /api/v1/{repoId}/{correlationId}/history/ingest}.
 */
public final class IngestCommand {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Guardrail for very large suites (single JSON body limits). */
    static final int MAX_JUNIT_FILES = 512;
    static final int MAX_ALLURE_FILES = 1024;
    static final int MAX_DOC_BYTES = 4_000_000;

    private IngestCommand() {}

    public static int run(Map<String, String> args) {
        String apiUrl = args.get("--api-url");
        String repoId = args.get("--repo-id");
        String correlationId = firstNonBlank(args.get("--correlation-id"), args.get("--pr-id"));
        String apiToken = firstNonBlank(args.get("--api-token"), System.getenv("AI_RTS_API_TOKEN"));
        String surefireDir = args.get("--surefire-dir");
        String allureDir = args.get("--allure-dir");

        if (apiUrl == null || apiUrl.isBlank()) {
            err("Missing --api-url");
            return 2;
        }
        if (repoId == null || repoId.isBlank()) {
            err("Missing --repo-id");
            return 2;
        }
        if (correlationId == null || correlationId.isBlank()) {
            err("Missing --correlation-id (or --pr-id) for ingest URL segment");
            return 2;
        }
        if ((surefireDir == null || surefireDir.isBlank()) && (allureDir == null || allureDir.isBlank())) {
            err("Provide --surefire-dir and/or --allure-dir");
            return 2;
        }

        try {
            assertUrlPathSegment(repoId, "--repo-id");
            assertUrlPathSegment(correlationId, "--correlation-id");

            List<String> junitDocs = new ArrayList<>();
            if (surefireDir != null && !surefireDir.isBlank()) {
                junitDocs.addAll(collectJUnitDocuments(Path.of(surefireDir)));
            }
            List<String> allureDocs = new ArrayList<>();
            if (allureDir != null && !allureDir.isBlank()) {
                allureDocs.addAll(collectAllureDocuments(Path.of(allureDir)));
            }
            if (junitDocs.isEmpty() && allureDocs.isEmpty()) {
                err("No JUnit XML (TEST-*.xml) or Allure *-result.json files found under given directories");
                return 3;
            }

            ObjectNode body = MAPPER.createObjectNode();
            body.putNull("timestamp");
            ArrayNode jn = body.putArray("junitXmlDocuments");
            for (String s : junitDocs) {
                jn.add(s);
            }
            ArrayNode an = body.putArray("allureResultJsonDocuments");
            for (String s : allureDocs) {
                an.add(s);
            }

            String base = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
            URI uri = URI.create(base + "/api/v1/" + repoId + "/" + correlationId + "/history/ingest");

            HttpRequest.Builder rb = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMinutes(2))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
            if (apiToken != null && !apiToken.isBlank()) {
                rb.header("Authorization", "Bearer " + apiToken);
            }
            HttpResponse<String> resp = HTTP.send(rb.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                err("Ingest HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 2000));
                return 4;
            }
            System.out.println(resp.body());
            return 0;
        } catch (Exception e) {
            err("Ingest failed: " + formatCliError(e, apiUrl));
            if (isLikelyUnreachableApi(e)) {
                err("Tip: this command only uploads files to an already-running API. Start the server in another terminal, "
                        + "leave it running, then run ingest again:");
                err("     java -jar api/target/api-0.1.0-SNAPSHOT.jar");
            }
            e.printStackTrace(System.err);
            return 5;
        }
    }

    static boolean isLikelyUnreachableApi(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.net.ConnectException) {
                return true;
            }
            if (t instanceof java.net.http.HttpConnectTimeoutException) {
                return true;
            }
        }
        return false;
    }

    /**
     * One-line failure text; {@code apiUrl} is used for connection errors (JDK often returns {@code null}
     * messages on {@link java.net.ConnectException}).
     */
    public static String formatCliError(Throwable e, String apiUrl) {
        if (isLikelyUnreachableApi(e)) {
            String u = apiUrl == null ? "" : apiUrl.trim();
            return "Cannot reach the API at " + (u.isEmpty() ? "(no URL)" : u) + " — is the server running?";
        }
        String m = e.getMessage();
        if (m != null && !m.isBlank()) {
            return m;
        }
        Throwable c = e.getCause();
        if (c != null && c.getClass() != e.getClass()) {
            String cm = c.getMessage();
            if (cm != null && !cm.isBlank()) {
                return e.getClass().getSimpleName() + ": " + cm;
            }
            return e.getClass().getSimpleName() + " (caused by " + c.getClass().getSimpleName() + ")";
        }
        if (c != null) {
            return e.getClass().getSimpleName() + " (see cause in stack trace)";
        }
        return e.getClass().getSimpleName() + " (no message; see stack trace)";
    }

    static List<String> collectJUnitDocuments(Path root) throws Exception {
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + root.toAbsolutePath());
        }
        List<Path> paths = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.getFileName().toString();
                if (name.startsWith("TEST-") && name.endsWith(".xml")) {
                    paths.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        if (paths.size() > MAX_JUNIT_FILES) {
            throw new IllegalArgumentException("Too many JUnit XML files (" + paths.size() + "), max " + MAX_JUNIT_FILES);
        }
        List<String> out = new ArrayList<>(paths.size());
        for (Path p : paths) {
            byte[] bytes = Files.readAllBytes(p);
            if (bytes.length > MAX_DOC_BYTES) {
                throw new IllegalArgumentException("File too large: " + p + " (" + bytes.length + " bytes)");
            }
            out.add(new String(bytes, StandardCharsets.UTF_8));
        }
        return out;
    }

    static List<String> collectAllureDocuments(Path root) throws Exception {
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + root.toAbsolutePath());
        }
        List<Path> paths = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith("-result.json"))
                    .forEach(paths::add);
        }
        if (paths.size() > MAX_ALLURE_FILES) {
            throw new IllegalArgumentException("Too many Allure JSON files (" + paths.size() + "), max " + MAX_ALLURE_FILES);
        }
        List<String> out = new ArrayList<>(paths.size());
        for (Path p : paths) {
            byte[] bytes = Files.readAllBytes(p);
            if (bytes.length > MAX_DOC_BYTES) {
                throw new IllegalArgumentException("File too large: " + p + " (" + bytes.length + " bytes)");
            }
            out.add(new String(bytes, StandardCharsets.UTF_8));
        }
        return out;
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

    /** Single path segment: avoids ambiguous URLs and Tomcat %2F edge cases. */
    static void assertUrlPathSegment(String raw, String label) {
        if (raw == null || raw.isEmpty() || raw.chars().anyMatch(c -> c == '/' || c == '?' || c == '#' || c == '%')) {
            throw new IllegalArgumentException(label + " must be a single URL path segment without / ? # %");
        }
    }

    private static void err(String m) {
        System.err.println("[ai-rts-cli] " + m);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
