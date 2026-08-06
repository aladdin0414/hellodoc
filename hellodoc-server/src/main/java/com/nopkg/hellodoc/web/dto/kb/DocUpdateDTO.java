package com.nopkg.hellodoc.web.dto.kb;

import com.nopkg.hellodoc.enums.DocStatus;
import com.nopkg.hellodoc.enums.DocType;
import lombok.Data;
import java.util.Map;

@Data
public class DocUpdateDTO {
    private String name;
    private DocType type;
    private Long parentId;
    private Integer orderNum;
    private DocStatus status;
    private String content;
    private Boolean isOpen;
    private Boolean isCover;
    private String slug;
    private String password;
    private String paperBgColor;
    private String paperBgImage;
    private Map<String, Object> extraMeta;
}
