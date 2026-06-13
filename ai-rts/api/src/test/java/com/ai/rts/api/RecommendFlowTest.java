package com.ai.rts.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class RecommendFlowTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void ingestHistoryThenRecommendForSameRepo() throws Exception {
        String xml;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("sample-surefire.xml")) {
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        ObjectNode ingestBody = MAPPER.createObjectNode();
        ingestBody.putNull("timestamp");
        ArrayNode junit = ingestBody.putArray("junitXmlDocuments");
        junit.add(xml);
        ingestBody.putArray("allureResultJsonDocuments");

        mockMvc.perform(post("/api/v1/demo-repo/ci-it-1/history/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ingestBody.toString()))
                .andExpect(status().isOk());

        ObjectNode recommendBody = MAPPER.createObjectNode();
        recommendBody.put("repoUrl", "https://github.com/example/demo");
        recommendBody.put("prNumber", 1);
        recommendBody.put("testHistoryDays", 30);

        MvcResult result = mockMvc.perform(post("/api/v1/demo-repo/1/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recommendBody.toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertTrue(root.path("rankedTests").isArray());
        assertFalse(root.path("rankedTests").isEmpty());
        assertFalse(root.path("recommendedSubset").isEmpty());
    }
}
