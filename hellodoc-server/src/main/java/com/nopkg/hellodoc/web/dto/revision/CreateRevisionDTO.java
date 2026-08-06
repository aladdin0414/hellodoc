package com.nopkg.hellodoc.web.dto.revision;

import lombok.Data;

@Data
public class CreateRevisionDTO {
    private String content;
    private String message;
}
