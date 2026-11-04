package com.nopkg.hellodoc.mcp;

import com.nopkg.hellodoc.entities.KbDocument;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DirectoryOrderTest {
    private KbDocument entry(long id, Integer order) {
        KbDocument document = new KbDocument();
        document.setId(id);
        document.setOrderNum(order);
        return document;
    }

    @Test void sortsNullLastAndResolvesTiesById() {
        var documents = List.of(entry(4, null), entry(3, 10), entry(2, 10), entry(1, -1));
        assertEquals(List.of(1L, 2L, 3L, 4L), DirectoryOrder.ids(documents));
    }

    @Test void acceptsOnlyCompleteUniquePermutations() {
        var current = List.of(1L, 2L, 3L);
        assertTrue(DirectoryOrder.isPermutation(current, List.of(3L, 1L, 2L)));
        assertFalse(DirectoryOrder.isPermutation(current, List.of(1L, 1L, 3L)));
        assertFalse(DirectoryOrder.isPermutation(current, List.of(1L, 2L)));
        assertFalse(DirectoryOrder.isPermutation(current, List.of(1L, 2L, 4L)));
        assertTrue(DirectoryOrder.isPermutation(List.of(), List.of()));
    }
}
