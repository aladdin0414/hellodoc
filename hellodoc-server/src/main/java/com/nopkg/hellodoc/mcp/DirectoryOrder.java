package com.nopkg.hellodoc.mcp;

import com.nopkg.hellodoc.entities.KbDocument;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

final class DirectoryOrder {
    private DirectoryOrder() {}

    static Comparator<KbDocument> comparator() {
        return Comparator.comparing(KbDocument::getOrderNum,
                Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(KbDocument::getId);
    }

    static List<Long> ids(List<KbDocument> documents) {
        return documents.stream().sorted(comparator()).map(KbDocument::getId).toList();
    }

    static boolean isPermutation(List<Long> current, List<Long> requested) {
        return current != null && requested != null && current.size() == requested.size()
                && requested.stream().allMatch(id -> id != null && id > 0) && new HashSet<>(requested).size() == requested.size()
                && new HashSet<>(current).equals(new HashSet<>(requested));
    }
}
