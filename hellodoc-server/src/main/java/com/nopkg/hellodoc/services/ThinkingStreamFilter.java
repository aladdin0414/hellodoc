package com.nopkg.hellodoc.services;

/** Incrementally removes model reasoning/control tokens, including split delimiters. */
final class ThinkingStreamFilter {
    private static final String[] OPEN = {"<think>", "<thought>", "<thinking>"};
    private static final String[] CLOSE = {"</think>", "</thought>", "</thinking>"};
    private static final String[] STOP = {"<|im_end|>", "<|endoftext|>", "<|end|>"};
    private final StringBuilder pending = new StringBuilder();
    private String closingTag;
    private boolean stopped;

    String accept(String text) {
        if (stopped) return "";
        pending.append(text);
        return drain(false);
    }

    String finish() { return drain(true); }
    boolean isStopped() { return stopped; }

    private String drain(boolean end) {
        StringBuilder output = new StringBuilder();
        while (!pending.isEmpty() && !stopped) {
            if (closingTag != null) {
                int close = pending.indexOf(closingTag);
                if (close < 0) {
                    // Retain only a possible delimiter suffix, never the reasoning itself.
                    int keep = end ? 0 : Math.min(pending.length(), closingTag.length() - 1);
                    pending.delete(0, pending.length() - keep);
                    break;
                }
                pending.delete(0, close + closingTag.length());
                closingTag = null;
                continue;
            }
            int start = pending.indexOf("<");
            if (start < 0) { output.append(pending); pending.setLength(0); break; }
            if (start > 0) { output.append(pending, 0, start); pending.delete(0, start); }
            String text = pending.toString();
            boolean consumed = false;
            for (int i = 0; i < OPEN.length; i++) {
                if (text.startsWith(OPEN[i])) {
                    pending.delete(0, OPEN[i].length()); closingTag = CLOSE[i]; consumed = true; break;
                }
            }
            if (consumed) continue;
            for (String token : STOP) {
                if (text.startsWith(token)) { stopped = true; pending.setLength(0); break; }
            }
            if (stopped) break;
            boolean prefix = false;
            for (String token : OPEN) prefix |= token.startsWith(text);
            for (String token : STOP) prefix |= token.startsWith(text);
            if (prefix && !end) break;
            output.append(pending.charAt(0)); pending.deleteCharAt(0);
        }
        return output.toString();
    }
}
