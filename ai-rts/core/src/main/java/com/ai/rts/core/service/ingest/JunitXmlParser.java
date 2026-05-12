package com.ai.rts.core.service.ingest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class JunitXmlParser {
    public List<JunitTestCaseResult> parse(String junitXml) {
        if (junitXml == null || junitXml.isBlank()) {
            return List.of();
        }
        Document doc = parseXml(junitXml);
        NodeList testcases = doc.getElementsByTagName("testcase");
        List<JunitTestCaseResult> results = new ArrayList<>(testcases.getLength());
        for (int i = 0; i < testcases.getLength(); i++) {
            Node node = testcases.item(i);
            if (!(node instanceof Element el)) continue;

            String className = el.getAttribute("classname");
            String name = el.getAttribute("name");
            long durationMs = parseDurationMs(el.getAttribute("time"));

            String status = "PASSED";
            if (hasChild(el, "skipped")) status = "SKIPPED";
            if (hasChild(el, "failure") || hasChild(el, "error")) status = "FAILED";

            if (className == null || className.isBlank()) className = "UnknownClass";
            if (name == null || name.isBlank()) name = "unknownTest";

            results.add(new JunitTestCaseResult(className, name, status, durationMs));
        }
        return results;
    }

    private static Document parseXml(String xml) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
            return dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JUnit XML", e);
        }
    }

    private static boolean hasChild(Element testcase, String childName) {
        NodeList children = testcase.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element el && childName.equals(el.getTagName())) return true;
        }
        return false;
    }

    private static long parseDurationMs(String secondsString) {
        if (secondsString == null || secondsString.isBlank()) return 0L;
        try {
            double seconds = Double.parseDouble(secondsString.trim());
            return (long) Math.round(seconds * 1000.0d);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}

