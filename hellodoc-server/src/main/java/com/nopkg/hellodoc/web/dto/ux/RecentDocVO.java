package com.nopkg.hellodoc.web.dto.ux;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class RecentDocVO {
    private Long id;
    private Long kbId;
    private String kbTitle;
    private String name;
    private String type;
    private OffsetDateTime visitedAt;
}
