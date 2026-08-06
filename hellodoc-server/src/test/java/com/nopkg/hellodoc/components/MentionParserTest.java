package com.nopkg.hellodoc.components;

import com.nopkg.hellodoc.entities.SysUser;
import com.nopkg.hellodoc.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentionParserTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MentionParser mentionParser;

    @Test
    void parseMentions_ShouldReturnUserIds_WhenUsersExist() {
        String content = "Hello @alice and @bob, how are you?";

        SysUser alice = new SysUser();
        alice.setId(1L);
        when(userRepository.findByNickname("alice")).thenReturn(Optional.of(alice));

        SysUser bob = new SysUser();
        bob.setId(2L);
        when(userRepository.findByNickname("bob")).thenReturn(Optional.of(bob));

        List<Long> result = mentionParser.parseMentions(content);

        assertEquals(2, result.size());
        assertTrue(result.contains(1L));
        assertTrue(result.contains(2L));
    }

    @Test
    void parseMentions_ShouldIgnoreUnknownUsers() {
        String content = "Hello @charlie";

        when(userRepository.findByNickname("charlie")).thenReturn(Optional.empty());

        List<Long> result = mentionParser.parseMentions(content);

        assertTrue(result.isEmpty());
    }

    @Test
    void parseMentions_ShouldHandleNoMentions() {
        String content = "Hello world";

        List<Long> result = mentionParser.parseMentions(content);

        assertTrue(result.isEmpty());
    }

    @Test
    void testParseMentions_ChineseUsername() {
        String content = "你好 @张三 和 @李四";

        SysUser zhangsan = new SysUser();
        zhangsan.setId(10L);
        when(userRepository.findByNickname("张三")).thenReturn(Optional.of(zhangsan));

        SysUser lisi = new SysUser();
        lisi.setId(11L);
        when(userRepository.findByNickname("李四")).thenReturn(Optional.of(lisi));

        List<Long> result = mentionParser.parseMentions(content);

        assertEquals(2, result.size());
        assertTrue(result.contains(10L));
        assertTrue(result.contains(11L));
    }

    @Test
    void testParseMentions_ShouldDeduplicate() {
        String content = "@alice please check, @alice you need to review this @alice";

        SysUser alice = new SysUser();
        alice.setId(1L);
        when(userRepository.findByNickname("alice")).thenReturn(Optional.of(alice));

        List<Long> result = mentionParser.parseMentions(content);

        // Should only return alice once
        assertEquals(1, result.size());
        assertTrue(result.contains(1L));
    }
}
