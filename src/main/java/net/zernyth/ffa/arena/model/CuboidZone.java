package net.zernyth.ffa.arena.model;

import org.powernukkitx.level.Location;
import org.powernukkitx.math.Vector3;

public final class CuboidZone {

    private String worldName;
    private double minX;
    private double minY;
    private double minZ;
    private double maxX;
    private double maxY;
    private double maxZ;

    public CuboidZone() {
    }

    public CuboidZone(String worldName, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.worldName = worldName;
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public static CuboidZone fromCorners(String worldName, Vector3 first, Vector3 second) {
        return new CuboidZone(
                worldName,
                first.getX(),
                first.getY(),
                first.getZ(),
                second.getX(),
                second.getY(),
                second.getZ()
        );
    }

    public String getWorldName() {
        return worldName;
    }

    public double getMinX() {
        return minX;
    }

    public double getMinY() {
        return minY;
    }

    public double getMinZ() {
        return minZ;
    }

    public double getMaxX() {
        return maxX;
    }

    public double getMaxY() {
        return maxY;
    }

    public double getMaxZ() {
        return maxZ;
    }

    public boolean contains(Location location) {
        if (location == null || location.getLevel() == null) {
            return false;
        }
        return contains(location.getLevel().getName(), location.getX(), location.getY(), location.getZ());
    }

    /** Whether a point in a world lies inside the zone, edges included. */
    public boolean contains(String world, double x, double y, double z) {
        if (worldName == null || world == null || !worldName.equalsIgnoreCase(world)) {
            return false;
        }
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }
}


