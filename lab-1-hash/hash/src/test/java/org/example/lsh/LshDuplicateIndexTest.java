package org.example.lsh;

import org.example.lsh.LshDuplicateIndex.DuplicatePair;
import org.example.lsh.LshDuplicateIndex.DuplicateSearchResult;
import org.example.lsh.LshDuplicateIndex.Point3D;
import org.example.support.RandomDataFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LshDuplicateIndexTest {
    @Test
    void randomPoints() {
        for (long seed = 1L; seed <= 5L; seed++) {
            LshDuplicateIndex index = new LshDuplicateIndex(8, 10_000L + seed);
            index.addAll(RandomDataFactory.pointsWithDuplicates(400, 80, 0.0, seed));

            DuplicateSearchResult byLsh = index.findDoubles();
            DuplicateSearchResult byFullScan = index.fullScanDuplicates();

            assertEquals(pairKeys(byFullScan.duplicates), pairKeys(byLsh.duplicates));
            assertTrue(byLsh.distanceComparisons <= byFullScan.distanceComparisons);
        }
    }

    @Test
    void fullScanMatch() {
        LshDuplicateIndex index = new LshDuplicateIndex(8, 42L);
        index.addAll(List.of(
                new Point3D("a", 1.0, 2.0, 3.0),
                new Point3D("b", 4.0, 5.0, 6.0),
                new Point3D("c", 1.0, 2.0, 3.0),
                new Point3D("d", 7.0, 8.0, 9.0),
                new Point3D("e", 4.0, 5.0, 6.0)
        ));

        DuplicateSearchResult byLsh = index.findDoubles();
        DuplicateSearchResult byFullScan = index.fullScanDuplicates();

        assertEquals(pairKeys(byFullScan.duplicates), pairKeys(byLsh.duplicates));
        assertTrue(byLsh.distanceComparisons <= byFullScan.distanceComparisons);
    }

    @Test
    void addAfterBuild() {
        LshDuplicateIndex index = new LshDuplicateIndex(6, 7L);
        index.add(new Point3D("a", 10.0, 20.0, 30.0));
        index.add(new Point3D("b", -5.0, 3.0, 1.0));
        index.add(new Point3D("c", 10.0, 20.0, 30.0));

        DuplicateSearchResult result = index.findDoubles();

        assertEquals(1, result.duplicates.size());
        DuplicatePair pair = result.duplicates.get(0);
        assertEquals("a", pair.leftId);
        assertEquals("c", pair.rightId);
        assertEquals(0.0, pair.distance);
    }

    private static Set<String> pairKeys(List<DuplicatePair> duplicates) {
        return duplicates.stream()
                .map(DuplicatePair::pairKey)
                .collect(Collectors.toSet());
    }
}
