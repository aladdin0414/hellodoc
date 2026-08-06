package com.nopkg.hellodoc.services.relation;

import com.nopkg.hellodoc.enums.RelationType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DocLinkParser {

    private static final Pattern WIKI_LINK = Pattern.compile("(!)?\\[\\[([^\\]|]+)(\\|[^\\]]+)?\\]\\]");
    private static final Pattern MD_LINK = Pattern.compile("\\[([^\\]]+)\\]\\(/doc/([^)]+)\\)");

    public record ParsedLink(String ref, RelationType type) {
    }

    public List<ParsedLink> parseLinks(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }

        List<ParsedLink> links = new ArrayList<>();

        Matcher matcher = WIKI_LINK.matcher(content);
        while (matcher.find()) {
            String embedMark = matcher.group(1);
            String docName = matcher.group(2);
            boolean isEmbed = "!".equals(embedMark);
            String ref = docName != null ? docName.trim() : "";
            if (StringUtils.hasText(ref)) {
                links.add(new ParsedLink(ref, isEmbed ? RelationType.EMBED : RelationType.LINK));
            }
        }

        matcher = MD_LINK.matcher(content);
        while (matcher.find()) {
            String slug = matcher.group(2);
            String ref = slug != null ? slug.trim() : "";
            if (StringUtils.hasText(ref)) {
                links.add(new ParsedLink(ref, RelationType.LINK));
            }
        }

        return links;
    }
}
