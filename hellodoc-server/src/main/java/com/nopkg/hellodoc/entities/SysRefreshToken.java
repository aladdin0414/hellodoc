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
@Table(name = "sys_refresh_token")
public class SysRefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "user_id", nullable = false)
    private SysUser user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @Size(max = 64)
    @NotNull
    @Column(name = "jti", nullable = false, length = 64, unique = true)
    private String jti;

    @Size(max = 64)
    @NotNull
    @Column(name = "family_id", nullable = false, length = 64)
    private String familyId;

    @Size(max = 64)
    @Column(name = "parent_jti", length = 64)
    private String parentJti;

    @NotNull
    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Size(max = 64)
    @Column(name = "replaced_by_jti", length = 64)
    private String replacedByJti;

    @Size(max = 64)
    @Column(name = "revoked_reason", length = 64)
    private String revokedReason;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "create_time")
    private Instant createTime;
}
