package com.nopkg.hellodoc.services;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

class SearchServiceTest {

    @Test
    void testBuildSnippet_CharFallback() throws Exception {
        SearchService service = new SearchService(null, null, null, null, null);
        Method method = SearchService.class.getDeclaredMethod("buildSnippet", String.class, String.class, int.class);
        method.setAccessible(true);

        // 内容中没有 "自律"，但有 "自"
        String content = "这是一个关于自我管理的文章，涉及到很多方面。";
        String query = "自律";

        String result = (String) method.invoke(service, content, query, 20);
        System.out.println("Char Match Result: " + result);
        // 应该匹配到 "自"，并显示包含 "自" 的片段
        assertTrue(result.contains("自我"));

        // 确保长度限制正确 (20 - 64) -> 20. 但 buildSnippet 返回固定 maxLen 还是？
        // 实际逻辑是返回 maxLen 长度的片段。
        assertTrue(result.length() <= 20);
    }

    @Test
    void testBuildSnippet_FallbackToStart() throws Exception {
        SearchService service = new SearchService(null, null, null, null, null);
        Method method = SearchService.class.getDeclaredMethod("buildSnippet", String.class, String.class, int.class);
        method.setAccessible(true);

        String content = "这是一个测试文档。";
        String query = "完全不匹配";

        String result = (String) method.invoke(service, content, query, 10);
        assertEquals("这是一个测试文档。", result);
    }

    @Test
    void postgresSearchIncludesAccessibleChineseSubstringAfterPermissionFiltering() throws Exception {
        var docs = org.mockito.Mockito.mock(com.nopkg.hellodoc.repositories.KbDocumentRepository.class);
        var contents = org.mockito.Mockito.mock(com.nopkg.hellodoc.repositories.KbDocumentContentRepository.class);
        var permissions = org.mockito.Mockito.mock(PermissionChecker.class);
        var em = org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class);
        var sql = org.mockito.Mockito.mock(jakarta.persistence.Query.class);
        var service = new SearchService(null, null, docs, contents, permissions);
        var pg = SearchService.class.getDeclaredField("isPostgresCached"); pg.setAccessible(true); pg.set(service, true);
        var field = SearchService.class.getDeclaredField("entityManager"); field.setAccessible(true); field.set(service, em);
        org.mockito.Mockito.when(em.createNativeQuery(org.mockito.ArgumentMatchers.anyString())).thenReturn(sql);
        org.mockito.Mockito.when(sql.setParameter(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(sql);
        org.mockito.Mockito.when(sql.getResultList()).thenReturn(java.util.List.of());
        var denied = new com.nopkg.hellodoc.entities.KbDocument(); denied.setId(1L); denied.setName("秘密");
        var allowed = new com.nopkg.hellodoc.entities.KbDocument(); allowed.setId(2L); allowed.setName("部署指南");
        var encrypted = new com.nopkg.hellodoc.entities.KbDocument(); encrypted.setId(3L); encrypted.setName("知识库检索"); encrypted.setIsEncrypted(true);
        var body = new com.nopkg.hellodoc.entities.KbDocumentContent(); body.setContent("详细介绍知识库检索功能");
        org.mockito.Mockito.when(docs.findAll()).thenReturn(java.util.List.of(encrypted, denied, allowed));
        org.mockito.Mockito.when(permissions.hasDocRole(7L, 3L, com.nopkg.hellodoc.enums.DocRole.VIEWER)).thenReturn(true);
        org.mockito.Mockito.when(contents.findById(2L)).thenReturn(java.util.Optional.of(body));
        org.mockito.Mockito.when(permissions.hasDocRole(7L, 2L, com.nopkg.hellodoc.enums.DocRole.VIEWER)).thenReturn(true);
        var result = service.searchAll(7L, "知识库检索", 1);
        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).getDocId());
        assertTrue(service.searchAll(7L, "知识库检索", 20).stream().noneMatch(r -> r.getDocId() == 3L));
    }

    @Test
    void postgresRankedSearchAppliesPermissionBeforeLimit() throws Exception {
        var docs = org.mockito.Mockito.mock(com.nopkg.hellodoc.repositories.KbDocumentRepository.class);
        var contents = org.mockito.Mockito.mock(com.nopkg.hellodoc.repositories.KbDocumentContentRepository.class);
        var permissions = org.mockito.Mockito.mock(PermissionChecker.class);
        var em = org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class);
        var sql = org.mockito.Mockito.mock(jakarta.persistence.Query.class);
        var service = new SearchService(null, null, docs, contents, permissions);
        var pg = SearchService.class.getDeclaredField("isPostgresCached"); pg.setAccessible(true); pg.set(service, true);
        var field = SearchService.class.getDeclaredField("entityManager"); field.setAccessible(true); field.set(service, em);
        org.mockito.Mockito.when(em.createNativeQuery(org.mockito.ArgumentMatchers.anyString())).thenReturn(sql);
        org.mockito.Mockito.when(sql.setParameter(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(sql);
        org.mockito.Mockito.when(sql.getResultList()).thenReturn(java.util.Arrays.asList(new Object[]{1L, 2.0}, new Object[]{2L, 1.0}));
        var denied = new com.nopkg.hellodoc.entities.KbDocument(); denied.setId(1L); denied.setName("密文");
        var allowed = new com.nopkg.hellodoc.entities.KbDocument(); allowed.setId(2L); allowed.setName("可见");
        org.mockito.Mockito.when(docs.findAll()).thenReturn(java.util.List.of(denied, allowed));
        org.mockito.Mockito.when(permissions.hasDocRole(7L, 2L, com.nopkg.hellodoc.enums.DocRole.VIEWER)).thenReturn(true);
        assertEquals(2L, service.searchAll(7L, "检索", 1).get(0).getDocId());
        org.mockito.Mockito.verify(contents, org.mockito.Mockito.never()).findById(1L);
    }
}
