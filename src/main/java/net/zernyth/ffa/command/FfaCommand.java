package net.zernyth.ffa.command;

import org.powernukkitx.Player;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.utils.TextFormat;
import net.zernyth.ffa.ui.form.FfaFormController;

/** {@code /ffa} opens the menu; {@code /ffa <subcommand>} runs one of {@link #subcommands()}. */
public final class FfaCommand extends Command {

    private final FfaFormController formController;
    private final Subcommands subcommands = new Subcommands();

    public FfaCommand(FfaFormController formController) {
        super("ffa", "Open the FFA menu", "/ffa [duel|spectate|team|help]", new String[]{"freeforall"});
        this.formController = formController;
    }

    public Subcommands subcommands() {
        return subcommands;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextFormat.RED + "This command is in-game only.");
            return true;
        }
        if (args.length == 0) {
            formController.openPlayerMain(player);
            return true;
        }
        if (!subcommands.dispatch(player, args)) {
            player.sendMessage(TextFormat.GOLD + "FFA" + TextFormat.GRAY + " | /ffa" + subcommands.usage("ffa"));
        }
        return true;
    }
}
