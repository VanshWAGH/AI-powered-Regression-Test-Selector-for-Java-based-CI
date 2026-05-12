package com.ai.rts.cli;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        Map<String, String> argMap = parse(args);
        String repoDir = argMap.getOrDefault("--repo-dir", ".");
        String prId = argMap.getOrDefault("--pr-id", "0");
        String outputFormat = argMap.getOrDefault("--output-format", "surefire");

        String selected = "TestClass1#testMethodA,TestClass2#testMethodB";
        if ("surefire".equalsIgnoreCase(outputFormat)) {
            System.out.println("mvn test -Dtest=" + selected);
        } else {
            System.out.println("repo=" + repoDir + ", pr=" + prId + ", tests=" + selected);
        }
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (String arg : Arrays.asList(args)) {
            if (arg.startsWith("--") && arg.contains("=")) {
                String[] split = arg.split("=", 2);
                map.put(split[0], split[1]);
            }
        }
        return map;
    }
}
