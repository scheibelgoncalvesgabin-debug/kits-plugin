package fr.gab9sg.premiumkits.service;

import fr.gab9sg.premiumkits.PremiumKits;
import fr.gab9sg.premiumkits.model.Kit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Queues kits when player inventory is full.
 * When space frees up, auto-delivers queued kits.
 */
public class QueueService implements Listener {

    private final PremiumKits plugin;
    // playerUUID -> list of pending kits
    private final Map<UUID, Queue<String>> queued = new HashMap<>();

    public QueueService(PremiumKits plugin) { this.plugin = plugin; }

    public void queue(Player player, String kitId) {
        queued.computeIfAbsent(player.getUniqueId(), u -> new LinkedList<>()).add(kitId);
        plugin.getLang().send(player, "queue-full");
        plugin.getLang().send(player, "queue-hint");
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        checkAndDeliver(e.getPlayer());
    }

    public void checkAndDeliver(Player player) {
        Queue<String> q = queued.get(player.getUniqueId());
        if (q == null || q.isEmpty()) return;

        int freeSlots = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) freeSlots++;
        }
        if (freeSlots < 3) return; // need at least 3 free slots

        String kitId = q.poll();
        if (kitId == null) return;

        Kit kit = plugin.getKitRegistry().getKit(kitId);
        if (kit == null) return;

        GiveService.Result result = plugin.getGiveService().give(player, kitId, true);
        if (result == GiveService.Result.SUCCESS) {
            plugin.getLang().send(player, "queue-delivered", "kit", kit.getName());
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            if (!q.isEmpty()) {
                plugin.getLang().send(player, "queue-remaining", "amount", String.valueOf(q.size()));
            }
        }
    }

    public int getQueueSize(Player player) {
        Queue<String> q = queued.get(player.getUniqueId());
        return q == null ? 0 : q.size();
    }

    public void cleanup(Player player) { queued.remove(player.getUniqueId()); }
}
