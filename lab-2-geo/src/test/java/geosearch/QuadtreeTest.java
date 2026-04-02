package geosearch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class jertQuadtreeTest {

    @Test
    void insertSeveralPoints() {
        Quadtree quadtree = new Quadtree();

        quadtree.insert(new GeoPoint(1, 55.7500, 37.6100));
        quadtree.insert(new GeoPoint(2, 55.7550, 37.6150));
        quadtree.insert(new GeoPoint(3, 55.9000, 37.9000));

        assertEquals(3, quadtree.size());
    }

    @Test
    void searchInRadiusWorkOnSmallManualExample() {
        Quadtree quadtree = new Quadtree();

        quadtree.insert(new GeoPoint(1, 55.7500, 37.6100));
        quadtree.insert(new GeoPoint(2, 55.7550, 37.6150));
        quadtree.insert(new GeoPoint(3, 55.9000, 37.9000));

        assertEquals(List.of(1L, 2L), sortedIds(quadtree.searchInRadius(55.7520, 37.6120, 0.01)));
        assertEquals(List.of(1L, 2L), sortedIds(quadtree.searchInBox(55.74, 37.60, 55.76, 37.62)));
    }

    @Test
    void searchIncludesPointsOnWorldBorders() {
        Quadtree quadtree = new Quadtree();

        quadtree.insert(new GeoPoint(1, -90.0, -180.0));
        quadtree.insert(new GeoPoint(2, 90.0, 180.0));

        assertEquals(List.of(1L), sortedIds(quadtree.searchInRadius(-90.0, -180.0, 0.0)));
        assertEquals(List.of(2L), sortedIds(quadtree.searchInRadius(90.0, 180.0, 0.0)));
    }

    @Test
    void duplicateCoordinatesDoNotBreakTree() {
        Quadtree quadtree = new Quadtree();

        for (int i = 0; i < 20; i++) {
            quadtree.insert(new GeoPoint(i, 10.0, 10.0));
        }

        assertEquals(20, quadtree.size());
        assertEquals(20, quadtree.searchInRadius(10.0, 10.0, 0.0).size());
    }

    @Test
    void randomizedSearchMatchesNaive() {
        List<GeoPoint> points = RandomDataGenerator.generatePoints(2_000, 123L);
        List<GeoPoint> queries = RandomDataGenerator.generatePoints(200, 456L);
        Random random = new Random(789L);

        Quadtree quadtree = new Quadtree();
        NaiveGeoIndex naive = new NaiveGeoIndex();

        for (GeoPoint point : points) {
            quadtree.insert(point);
            naive.insert(point);
        }

        for (GeoPoint query : queries) {
            double radius = 0.1 + random.nextDouble() * 15.0;
            assertEquals(
                    sortedIds(naive.searchInRadius(query.lat, query.lng, radius)),
                    sortedIds(quadtree.searchInRadius(query.lat, query.lng, radius))
            );
        }
    }

    private List<Long> sortedIds(List<GeoPoint> points) {
        List<Long> ids = new ArrayList<>();
        for (GeoPoint point : points) {
            ids.add(point.id);
        }
        Collections.sort(ids);
        return ids;
    }
}
