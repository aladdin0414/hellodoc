package com.nopkg.hellodoc.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;

@Component
public class McpSourceLinks {
    private final ObjectMapper mapper;
    private final String origin;
    public McpSourceLinks(ObjectMapper mapper, @Value("${hellodoc.public-url:}") String publicUrl) {
        this.mapper = mapper;
        if (publicUrl == null || publicUrl.isBlank()) { origin = null; return; }
        URI uri = URI.create(publicUrl);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath())))
            throw new IllegalArgumentException("hellodoc.public-url must be an HTTP(S) origin");
        origin = publicUrl.replaceAll("/+$", "");
    }
    public JsonNode enrich(Object value) {
        JsonNode root = mapper.valueToTree(value);
        if (origin != null) addLinks(root);
        return root;
    }
    private void addLinks(JsonNode node) {
        if (node.isObject()) {
            var path = node.get("path");
            if (path != null && path.isTextual() && path.textValue().matches("/kb/[0-9]+/[0-9]+"))
                ((ObjectNode) node).put("url", origin + path.textValue());
            node.elements().forEachRemaining(this::addLinks);
        } else if (node.isArray()) node.elements().forEachRemaining(this::addLinks);
    }
}
