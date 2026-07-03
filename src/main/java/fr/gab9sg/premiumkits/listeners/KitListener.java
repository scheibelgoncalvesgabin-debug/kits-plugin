package fr.gab9sg.premiumkits.listeners;

import fr.gab9sg.premiumkits.PremiumKits;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;

public class KitListener implements Listener {

    private final PremiumKits plugin;

    public KitListener(PremiumKits plugin) { this.plugin = plugin; }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        String title = e.getView().getTitle();

        if (plugin.getPlayerKitMenuGUI().isKitMenu(title)) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getView().getTopInventory())) return;
            boolean right = e.getClick().isRightClick();
            plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getPlayerKitMenuGUI().handleClick(player, e.getRawSlot(), right));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        String title = e.getView().getTitle();
        if (plugin.getPlayerKitMenuGUI().isKitMenu(title))
            plugin.getPlayerKitMenuGUI().cleanup(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.getPlayerKitMenuGUI().cleanup(e.getPlayer());
        plugin.getKillStreakListener().resetPlayer(e.getPlayer().getUniqueId());
    }
}
