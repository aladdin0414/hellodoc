package com.nopkg.hellodoc.controllers;

import com.nopkg.hellodoc.entities.KbKbMember;
import com.nopkg.hellodoc.entities.SysUser;
import com.nopkg.hellodoc.enums.KbRole;
import com.nopkg.hellodoc.security.RequireKbRole;
import com.nopkg.hellodoc.services.KbMemberService;
import com.nopkg.hellodoc.services.KbService;
import com.nopkg.hellodoc.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/kb/{kbId}/members")
@RequiredArgsConstructor
@Tag(name = "知识库成员管理", description = "知识库协作成员管理接口")
public class KbMemberController {

    private final KbMemberService memberService;
    private final KbService kbService;

    record KbMemberVO(Long id, Long userId, String nickname, String email, String avatar, KbRole role) {
    }

    record AddMemberRequest(Long userId, String username, KbRole role) {
    }

    record UpdateMemberRequest(KbRole role) {
    }

    private Long currentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return kbService.requireUserId(username);
    }

    @GetMapping
    @Operation(summary = "成员列表", description = "获取知识库成员列表")
    @RequireKbRole(KbRole.VIEWER)
    public ApiResponse<List<KbMemberVO>> listMembers(@PathVariable Long kbId) {
        List<KbKbMember> members = memberService.listMembers(kbId);
        Set<Long> userIds = members.stream().map(KbKbMember::getUserId).collect(Collectors.toSet());
        Map<Long, SysUser> users = kbService.loadUsersByIds(userIds);
        List<KbMemberVO> result = members.stream()
                .map(member -> {
                    SysUser user = users.get(member.getUserId());
                    return new KbMemberVO(member.getId(), member.getUserId(),
                            user != null ? user.getNickname() : "",
                            user != null ? user.getEmail() : "",
                            user != null ? user.getAvatar() : "",
                            member.getRole());
                })
                .toList();
        return ApiResponse.success(result);
    }

    @PostMapping
    @Operation(summary = "添加成员", description = "添加知识库协作成员")
    @RequireKbRole(KbRole.ADMIN)
    public ApiResponse<KbMemberVO> addMember(@PathVariable Long kbId, @RequestBody AddMemberRequest request) {
        Long operatorId = currentUserId();
        SysUser target;
        if (request.userId() != null) {
            target = kbService.loadUsersByIds(Set.of(request.userId())).get(request.userId());
            if (target == null) {
                throw new com.nopkg.hellodoc.exceptions.ResourceNotFoundException("User", request.userId());
            }
        } else {
            target = kbService.findUserByUsername(request.username())
                    .orElseThrow(() -> new com.nopkg.hellodoc.exceptions.ResourceNotFoundException("User",
                            request.username()));
        }

        KbKbMember member = memberService.addMember(operatorId, kbId, target.getId(), request.role());
        KbMemberVO response = new KbMemberVO(member.getId(), member.getUserId(), target.getNickname(),
                target.getEmail(), target.getAvatar(), member.getRole());
        return ApiResponse.success(response);
    }

    @PutMapping("/{userId}")
    @Operation(summary = "修改角色", description = "修改知识库成员角色")
    @RequireKbRole(KbRole.ADMIN)
    public ApiResponse<KbMemberVO> updateMember(@PathVariable Long kbId, @PathVariable Long userId,
            @RequestBody UpdateMemberRequest request) {
        Long operatorId = currentUserId();
        KbKbMember member = memberService.updateMemberRole(operatorId, kbId, userId, request.role());
        SysUser user = kbService.loadUsersByIds(Set.of(member.getUserId())).get(member.getUserId());
        KbMemberVO response = new KbMemberVO(member.getId(), member.getUserId(),
                user != null ? user.getNickname() : "",
                user != null ? user.getEmail() : "",
                user != null ? user.getAvatar() : "",
                member.getRole());
        return ApiResponse.success(response);
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "移除成员", description = "移除知识库成员")
    @RequireKbRole(KbRole.ADMIN)
    public ApiResponse<Void> removeMember(@PathVariable Long kbId, @PathVariable Long userId) {
        Long operatorId = currentUserId();
        memberService.removeMember(operatorId, kbId, userId);
        return ApiResponse.success(null);
    }

    @PostMapping("/leave")
    @Operation(summary = "主动退出", description = "主动退出知识库")
    public ApiResponse<Void> leave(@PathVariable Long kbId) {
        Long operatorId = currentUserId();
        memberService.removeMember(operatorId, kbId, operatorId);
        return ApiResponse.success(null);
    }
}
