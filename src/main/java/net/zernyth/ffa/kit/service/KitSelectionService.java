package net.zernyth.ffa.kit.service;

import net.zernyth.ffa.player.service.PlayerProfileService;

import java.util.Optional;
import java.util.UUID;

public final class KitSelectionService {

    private final KitRegistry kitRegistry;
    private final PlayerProfileService playerProfileService;

    /** A player's level; everyone is level 1 until progression is wired in. */
    private java.util.function.ToIntFunction<UUID> levels = id -> 1;

    public KitSelectionService(KitRegistry kitRegistry, PlayerProfileService playerProfileService) {
        this.kitRegistry = kitRegistry;
        this.playerProfileService = playerProfileService;
    }

    public void setLevels(java.util.function.ToIntFunction<UUID> levels) {
        this.levels = levels;
    }

    /** Whether the player's level allows this kit. */
    public boolean isUnlocked(UUID playerUniqueId, net.zernyth.ffa.kit.model.KitDefinition kit) {
        return levels.applyAsInt(playerUniqueId) >= kit.getRequiredLevel();
    }

    public String resolveKitForPlayer(UUID playerUniqueId) {
        // A kit that is gone, or locked again (an admin raised its level), falls back to the default.
        Optional<String> selectedKit = playerProfileService.getSelectedKit(playerUniqueId)
                .filter(kitId -> kitRegistry.findKit(kitId).map(kit -> isUnlocked(playerUniqueId, kit)).orElse(false));

        if (selectedKit.isPresent()) {
            return selectedKit.get();
        }

        Optional<String> defaultKit = kitRegistry.getDefaultKit().map(kit -> kit.getKitId());
        defaultKit.ifPresent(kitId -> playerProfileService.setSelectedKit(playerUniqueId, kitId));
        return defaultKit.orElse(null);
    }

    public boolean selectKit(UUID playerUniqueId, String kitId) {
        var kit = kitRegistry.findKit(kitId);
        if (kit.isEmpty() || !isUnlocked(playerUniqueId, kit.get())) {
            return false;
        }
        playerProfileService.setSelectedKit(playerUniqueId, kitId);
        return true;
    }
}


