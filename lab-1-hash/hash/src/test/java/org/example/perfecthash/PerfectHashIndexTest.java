package org.example.perfecthash;

import org.example.support.RandomDataFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PerfectHashIndexTest {
    @Test
    void shouldFindEveryKeyFromRandomSet() {
        List<String> keys = RandomDataFactory.uniqueKeys(2_000, 101L);
        PerfectHashIndex index = PerfectHashIndex.fromKeys(keys);

        assertEquals(keys.size(), index.size());
        for (int expectedIndex = 0; expectedIndex < keys.size(); expectedIndex++) {
            assertEquals(expectedIndex, index.find(keys.get(expectedIndex)));
        }

        for (String missingKey : RandomDataFactory.missingKeys(200, 777L)) {
            assertEquals(-1, index.find(missingKey));
            assertFalse(index.contains(missingKey));
        }
    }

    @Test
    void shouldRejectDuplicateKeys() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PerfectHashIndex.fromKeys(List.of("a", "b", "a"))
        );
    }

    @Test
    void distinctStringsWithSameJavaHashCodeStayDistinct() {
        PerfectHashIndex index = PerfectHashIndex.fromKeys(List.of("Aa", "BB", "AaAa", "BBBB"));

        assertEquals(4, index.size());
        assertEquals(0, index.find("Aa"));
        assertEquals(1, index.find("BB"));
        assertEquals(2, index.find("AaAa"));
        assertEquals(3, index.find("BBBB"));
    }
}
