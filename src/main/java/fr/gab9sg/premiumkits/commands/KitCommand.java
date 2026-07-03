package fr.gab9sg.premiumkits.commands;

import fr.gab9sg.premiumkits.PremiumKits;
import fr.gab9sg.premiumkits.service.GiveService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class KitCommand implements CommandExecutor {

    private final PremiumKits plugin;

    public KitCommand(PremiumKits plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLang().getRaw("players-only")); return true;
        }
        if (!player.hasPermission("premiumkits.use")) {
            plugin.getLang().send(player, "no-permission"); return true;
        }

        // /kit reload
        if (args.length > 0 && args[0].equalsIgnoreCase("reload") && player.hasPermission("premiumkits.admin")) {
            plugin.reload();
            plugin.getLang().send(player, "plugin-reloaded", "amount", String.valueOf(plugin.getKitRegistry().size()));
            return true;
        }

        // /kit random
        if (args.length > 0 && args[0].equalsIgnoreCase("random")) {
            GiveService.Result r = plugin.getRandomKitService().giveRandom(player);
            if (r == GiveService.Result.NO_KIT) plugin.getLang().send(player, "random-no-kits");
            return true;
        }

        // /kit mystery
        if (args.length > 0 && args[0].equalsIgnoreCase("mystery")) {
            plugin.getRandomKitService().giveMystery(player);
            return true;
        }

        // /kit <id> [player] — admin give
        if (args.length >= 1 && player.hasPermission("premiumkits.admin")) {
            String kitId = args[0];
            Player target = args.length >= 2 ? plugin.getServer().getPlayer(args[1]) : player;
            if (target == null) { plugin.getLang().send(player, "player-not-found"); return true; }
            GiveService.Result r = plugin.getGiveService().give(target, kitId, true);
            if (r == GiveService.Result.NO_KIT)
                plugin.getLang().send(player, "kit-not-found", "kit", kitId);
            else if (r == GiveService.Result.SUCCESS)
                plugin.getLang().send(player, "kit-gave-admin", "kit", kitId, "player", target.getName());
            return true;
        }

        // /kit — open GUI
        plugin.getPlayerKitMenuGUI().open(player);
        return true;
    }
}
