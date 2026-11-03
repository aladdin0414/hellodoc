package com.nopkg.hellodoc.mcp;

public class McpVersionConflictException extends RuntimeException {
    public McpVersionConflictException() { super("VERSION_CONFLICT"); }
}
