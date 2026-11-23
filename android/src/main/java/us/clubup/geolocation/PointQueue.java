package us.clubup.geolocation;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;


final class PointQueue {

    private final TreeMap<Long, TrackedPoint> pointsByMinute = new TreeMap<>();

    void add(TrackedPoint point) {

        TrackedPoint queued = pointsByMinute.get(point.clockMinute());
        if (queued == null || point.timeMs >= queued.timeMs) {
            pointsByMinute.put(point.clockMinute(), point);
        }
    }

    void addAll(List<TrackedPoint> points) {

        for (TrackedPoint point : points) {
            add(point);
        }
    }

    List<TrackedPoint> takeAll() {

        List<TrackedPoint> points = new ArrayList<>(pointsByMinute.values());
        pointsByMinute.clear();
        return points;
    }

    void dropFrom(long timeMs) {

        Iterator<TrackedPoint> iterator = pointsByMinute.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().timeMs >= timeMs) {
                iterator.remove();
            }
        }
    }

    boolean isEmpty() {

        return pointsByMinute.isEmpty();
    }
}
