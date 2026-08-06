package com.nopkg.hellodoc.web.dto.comment;

import com.nopkg.hellodoc.entities.DocComment;
import com.nopkg.hellodoc.enums.AnchorType;
import com.nopkg.hellodoc.web.dto.user.SysUserVO;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocCommentVO {
    private Long id;
    private Long docId;
    private Long userId;
    private SysUserVO user;
    private Long parentId;
    private AnchorType anchorType;
    private String anchorData;
    private String anchorText;
    private String content;
    private Boolean isResolved;
    private Long resolvedBy;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DocCommentVO from(DocComment comment) {
        if (comment == null) {
            return null;
        }
        DocCommentVO vo = new DocCommentVO();
        vo.setId(comment.getId());
        vo.setDocId(comment.getDocId());
        vo.setUserId(comment.getUserId());
        vo.setUser(SysUserVO.from(comment.getUser()));
        vo.setParentId(comment.getParentId());
        vo.setAnchorType(comment.getAnchorType());
        vo.setAnchorData(comment.getAnchorData());
        vo.setAnchorText(comment.getAnchorText());
        vo.setContent(comment.getContent());
        vo.setIsResolved(comment.getIsResolved());
        vo.setResolvedBy(comment.getResolvedBy());
        vo.setResolvedAt(comment.getResolvedAt());
        vo.setCreatedAt(comment.getCreatedAt());
        vo.setUpdatedAt(comment.getUpdatedAt());
        return vo;
    }
}
