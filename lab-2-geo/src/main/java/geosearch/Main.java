package geosearch;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        Quadtree quadtree = new Quadtree();

        GeoPoint point1 = new GeoPoint(1, 88, 99);
        GeoPoint point2 = new GeoPoint(2, 88, 100);
        GeoPoint point3 = new GeoPoint(3, 77, 66);
        GeoPoint point4 = new GeoPoint(4, 66, 77);
        GeoPoint point5 = new GeoPoint(5, 55, 44);

        quadtree.insert(point1);
        quadtree.insert(point2);
        quadtree.insert(point3);
        quadtree.insert(point4);
        quadtree.insert(point5);

        System.out.println("Точек в дереве " + quadtree.size());
        System.out.println();

        System.out.println("Точки в радиусе");
        List<GeoPoint> radiusPoints = quadtree.searchInRadius(88, 99, 2);
        printPoints(radiusPoints);
        System.out.println();

        System.out.println("Точки в области");
        List<GeoPoint> boxPoints = quadtree.searchInBox(70, 60, 90, 101);
        printPoints(boxPoints);
    }

    private static void printPoints(List<GeoPoint> points) {
        for (GeoPoint point : points) {
            System.out.println(point.id + " " + point.lat + " " + point.lng);
        }
    }
}
