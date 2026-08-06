package com.nopkg.hellodoc.web.dto.search;

import lombok.Data;

@Data
public class SearchResultVO {
    private Long docId;
    private String docName;
    private Long kbId;
    private String kbTitle;
    private String snippet;
    private Double score;
    private String highlightedTitle;
    private String highlightedSnippet;
}
