package geosearch;

import java.util.ArrayList;
import java.util.List;

public class NaiveGeoIndex {
    private final List<GeoPoint> points = new ArrayList<>();

    public void insert(GeoPoint point) {
        points.add(point);
    }

    public List<GeoPoint> searchInRadius(double lat, double lng, double radius) {
        List<GeoPoint> result = new ArrayList<>();
        double radiusSquared = radius * radius;
        for (GeoPoint point : points) {
            double dLat = point.lat - lat;
            double dLng = point.lng - lng;
            if (dLat * dLat + dLng * dLng <= radiusSquared) {
                result.add(point);
            }
        }
        return result;
    }

    public int size() {
        return points.size();
    }
}
