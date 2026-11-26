package net.zernyth.ffa.admin;

import net.zernyth.ffa.arena.model.CuboidZone;
import org.powernukkitx.math.Vector3;

import java.util.ArrayList;
import java.util.List;

/** Points along the 12 edges of a zone, spaced so that at most {@code maxPoints} are drawn. */
public final class ZoneOutline {
    private ZoneOutline() {
    }

    public static List<Vector3> points(CuboidZone zone, int maxPoints) {
        double x0 = zone.getMinX();
        double y0 = zone.getMinY();
        double z0 = zone.getMinZ();
        double x1 = zone.getMaxX() + 1;
        double y1 = zone.getMaxY() + 1;
        double z1 = zone.getMaxZ() + 1;
        double edges = 4 * ((x1 - x0) + (y1 - y0) + (z1 - z0));
        double step = Math.max(0.5, edges / Math.max(12, maxPoints));
        List<Vector3> points = new ArrayList<>();
        double[][] corners = {{x0, y0, z0}, {x1, y0, z0}, {x0, y1, z0}, {x0, y0, z1}, {x1, y1, z0}, {x1, y0, z1}, {x0, y1, z1}, {x1, y1, z1}};
        int[][] edgePairs = {{0, 1}, {0, 2}, {0, 3}, {1, 4}, {1, 5}, {2, 4}, {2, 6}, {3, 5}, {3, 6}, {4, 7}, {5, 7}, {6, 7}};
        for (int[] edge : edgePairs) {
            double[] a = corners[edge[0]];
            double[] b = corners[edge[1]];
            double length = Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]);
            int segments = Math.max(1, (int) Math.ceil(length / step));
            for (int i = 0; i <= segments; i++) {
                double f = (double) i / segments;
                points.add(new Vector3(a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f));
            }
        }
        return points;
    }
}
