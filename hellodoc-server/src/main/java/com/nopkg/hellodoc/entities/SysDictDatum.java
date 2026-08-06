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
@Table(name = "sys_dict_data")
public class SysDictDatum {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "dict_type_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private SysDictType dictType;

    @Size(max = 64)
    @NotNull
    @Column(name = "dict_code", nullable = false, length = 64)
    private String dictCode;

    @Size(max = 128)
    @NotNull
    @Column(name = "label", nullable = false, length = 128)
    private String label;

    @Column(name = "label_i18n", length = Integer.MAX_VALUE)
    private String labelI18n;

    @Size(max = 256)
    @NotNull
    @Column(name = "\"value\"", nullable = false, length = 256)
    private String value;

    @Size(max = 16)
    @ColumnDefault("'string'")
    @Column(name = "value_type", length = 16)
    private String valueType;

    @Size(max = 64)
    @Column(name = "css_class", length = 64)
    private String cssClass;

    @Size(max = 256)
    @Column(name = "style_attr", length = 256)
    private String styleAttr;

    @ColumnDefault("0")
    @Column(name = "sort_order")
    private Integer sortOrder;

    @ColumnDefault("false")
    @Column(name = "is_default")
    private Boolean isDefault;

    @ColumnDefault("0")
    @Column(name = "status")
    private Short status;

    @Column(name = "remark", length = Integer.MAX_VALUE)
    private String remark;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "create_time")
    private Instant createTime;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "update_time")
    private Instant updateTime;

}
