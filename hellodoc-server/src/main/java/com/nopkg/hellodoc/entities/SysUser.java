package com.nopkg.hellodoc.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "sys_user")
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class SysUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Size(max = 50)
    @Column(name = "nickname", length = 50)
    private String nickname;

    @Size(max = 50)
    @Column(name = "real_name", length = 50)
    private String realName;

    @Size(max = 255)
    @Column(name = "avatar")
    private String avatar;

    @Size(max = 20)
    @Column(name = "phone", length = 20)
    private String phone;

    @Size(max = 100)
    @Column(name = "email", length = 100)
    private String email;

    @ColumnDefault("'AUTO'")
    @Size(max = 16)
    @Column(name = "language_mode", length = 16)
    private String languageMode = "AUTO";

    @ColumnDefault("0")
    @Column(name = "status")
    private Short status;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "create_time")
    private Instant createTime;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "update_time")
    private Instant updateTime;

    @Transient
    private String username;

    @Transient
    private List<String> roles = new ArrayList<>();
}
