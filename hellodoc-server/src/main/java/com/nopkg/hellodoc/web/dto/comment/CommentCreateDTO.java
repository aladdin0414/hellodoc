package com.nopkg.hellodoc.web.dto.comment;

import com.nopkg.hellodoc.enums.AnchorType;
import lombok.Data;

@Data
public class CommentCreateDTO {
    private Long docId;
    private Long parentId;
    private AnchorType anchorType;
    private String anchorData;
    private String anchorText;
    private String content;
}
