package net.zernyth.ffa.command;

import org.powernukkitx.Player;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.utils.TextFormat;
import net.zernyth.ffa.ui.form.FfaFormController;

public final class FfaAdminCommand extends Command {

    private final FfaFormController formController;

    public FfaAdminCommand(FfaFormController formController) {
        super("ffaadmin", "Open the FFA admin menu", "/ffaadmin", new String[]{"ffaadm"});
        setPermission("ffa.admin");
        this.formController = formController;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextFormat.RED + "This command is in-game only.");
            return true;
        }
        if (!player.isOp() && !player.hasPermission("ffa.admin")) {
            player.sendMessage(TextFormat.RED + "You do not have permission to use this command.");
            return true;
        }
        formController.openAdminMain(player);
        return true;
    }
}


