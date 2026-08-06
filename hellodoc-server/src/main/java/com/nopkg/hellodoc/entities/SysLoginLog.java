package com.nopkg.hellodoc.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "sys_login_log")
public class SysLoginLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "auth_id")
    private Long authId;

    @Size(max = 64)
    @Column(name = "ip", length = 64)
    private String ip;

    @Size(max = 100)
    @Column(name = "device", length = 100)
    private String device;

    @Size(max = 255)
    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "success")
    private Boolean success;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "login_time")
    private Instant loginTime;


}