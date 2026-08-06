package com.nopkg.hellodoc.web.dto.user;

import com.nopkg.hellodoc.entities.SysUser;
import lombok.Data;

@Data
public class SysUserVO {
    private Long id;
    private String nickname;
    private String avatar;

    public static SysUserVO from(SysUser user) {
        if (user == null) {
            return null;
        }
        SysUserVO vo = new SysUserVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        return vo;
    }
}
