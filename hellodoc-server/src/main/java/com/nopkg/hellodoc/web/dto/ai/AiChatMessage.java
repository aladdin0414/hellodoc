package com.nopkg.hellodoc.web.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiChatMessage {
    @NotBlank @Pattern(regexp = "user|assistant")
    private String role;
    @NotBlank @Size(max = 32000)
    private String content;
}
