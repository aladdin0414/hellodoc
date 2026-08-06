package com.nopkg.hellodoc.controllers;

import com.nopkg.hellodoc.entities.KbDocumentRevision;
import com.nopkg.hellodoc.enums.RevisionType;
import com.nopkg.hellodoc.services.KbService;
import com.nopkg.hellodoc.services.RevisionService;
import com.nopkg.hellodoc.web.dto.revision.CreateRevisionDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RevisionController.class)
@AutoConfigureMockMvc(addFilters = false)
public class RevisionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RevisionService revisionService;

    @MockBean
    private KbService kbService;

    @Test
    @WithMockUser
    public void testGetHistory() throws Exception {
        Long docId = 1L;
        KbDocumentRevision revision = new KbDocumentRevision();
        revision.setId(10L);
        revision.setVersion(1);
        revision.setRevisionType(RevisionType.MANUAL);
        revision.setCreatedAt(OffsetDateTime.now());
        revision.setAuthorUserId(100L);

        when(revisionService.getRevisionHistory(eq(docId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.singletonList(revision)));

        mockMvc.perform(get("/api/docs/{docId}/revisions", docId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(10));
    }

    @Test
    @WithMockUser
    public void testCreateRevision() throws Exception {
        Long docId = 1L;
        String content = "New content";
        String message = "Update doc";

        when(kbService.requireUserId(any())).thenReturn(100L);

        KbDocumentRevision revision = new KbDocumentRevision();
        revision.setId(11L);
        revision.setVersion(2);
        revision.setRevisionType(RevisionType.MANUAL);
        revision.setAuthorUserId(100L);
        revision.setMessage(message);

        when(revisionService.createRevision(eq(docId), eq(content), eq(RevisionType.MANUAL), eq(message), eq(100L)))
                .thenReturn(revision);

        mockMvc.perform(post("/api/docs/{docId}/revisions", docId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"New content\", \"message\": \"Update doc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(11));
    }

    @Test
    @WithMockUser
    public void testCreateMilestone() throws Exception {
        Long docId = 1L;
        String message = "Release 1.0";

        when(kbService.requireUserId(any())).thenReturn(100L);

        mockMvc.perform(post("/api/docs/{docId}/revisions/milestone", docId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\": \"Release 1.0\"}"))
                .andExpect(status().isOk());

        verify(revisionService).createMilestone(eq(docId), eq(message), eq(100L));
    }

    @Test
    @WithMockUser
    public void testRestoreRevision() throws Exception {
        Long docId = 1L;
        Integer version = 5;

        when(kbService.requireUserId(any())).thenReturn(100L);

        mockMvc.perform(post("/api/docs/{docId}/revisions/{version}/restore", docId, version))
                .andExpect(status().isOk());

        verify(revisionService).restoreRevision(eq(docId), eq(version), eq(100L));
    }

    @Test
    @WithMockUser
    public void testCompareRevisions() throws Exception {
        Long docId = 1L;
        Integer v1 = 1;
        Integer v2 = 2;
        String diff = "Diff content";

        when(revisionService.compareRevisions(docId, v1, v2)).thenReturn(diff);

        mockMvc.perform(get("/api/docs/{docId}/revisions/compare", docId)
                .param("v1", "1")
                .param("v2", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(diff));
    }

    @Test
    @WithMockUser
    public void testGetRevisionContent() throws Exception {
        Long docId = 1L;
        Integer version = 3;
        String content = "Full content";

        when(revisionService.getRevisionContent(docId, version)).thenReturn(content);

        mockMvc.perform(get("/api/docs/{docId}/revisions/{version}/content", docId, version))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(content));
    }
}
