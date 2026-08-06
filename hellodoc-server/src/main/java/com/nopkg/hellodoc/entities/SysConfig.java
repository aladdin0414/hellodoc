package com.nopkg.hellodoc.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.Map;
import com.nopkg.hellodoc.utils.MapToJsonConverter;

@Getter
@Setter
@Entity
@Table(name = "sys_config")
public class SysConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Size(max = 128)
    @NotNull
    @Column(name = "config_key", nullable = false, length = 128)
    private String configKey;

    @Size(max = 128)
    @NotNull
    @Column(name = "config_name", nullable = false, length = 128)
    private String configName;

    @Column(name = "config_value", length = Integer.MAX_VALUE)
    private String configValue;

    @Size(max = 16)
    @ColumnDefault("'string'")
    @Column(name = "value_type", length = 16)
    private String valueType;

    @Size(max = 64)
    @ColumnDefault("'default'")
    @Column(name = "config_group", length = 64)
    private String configGroup;

    @ColumnDefault("false")
    @Column(name = "is_system")
    private Boolean isSystem;

    @ColumnDefault("false")
    @Column(name = "is_frontend")
    private Boolean isFrontend;

    @Column(name = "description", length = Integer.MAX_VALUE)
    private String description;

    @ColumnDefault("0")
    @Column(name = "status")
    private Short status;

    @Convert(converter = MapToJsonConverter.class)
    @Column(name = "config_name_i18n", columnDefinition = "text")
    private Map<String, String> configNameI18n;

    @Convert(converter = MapToJsonConverter.class)
    @Column(name = "description_i18n", columnDefinition = "text")
    private Map<String, String> descriptionI18n;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "create_time")
    private Instant createTime;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "update_time")
    private Instant updateTime;


}