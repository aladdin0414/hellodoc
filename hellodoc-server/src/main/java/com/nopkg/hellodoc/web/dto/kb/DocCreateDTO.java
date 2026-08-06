package com.nopkg.hellodoc.web.dto.kb;

import com.nopkg.hellodoc.enums.DocType;
import lombok.Data;
import java.util.Map;

@Data
public class DocCreateDTO {
    private String name;
    private DocType type;
    private Long parentId;
    private String content;
    private String slug;
    private Integer orderNum;
    private String password;
    private String paperBgColor;
    private String paperBgImage;
    private Map<String, Object> extraMeta;
}
