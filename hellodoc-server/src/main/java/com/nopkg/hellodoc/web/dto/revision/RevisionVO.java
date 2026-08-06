package com.nopkg.hellodoc.web.dto.revision;

import com.nopkg.hellodoc.enums.RevisionType;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class RevisionVO {
    private Long id;
    private Integer version;
    private String authorName;
    private Long authorId;
    private String message;
    private RevisionType type;
    private OffsetDateTime createdAt;
    private Integer wordCount;
}
