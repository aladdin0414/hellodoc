package com.nopkg.hellodoc.web.dto.kb;

import com.nopkg.hellodoc.enums.Visibility;
import lombok.Data;

@Data
public class KbUpdateDTO {
    private String title;
    private String description;
    private String icon;
    private String color;
    private Boolean allowAnonymous;
    private Visibility visibility;
}
