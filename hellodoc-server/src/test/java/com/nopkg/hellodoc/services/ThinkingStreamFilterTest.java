package com.nopkg.hellodoc.services;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThinkingStreamFilterTest {
    @Test void handlesEverySplitForAllSupportedReasoningTags() {
        for (String tag : new String[]{"think", "thought", "thinking"}) {
            String text = "prefix<" + tag + ">hidden</" + tag + ">answer";
            for (int split = 0; split <= text.length(); split++) {
                ThinkingStreamFilter filter = new ThinkingStreamFilter();
                assertEquals("prefixanswer", filter.accept(text.substring(0, split)) + filter.accept(text.substring(split)) + filter.finish());
            }
        }
    }
    @Test void preservesNormalHtmlAndTrailingLessThanCharacters() {
        ThinkingStreamFilter filter = new ThinkingStreamFilter();
        assertEquals("<table>x</table> 1 < 2 <", filter.accept("<table>x</table> 1 < 2 <") + filter.finish());
    }
    @Test void dropsUnterminatedReasoning() {
        ThinkingStreamFilter filter = new ThinkingStreamFilter();
        assertEquals("before", filter.accept("before<think>hidden") + filter.finish());
    }
    @Test void stopsAtASplitControlToken() {
        ThinkingStreamFilter filter = new ThinkingStreamFilter();
        assertEquals("answer", filter.accept("answer<|im_") + filter.accept("end|>ignored") + filter.finish());
        assertTrue(filter.isStopped());
        assertEquals("", filter.accept("more ignored text"));
    }
}
