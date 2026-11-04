package com.nopkg.hellodoc.mcp;

import com.nopkg.hellodoc.entities.SysUser;
import com.nopkg.hellodoc.entities.SysUserAuth;
import com.nopkg.hellodoc.repositories.UserRepository;
import com.nopkg.hellodoc.repositories.UserAuthRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class McpAccessTokenServiceTest {
    @Autowired McpAccessTokenService tokens;
    @Autowired UserRepository users;
    @Autowired UserAuthRepository identities;

    @Test void tokenIsScopedToOwnerAndRevocable() {
        var user = new SysUser(); user.setNickname("token owner"); user.setStatus((short) 0); user = users.save(user);
        String username = "mcp-token-" + user.getId();
        var auth = new SysUserAuth(); auth.setUser(user); auth.setIdentityType("PASSWORD"); auth.setIdentifier(username); auth.setCredential("unused"); identities.save(auth);
        var issued = tokens.create(user.getId(), username, "test client");
        assertTrue(issued.token().startsWith("hdmcp_"));
        assertEquals(username, tokens.authenticate(issued.token()).orElseThrow());
        assertTrue(tokens.authenticate(issued.token() + "x").isEmpty());
        assertFalse(tokens.revoke(user.getId() + 100000, issued.id()));
        assertTrue(tokens.revoke(user.getId(), issued.id()));
        assertTrue(tokens.authenticate(issued.token()).isEmpty());
    }
}
