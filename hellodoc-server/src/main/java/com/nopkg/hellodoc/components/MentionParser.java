package com.nopkg.hellodoc.components;

import com.nopkg.hellodoc.entities.SysUser;
import com.nopkg.hellodoc.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class MentionParser {

    // 匹配 @username 的模式。支持字母数字和中文字符。
    // 根据实际用户名策略调整逻辑。
    // 假设用户名/昵称可以包含中文。
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\w\\u4e00-\\u9fa5]+)");

    private final UserRepository userRepository;

    public List<Long> parseMentions(String content) {
        Matcher matcher = MENTION_PATTERN.matcher(content);
        java.util.Set<Long> userIds = new java.util.LinkedHashSet<>();
        while (matcher.find()) {
            String nickname = matcher.group(1);
            // 通过昵称搜索用户（或取决于要求的用户名）
            // 实现细节：可能需要匹配精确的用户名或昵称
            // 目前，假定昵称查找已足够
            // 如果潜在的查找次数很多，我们可能需要缓存此内容或使用更鲁棒的搜索
            userRepository.findByNickname(nickname)
                    .ifPresent(user -> userIds.add(user.getId()));
        }
        return new ArrayList<>(userIds);
    }
}
