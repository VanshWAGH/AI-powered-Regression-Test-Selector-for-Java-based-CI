package com.ai.rts.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MainTest {
    @Test
    void printsSurefireCommand() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(out));
        try {
            Main.main(new String[] {"--repo-dir=.", "--pr-id=123", "--output-format=surefire"});
        } finally {
            System.setOut(original);
        }
        assertTrue(out.toString().contains("mvn test"));
    }

    @Test
    void parsesSpaceSeparatedArgs() {
        Map<String, String> m = Main.parse(new String[] {
                "--api-url", "http://localhost:8080",
                "--repo-url", "https://github.com/a/b",
                "--pr-id", "7",
                "--bool-flag"
        });
        assertEquals("http://localhost:8080", m.get("--api-url"));
        assertEquals("https://github.com/a/b", m.get("--repo-url"));
        assertEquals("7", m.get("--pr-id"));
        assertEquals("true", m.get("--bool-flag"));
    }

    @Test
    void parsesEqualsFormArgs() {
        Map<String, String> m = Main.parse(new String[] {"--api-url=http://x", "--pr-id=3"});
        assertEquals("http://x", m.get("--api-url"));
        assertEquals("3", m.get("--pr-id"));
    }

    @Test
    void infersRepoSlugFromUrl() {
        assertEquals("ai-rts-test", Main.inferRepoSlug("https://github.com/VanshWAGH-CS/ai-rts-test"));
        assertEquals("repo", Main.inferRepoSlug("https://github.com/org/repo.git"));
    }
}