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
}
