package com.ai.rts.core.service.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JunitXmlParserTest {
    @Test
    void parsesPassFailSkip() {
        String xml = """
                <testsuite name="s">
                  <testcase classname="A" name="ok" time="0.01"/>
                  <testcase classname="A" name="bad" time="0.02"><failure/></testcase>
                  <testcase classname="A" name="skip" time="0"><skipped/></testcase>
                </testsuite>
                """;
        JunitXmlParser parser = new JunitXmlParser();
        var out = parser.parse(xml);
        assertEquals(3, out.size());
        assertEquals("PASSED", out.get(0).status());
        assertEquals("FAILED", out.get(1).status());
        assertEquals("SKIPPED", out.get(2).status());
    }
}

