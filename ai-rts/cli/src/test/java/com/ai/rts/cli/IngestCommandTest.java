package com.ai.rts.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class IngestCommandTest {
    @Test
    void collectsTestXmlFiles() throws Exception {
        Path root = Files.createTempDirectory("ing");
        Path sub = Files.createDirectories(root.resolve("a").resolve("surefire-reports"));
        Files.writeString(
                sub.resolve("TEST-Hello.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuite><testcase classname=\"x.Y\" name=\"z\" time=\"0.01\"/></testsuite>");
        List<String> docs = IngestCommand.collectJUnitDocuments(root);
        assertEquals(1, docs.size());
        assertTrue(docs.get(0).contains("x.Y"));
    }

    @Test
    void rejectsUnsafePathSegments() {
        assertThrows(IllegalArgumentException.class, () -> IngestCommand.assertUrlPathSegment("a/b", "x"));
    }

    @Test
    void formatsConnectExceptionWithoutNullMessage() {
        Exception nested = new java.nio.channels.ClosedChannelException();
        Exception e = new java.net.ConnectException();
        e.initCause(nested);
        String s = IngestCommand.formatCliError(e, "http://localhost:8080");
        assertTrue(s.contains("Cannot reach") && s.contains("8080"));
    }

    @Test
    void ingestPostsJsonBody() throws Exception {
        Path dir = Files.createTempDirectory("sf");
        Files.writeString(
                dir.resolve("TEST-T.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuite><testcase classname=\"q.U\" name=\"m\" time=\"0.01\"/></testsuite>");

        AtomicReference<String> captured = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/api/v1/myrepo/ci-deadbeef/history/ingest",
                exchange -> {
                    captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    byte[] ok = "{\"testRunsInserted\":1,\"metadataUpserted\":1}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, ok.length);
                    exchange.getResponseBody().write(ok);
                    exchange.close();
                });
        server.setExecutor(null);
        server.start();
        try {
            int port = server.getAddress().getPort();
            Map<String, String> args = new HashMap<>();
            args.put("--ingest", "true");
            args.put("--api-url", "http://127.0.0.1:" + port);
            args.put("--repo-id", "myrepo");
            args.put("--correlation-id", "ci-deadbeef");
            args.put("--surefire-dir", dir.toString());
            assertEquals(0, IngestCommand.run(args));
        } finally {
            server.stop(0);
        }
        String body = captured.get();
        assertTrue(body.contains("junitXmlDocuments"));
        assertTrue(body.contains("q.U"));
    }
}
