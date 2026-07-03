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
            sender.sendMessage("\u00a7cPlayers only."); return true;
        }
        if (!player.hasPermission("premiumkits.use")) {
            player.sendMessage(c("&8[&6PK&8] &cYou don't have permission.")); return true;
        }

        // /kit reload
        if (args.length > 0 && args[0].equalsIgnoreCase("reload") && player.hasPermission("premiumkits.admin")) {
            plugin.reload();
            player.sendMessage(c("&8[&6PK&8] &aPlugin reloaded! &7(" + plugin.getKitRegistry().size() + " kits)"));
            return true;
        }

        // /kit random
        if (args.length > 0 && args[0].equalsIgnoreCase("random")) {
            GiveService.Result r = plugin.getRandomKitService().giveRandom(player);
            if (r == GiveService.Result.NO_KIT) player.sendMessage(c("&8[&6PK&8] &cAucun kit disponible."));
            return true;
        }

        // /kit mystery
        if (args.length > 0 && args[0].equalsIgnoreCase("mystery")) {
            plugin.getRandomKitService().giveMystery(player);
            return true;
        }

        // /kit <id> [player]
        if (args.length >= 1 && player.hasPermission("premiumkits.admin")) {
            String kitId = args[0];
            Player target = args.length >= 2 ? plugin.getServer().getPlayer(args[1]) : player;
            if (target == null) { player.sendMessage(c("&cPlayer not found.")); return true; }
            GiveService.Result r = plugin.getGiveService().give(target, kitId, true);
            if (r == GiveService.Result.NO_KIT) player.sendMessage(c("&cKit '" + kitId + "' not found."));
            else if (r == GiveService.Result.SUCCESS) player.sendMessage(c("&aGave kit &e" + kitId + "&a to &e" + target.getName()));
            return true;
        }

        // /kit — open menu
        plugin.getPlayerKitMenuGUI().open(player);
        return true;
    }

    private String c(String s) { return s.replace("&", "\u00a7"); }
}
