package com.nopkg.hellodoc.websocket;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class WsMessage {
    private String type; // operation, cursor, presence, sync, ack, error, reconnect
    private Object data;
    private Long timestamp;
    private String messageId; // For ACK confirmation

    public WsMessage(String type, Object data) {
        this.type = type;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }
}
