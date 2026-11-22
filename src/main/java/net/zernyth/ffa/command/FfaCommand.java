package net.zernyth.ffa.command;

import org.powernukkitx.Player;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.utils.TextFormat;
import net.zernyth.ffa.ui.form.FfaFormController;

public final class FfaCommand extends Command {

    private final FfaFormController formController;

    public FfaCommand(FfaFormController formController) {
        super("ffa", "Open the FFA menu", "/ffa", new String[]{"freeforall"});
        this.formController = formController;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextFormat.RED + "This command is in-game only.");
            return true;
        }
        formController.openPlayerMain(player);
        return true;
    }
}


