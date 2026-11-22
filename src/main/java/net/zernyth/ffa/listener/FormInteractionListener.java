package net.zernyth.ffa.listener;

import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.player.PlayerFormRespondedEvent;
import net.zernyth.ffa.ui.form.FfaFormController;

public final class FormInteractionListener implements Listener {

    private final FfaFormController formController;

    public FormInteractionListener(FfaFormController formController) {
        this.formController = formController;
    }

    @EventHandler
    public void onFormResponse(PlayerFormRespondedEvent event) {
        formController.handleResponse(
                event.getPlayer(),
                event.getFormID(),
                event.getResponse(),
                event.getResponse() == null
        );
    }
}


