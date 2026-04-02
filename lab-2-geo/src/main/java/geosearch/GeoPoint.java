package geosearch;

public class GeoPoint {
    public final long id;
    public final double lat;
    public final double lng;

    public GeoPoint(long id, double lat, double lng) {
        this.id = id;
        this.lat = lat;
        this.lng = lng;
    }
}
