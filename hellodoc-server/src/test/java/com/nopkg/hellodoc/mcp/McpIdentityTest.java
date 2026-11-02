package com.nopkg.hellodoc.mcp;

import com.nopkg.hellodoc.services.KbService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpIdentityTest {
    @Test
    void concurrentCallsHaveSeparateIdentitiesAndRestorePriorContext() throws Exception {
        var kb = mock(KbService.class);
        when(kb.requireUserId("alice")).thenReturn(1L);
        when(kb.requireUserId("bob")).thenReturn(2L);
        var identity = new McpIdentity(kb);
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> call(identity, "alice", ready, start));
            var second = pool.submit(() -> call(identity, "bob", ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals("alice:1", first.get(5, TimeUnit.SECONDS));
            assertEquals("bob:2", second.get(5, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void rejectsMissingOrAnonymousAuthentication() {
        var identity = new McpIdentity(mock(KbService.class));
        assertThrows(SecurityException.class, () -> identity.run(null, id -> id));
        var anonymous = new AnonymousAuthenticationToken("key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        assertThrows(SecurityException.class, () -> identity.run(anonymous, id -> id));
    }

    private static String call(McpIdentity identity, String name, CountDownLatch ready, CountDownLatch start) throws Exception {
        var before = new UsernamePasswordAuthenticationToken("previous", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(before);
        ready.countDown();
        start.await();
        try {
            String result = identity.run(new UsernamePasswordAuthenticationToken(name, null, List.of()), id ->
                    SecurityContextHolder.getContext().getAuthentication().getName() + ":" + id);
            assertSame(before, SecurityContextHolder.getContext().getAuthentication());
            return result;
        } finally { SecurityContextHolder.clearContext(); }
    }
}
