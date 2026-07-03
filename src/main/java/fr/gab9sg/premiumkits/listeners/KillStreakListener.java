package fr.gab9sg.premiumkits.listeners;

import fr.gab9sg.premiumkits.PremiumKits;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class KillStreakListener implements Listener {

    private final PremiumKits plugin;
    private final Map<UUID, Integer> kills  = new HashMap<>();
    private final Map<UUID, Integer> deaths = new HashMap<>();

    public KillStreakListener(PremiumKits plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();

        // Reset victim streak
        kills.put(victim.getUniqueId(), 0);

        // Revenge kit
        if (plugin.getConfig().getBoolean("revenge.enabled", false)) {
            int deathCount = deaths.merge(victim.getUniqueId(), 1, Integer::sum);
            int threshold  = plugin.getConfig().getInt("revenge.threshold", 3);
            if (deathCount >= threshold) {
                deaths.put(victim.getUniqueId(), 0);
                String kitId = plugin.getConfig().getString("revenge.kit-id", "");
                if (!kitId.isEmpty()) {
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (victim.isOnline()) {
                            plugin.getGiveService().give(victim, kitId, true);
                            plugin.getLang().send(victim, "revenge-received");
                        }
                    }, 60L);
                }
            }
        }

        if (killer == null || !killer.isOnline()) return;
        deaths.put(killer.getUniqueId(), 0);

        // Kill streak
        if (!plugin.getConfig().getBoolean("killstreak.enabled", false)) return;
        int count     = kills.merge(killer.getUniqueId(), 1, Integer::sum);
        int threshold = plugin.getConfig().getInt("killstreak.threshold", 5);

        if (count > 0 && count % threshold == 0) {
            String kitId = plugin.getConfig().getString("killstreak.kit-id", "");
            if (!kitId.isEmpty()) plugin.getGiveService().give(killer, kitId, true);
            if (plugin.getConfig().getBoolean("killstreak.broadcast", true)) {
                String msg = plugin.getConfig().getString("killstreak.broadcast-message",
                    "&6[PK] &e{player} &7is on a &c{kills} kill streak!")
                    .replace("{player}", killer.getName())
                    .replace("{kills}", String.valueOf(count))
                    .replace("&", "\u00a7");
                plugin.getServer().broadcastMessage(msg);
            }
        }
    }

    public void resetPlayer(UUID uuid) { kills.remove(uuid); deaths.remove(uuid); }
    public int getStreak(UUID uuid) { return kills.getOrDefault(uuid, 0); }
}
