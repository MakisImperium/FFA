package net.zernyth.ffa.arena.model;

import org.powernukkitx.level.Level;
import org.powernukkitx.level.Location;

public final class SpawnPoint {

    private double x;
    private double y;
    private double z;
    private double yaw;
    private double pitch;

    public SpawnPoint() {
    }

    public SpawnPoint(double x, double y, double z, double yaw, double pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public static SpawnPoint fromLocation(Location location) {
        return new SpawnPoint(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    public Location toLocation(Level level) {
        return new Location(x, y, z, yaw, pitch, level);
    }
}


