package geosearch;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RandomDataGenerator {
    private static final double MIN_LAT = -90.0;
    private static final double MIN_LNG = -180.0;
    private static final double MAX_LAT = 90.0;
    private static final double MAX_LNG = 180.0;

    public static List<GeoPoint> generatePoints(int count, long seed) {
        Random random = new Random(seed);
        List<GeoPoint> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double lat = MIN_LAT + random.nextDouble() * (MAX_LAT - MIN_LAT);
            double lng = MIN_LNG + random.nextDouble() * (MAX_LNG - MIN_LNG);
            points.add(new GeoPoint(i, lat, lng));
        }
        return points;
    }
}
