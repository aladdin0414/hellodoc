package com.nopkg.hellodoc.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "sys_social_profile")
public class SysSocialProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "auth_id", nullable = false)
    private SysUserAuth auth;

    @Size(max = 20)
    @Column(name = "source", length = 20)
    private String source;

    @Size(max = 100)
    @Column(name = "open_id", length = 100)
    private String openId;

    @Size(max = 100)
    @Column(name = "union_id", length = 100)
    private String unionId;

    @Size(max = 50)
    @Column(name = "nickname", length = 50)
    private String nickname;

    @Column(name = "gender")
    private Short gender;

    @Size(max = 50)
    @Column(name = "city", length = 50)
    private String city;

    @Size(max = 255)
    @Column(name = "avatar")
    private String avatar;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "create_time")
    private Instant createTime;


}