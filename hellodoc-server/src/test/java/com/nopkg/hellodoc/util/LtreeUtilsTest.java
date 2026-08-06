package com.nopkg.hellodoc.util;

import com.nopkg.hellodoc.utils.LtreeUtils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class LtreeUtilsTest {

    @Test
    public void testGeneratePath() {
        assertEquals("123", LtreeUtils.generatePath(123L));
    }

    @Test
    public void testConcat() {
        assertEquals("1.2.3", LtreeUtils.concat("1.2", "3"));
        assertEquals("1", LtreeUtils.concat("", "1"));
        assertEquals("1", LtreeUtils.concat(null, "1"));
    }

    @Test
    public void testGetParentPath() {
        assertEquals("1.2", LtreeUtils.getParentPath("1.2.3"));
        assertEquals("", LtreeUtils.getParentPath("1"));
        assertEquals("", LtreeUtils.getParentPath(""));
        assertEquals("", LtreeUtils.getParentPath(null));
    }
}
