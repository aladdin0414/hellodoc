package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.DocComment;
import com.nopkg.hellodoc.web.dto.comment.CommentCreateDTO;

import java.util.List;

public interface CommentService {
    DocComment addComment(CommentCreateDTO dto, Long userId);

    DocComment replyComment(Long parentId, String content, Long userId);

    DocComment updateComment(Long id, String content, Long userId);

    void deleteComment(Long id, Long userId);

    DocComment resolveComment(Long id, Long userId);

    DocComment unresolveComment(Long id, Long userId);

    List<DocComment> getDocumentComments(Long docId);

    Integer getUnresolvedCount(Long docId);
}
