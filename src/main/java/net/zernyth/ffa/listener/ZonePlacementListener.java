package net.zernyth.ffa.listener;

import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.player.PlayerInteractEvent;
import org.powernukkitx.event.player.PlayerQuitEvent;
import net.zernyth.ffa.arena.service.SafeZonePlacementService;
import net.zernyth.ffa.ui.form.FfaFormController;

public final class ZonePlacementListener implements Listener {

    private final SafeZonePlacementService safeZonePlacementService;
    private final FfaFormController formController;

    public ZonePlacementListener(SafeZonePlacementService safeZonePlacementService, FfaFormController formController) {
        this.safeZonePlacementService = safeZonePlacementService;
        this.formController = formController;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        SafeZonePlacementService.PlacementResult result = safeZonePlacementService.handleBlockInteraction(event);
        if (result == SafeZonePlacementService.PlacementResult.COMPLETED_SUCCESS
                || result == SafeZonePlacementService.PlacementResult.COMPLETED_FAILED) {
            formController.openAdminZoneMenu(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        safeZonePlacementService.cancel(event.getPlayer().getUniqueId());
    }
}


