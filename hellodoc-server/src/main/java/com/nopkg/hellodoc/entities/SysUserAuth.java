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
@Table(name = "sys_user_auth")
public class SysUserAuth {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "user_id", nullable = false)
    private SysUser user;

    @Size(max = 20)
    @NotNull
    @Column(name = "identity_type", nullable = false, length = 20)
    private String identityType;

    @Size(max = 100)
    @NotNull
    @Column(name = "identifier", nullable = false, length = 100)
    private String identifier;

    @Size(max = 255)
    @Column(name = "credential")
    private String credential;

    @ColumnDefault("0")
    @Column(name = "status")
    private Short status;

    @ColumnDefault("true")
    @Column(name = "verified")
    private Boolean verified;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "create_time")
    private Instant createTime;


}