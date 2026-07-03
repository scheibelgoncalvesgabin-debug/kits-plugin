package fr.gab9sg.premiumkits.listeners;

import fr.gab9sg.premiumkits.PremiumKits;
import fr.gab9sg.premiumkits.model.Kit;
import fr.gab9sg.premiumkits.service.GiveService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Auto-give kit on join and on WorldGuard region entry.
 */
public class AutoKitListener implements Listener {

    private final PremiumKits plugin;
    // Track which regions a player is already in to avoid spamming
    private final Map<UUID, Set<String>> playerRegions = new HashMap<>();

    public AutoKitListener(PremiumKits plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        // Delay 2s to let LuckPerms load player data
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            // Give all auto-join kits the player qualifies for
            plugin.getKitRegistry().getAllKits().stream()
                .filter(Kit::isEnabled)
                .filter(k -> Boolean.TRUE.equals(k.getTags().get("auto-join")))
                .filter(k -> plugin.getKitRegistry().canAccess(player, k))
                .forEach(k -> plugin.getGiveService().give(player, k.getId(), false));
        }, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        // Only check when moving to a new block
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
            && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;

        Player player = e.getPlayer();
        if (plugin.getWorldGuardHook() == null || !plugin.getWorldGuardHook().isHooked()) return;

        plugin.getKitRegistry().getAllKits().stream()
            .filter(Kit::isEnabled)
            .filter(k -> {
                String region = k.getConditions().worldGuardRegion;
                return region != null && !region.isEmpty()
                    && Boolean.TRUE.equals(k.getTags().get("auto-region"));
            })
            .filter(k -> plugin.getKitRegistry().canAccess(player, k))
            .filter(k -> plugin.getWorldGuardHook().isInRegion(player, k.getConditions().worldGuardRegion))
            .forEach(k -> {
                Set<String> regions = playerRegions.computeIfAbsent(player.getUniqueId(), u -> new HashSet<>());
                String key = k.getId() + ":" + k.getConditions().worldGuardRegion;
                if (!regions.contains(key)) {
                    regions.add(key);
                    plugin.getGiveService().give(player, k.getId(), false);
                }
            });

        // Remove region keys for regions player has left
        Set<String> current = playerRegions.get(player.getUniqueId());
        if (current != null) {
            current.removeIf(key -> {
                String[] parts = key.split(":");
                if (parts.length < 2) return true;
                return !plugin.getWorldGuardHook().isInRegion(player, parts[1]);
            });
        }
    }

    public void cleanup(Player player) { playerRegions.remove(player.getUniqueId()); }
}
