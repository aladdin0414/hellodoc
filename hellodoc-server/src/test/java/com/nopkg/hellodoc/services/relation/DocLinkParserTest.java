package com.nopkg.hellodoc.services.relation;

import com.nopkg.hellodoc.enums.RelationType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DocLinkParserTest {

    private final DocLinkParser parser = new DocLinkParser();

    @Test
    void parseLinks_shouldHandleEmbedAtStart() {
        String content = "![[文档A]]是嵌入引用";
        List<DocLinkParser.ParsedLink> links = parser.parseLinks(content);
        assertEquals(1, links.size());
        assertEquals("文档A", links.get(0).ref());
        assertEquals(RelationType.EMBED, links.get(0).type());
    }

    @Test
    void parseLinks_shouldParseMixedLinks() {
        String content = "参考[[文档B]]和![[文档C]]以及[显示文本](/doc/slug-x)";
        List<DocLinkParser.ParsedLink> links = parser.parseLinks(content);
        assertEquals(3, links.size());
        assertEquals(RelationType.LINK, links.get(0).type());
        assertEquals(RelationType.EMBED, links.get(1).type());
        assertEquals("slug-x", links.get(2).ref());
    }
}
