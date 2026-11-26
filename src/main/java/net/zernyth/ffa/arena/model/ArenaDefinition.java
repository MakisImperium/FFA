package net.zernyth.ffa.arena.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ArenaDefinition {

    private String arenaId;
    private String displayName;
    private String worldName;
    private ArenaStatus status = ArenaStatus.WAITING;
    private List<SpawnPoint> spawnPoints = new ArrayList<>();
    private Map<String, CuboidZone> zones = new HashMap<>();
    private String safeZoneId;
    /** This arena's own streak rewards ("3": [...]); empty or missing uses the server's. */
    private Map<String, List<net.zernyth.ffa.streak.StreakReward>> streakRewards;
    /** "ffa" (everyone against everyone) or "team" (teams of {@link #teamSize}, no friendly fire). */
    private String mode;
    private int teamSize;

    public ArenaDefinition() {
    }

    public ArenaDefinition(String arenaId, String displayName, String worldName) {
        this.arenaId = arenaId;
        this.displayName = displayName;
        this.worldName = worldName;
        this.status = ArenaStatus.WAITING;
        this.spawnPoints = new ArrayList<>();
        this.zones = new HashMap<>();
    }

    public String getArenaId() {
        return arenaId;
    }

    public String getDisplayName() {
        if (displayName == null || displayName.isBlank()) {
            return arenaId;
        }
        return displayName;
    }

    public String getWorldName() {
        return worldName;
    }

    public ArenaStatus getStatus() {
        if (status == null) {
            status = ArenaStatus.WAITING;
        }
        return status;
    }

    public void setStatus(ArenaStatus status) {
        this.status = status;
    }

    public List<SpawnPoint> getSpawnPoints() {
        if (spawnPoints == null) {
            spawnPoints = new ArrayList<>();
        }
        return spawnPoints;
    }

    public Map<String, CuboidZone> getZones() {
        if (zones == null) {
            zones = new HashMap<>();
        }
        return zones;
    }

    public String getSafeZoneId() {
        return safeZoneId;
    }

    public void setSafeZoneId(String safeZoneId) {
        this.safeZoneId = safeZoneId;
    }

    public Optional<CuboidZone> getSafeZone() {
        if (safeZoneId == null || safeZoneId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(getZones().get(safeZoneId));
    }

    public Map<String, List<net.zernyth.ffa.streak.StreakReward>> getStreakRewards() {
        return streakRewards;
    }

    public boolean isTeamMode() {
        return "team".equalsIgnoreCase(mode == null ? "" : mode.trim());
    }

    /** Players per team in team mode: 2 to 4, default 2. */
    public int getTeamSize() {
        return teamSize <= 0 ? 2 : Math.max(2, Math.min(4, teamSize));
    }

    public void setMode(boolean team, int teamSize) {
        this.mode = team ? "team" : "ffa";
        this.teamSize = team ? Math.max(2, Math.min(4, teamSize)) : 0;
    }

    public void upsertZone(String zoneId, CuboidZone zone) {
        getZones().put(zoneId, zone);
    }
}


