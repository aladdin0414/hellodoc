package com.nopkg.hellodoc.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "sys_dict_type")
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class SysDictType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Size(max = 64)
    @NotNull
    @Column(name = "dict_code", nullable = false, length = 64)
    private String dictCode;

    @Size(max = 128)
    @NotNull
    @Column(name = "dict_name", nullable = false, length = 128)
    private String dictName;

    @Column(name = "description", length = Integer.MAX_VALUE)
    private String description;

    @ColumnDefault("false")
    @Column(name = "is_system")
    private Boolean isSystem;

    @ColumnDefault("0")
    @Column(name = "status")
    private Short status;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "create_time")
    private Instant createTime;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "update_time")
    private Instant updateTime;


}
