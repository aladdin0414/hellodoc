package com.nopkg.hellodoc.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class McpSourceLinksTest {
    @Test void addsAbsoluteLinksToNestedResultsOnlyForDocumentPaths() {
        var links = new McpSourceLinks(new ObjectMapper(), "https://docs.example.com");
        var result = links.enrich(Map.of("results", List.of(Map.of("path", "/kb/2/3"), Map.of("path", "//evil.example"))));
        assertEquals("https://docs.example.com/kb/2/3", result.at("/results/0/url").asText());
        assertTrue(result.at("/results/1/url").isMissingNode());
    }
}
