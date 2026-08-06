package com.nopkg.hellodoc.web.dto.user;

import com.nopkg.hellodoc.entities.SysUser;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
public class SysUserDetailVO extends SysUserVO {
    private String username;
    private String realName;
    private String email;
    private String phone;
    private String role; // Added role field
    private Short status;
    private Instant createTime;
    private Instant updateTime;

    public static SysUserDetailVO from(SysUser user) {
        if (user == null) {
            return null;
        }
        SysUserDetailVO vo = new SysUserDetailVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setRealName(user.getRealName());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        // Fill role information
        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            vo.setRole(user.getRoles().stream().findFirst().get());
        } else {
            vo.setRole("user");
        }
        return vo;
    }
}
