package com.ai.rts.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
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
        assertTrue(out.toString().contains("mvn test -Dtest="));
    }
}
