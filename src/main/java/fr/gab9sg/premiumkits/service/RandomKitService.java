package fr.gab9sg.premiumkits.service;

import fr.gab9sg.premiumkits.PremiumKits;
import fr.gab9sg.premiumkits.model.Kit;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Weighted random kit selection.
 * Each kit can have a "weight" tag (default 100).
 * Higher weight = more likely to be selected.
 * Usage: /kit random
 */
public class RandomKitService {

    private final PremiumKits plugin;
    private final Random rng = new Random();

    public RandomKitService(PremiumKits plugin) { this.plugin = plugin; }

    /**
     * Give a random kit the player can access.
     * Weighted by kit priority (or explicit weight tag).
     */
    public GiveService.Result giveRandom(Player player) {
        List<Kit> accessible = plugin.getKitRegistry().getAccessibleKits(player).stream()
            .filter(k -> !Boolean.TRUE.equals(k.getTags().get("no-random")))
            .toList();

        if (accessible.isEmpty()) return GiveService.Result.NO_KIT;

        // Build weighted pool
        int totalWeight = accessible.stream()
            .mapToInt(k -> {
                Object w = k.getTags().get("weight");
                if (w instanceof Number n) return n.intValue();
                return Math.max(1, 10 + k.getPriority()); // default: priority-based
            }).sum();

        int roll = rng.nextInt(totalWeight);
        int cumulative = 0;
        for (Kit kit : accessible) {
            Object w = kit.getTags().get("weight");
            int weight = (w instanceof Number n) ? n.intValue() : Math.max(1, 10 + kit.getPriority());
            cumulative += weight;
            if (roll < cumulative) {
                player.sendMessage("\u00a78[\u00a76PK\u00a78] \u00a77\uD83C\uDFB2 Kit al\u00e9atoire: \u00a7e" + kit.getName());
                return plugin.getGiveService().give(player, kit.getId(), false);
            }
        }

        // Fallback: give first
        return plugin.getGiveService().give(player, accessible.get(0).getId(), false);
    }

    /**
     * Give a mystery kit (hidden — player doesn't know what they'll get).
     */
    public GiveService.Result giveMystery(Player player) {
        player.sendMessage("\u00a78[\u00a76PK\u00a78] \u00a77\u2753 Kit myst\u00e8re en approche...");
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) giveRandom(player);
        }, 40L);
        return GiveService.Result.SUCCESS;
    }
}
