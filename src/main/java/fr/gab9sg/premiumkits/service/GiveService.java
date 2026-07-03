package fr.gab9sg.premiumkits.service;

import fr.gab9sg.premiumkits.PremiumKits;
import fr.gab9sg.premiumkits.model.Kit;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class GiveService {

    public enum Result {
        SUCCESS, NO_KIT, DISABLED, NO_PERMISSION, COOLDOWN,
        ONE_TIME, MIN_LEVEL, MIN_MONEY, COST_FAIL, PLACEHOLDER_FAIL,
        WORLD_MISMATCH, PREVIEW_PENDING, QUEUED
    }

    private final PremiumKits plugin;
    // playerUuid + kitId -> expires ms
    private final Map<String, Long> cooldowns = new HashMap<>();
    // Anti-spam: last give timestamp per player+kit, minimum 1.5s between identical gives
    private final Map<String, Long> lastGiveAttempt = new HashMap<>();
    private static final long ANTI_SPAM_MS = 1500;

    public GiveService(PremiumKits plugin) { this.plugin = plugin; }

    public Result give(Player player, String kitId) {
        return give(player, kitId, false);
    }

    public Result give(Player player, String kitId, boolean force) {
        Kit kit = plugin.getKitRegistry().getKit(kitId);
        if (kit == null) return Result.NO_KIT;
        if (!kit.isEnabled() && !force) return Result.DISABLED;
        return giveKit(player, kit, force);
    }

    /** Give best kit for player (highest priority they can access) */
    public Result giveBest(Player player) {
        Kit kit = plugin.getKitRegistry().resolveBest(player);
        if (kit == null) return Result.NO_KIT;
        return giveKit(player, kit, false);
    }

    private Result giveKit(Player player, Kit kit, boolean force) {
        // Anti-spam: block rapid repeated clicks on same kit
        if (!force) {
            String spamKey = player.getUniqueId() + ":" + kit.getId();
            long now = System.currentTimeMillis();
            Long last = lastGiveAttempt.get(spamKey);
            if (last != null && now - last < ANTI_SPAM_MS) return Result.COOLDOWN;
            lastGiveAttempt.put(spamKey, now);
        }

        Kit.KitConditions cond = kit.getConditions();

        if (!force) {
            // Access check
            if (!plugin.getKitRegistry().canAccess(player, kit)) return Result.NO_PERMISSION;

            // Event window check
            if (!cond.isEventActive()) return Result.NO_KIT;

            // World restriction
            if (!kit.getAccess().worlds.isEmpty()) {
                String currentWorld = player.getWorld().getName();
                if (kit.getAccess().worlds.stream().noneMatch(w -> w.equalsIgnoreCase(currentWorld))) {
                    return Result.WORLD_MISMATCH;
                }
            }

            // One-time
            if (cond.oneTime && hasReceivedOneTime(player, kit.getId())) return Result.ONE_TIME;

            // Cooldown
            if (!player.hasPermission("premiumkits.bypass.cooldown") && cond.cooldownSeconds > 0) {
                long rem = getRemainingCooldown(player, kit.getId());
                if (rem > 0) return Result.COOLDOWN;
            }

            // Min level
            if (cond.minLevel > 0 && player.getLevel() < cond.minLevel) return Result.MIN_LEVEL;

            // Economy (Vault)
            Economy eco = plugin.getEconomy();
            if (eco != null) {
                if (cond.minMoney > 0 && eco.getBalance(player) < cond.minMoney) return Result.MIN_MONEY;
                if (cond.cost > 0 && eco.getBalance(player) < cond.cost) return Result.COST_FAIL;
            }

            // WorldGuard
            if (cond.worldGuardRegion != null && !cond.worldGuardRegion.isEmpty()) {
                if (plugin.getWorldGuardHook() != null && !plugin.getWorldGuardHook().isInRegion(player, cond.worldGuardRegion)) {
                    return Result.NO_PERMISSION;
                }
            }

            // PlaceholderAPI check
            if (!player.hasPermission("premiumkits.bypass.conditions") && !cond.checkPlaceholder(player)) {
                return Result.PLACEHOLDER_FAIL;
            }

            // Deduct cost
            if (eco != null && cond.cost > 0) {
                eco.withdrawPlayer(player, cond.cost);
                player.sendMessage(plugin.getLang().get("kit-cost-paid", "cost", String.format("%.0f", cond.cost)));
            }
        }

        // Give items
        giveItems(player, kit.getItems());
        incrementTotalKits(player);

        // Record cooldown
        if (cond.cooldownSeconds > 0) {
            String key = player.getUniqueId() + ":" + kit.getId();
            cooldowns.put(key, System.currentTimeMillis() + cond.cooldownSeconds * 1000L);
        }
        if (cond.oneTime) markOneTime(player, kit.getId());

        // Actions
        executeActions(player, kit);

        // Report to panel
        plugin.getPanelHook().reportKitGiven(player, kit.getId());

        return Result.SUCCESS;
    }

    private void giveItems(Player player, Map<Integer, ItemStack> items) {
        Location loc = player.getLocation();
        for (Map.Entry<Integer, ItemStack> e : items.entrySet()) {
            int slot = e.getKey();
            ItemStack item = e.getValue().clone();
            // Armor slots
            if (slot == 36) { player.getInventory().setBoots(item); continue; }
            if (slot == 37) { player.getInventory().setLeggings(item); continue; }
            if (slot == 38) { player.getInventory().setChestplate(item); continue; }
            if (slot == 39) { player.getInventory().setHelmet(item); continue; }
            if (slot == 40) { player.getInventory().setItemInOffHand(item); continue; }
            // Normal slots
            ItemStack existing = player.getInventory().getItem(slot);
            if (existing == null || existing.getType().isAir()) {
                player.getInventory().setItem(slot, item);
            } else {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                leftover.values().forEach(i -> player.getWorld().dropItemNaturally(loc, i));
            }
        }
    }

    private void executeActions(Player player, Kit kit) {
        Kit.KitActions act = kit.getActions();
        if (act == null) return;
        Location loc = player.getLocation();

        // Custom message
        if (act.customMessage != null && !act.customMessage.isEmpty()) {
            player.sendMessage(colorize(act.customMessage.replace("{player}", player.getName()).replace("{kit}", kit.getName())));
        }

        // Broadcast
        if (act.broadcast != null && !act.broadcast.isEmpty()) {
            String msg = colorize(act.broadcast.replace("{player}", player.getName()).replace("{kit}", kit.getName()));
            plugin.getServer().broadcastMessage(msg);
        }

        // Sound
        if (act.sound != null && !act.sound.isEmpty()) {
            try {
                Sound sound = Sound.valueOf(act.sound.toUpperCase());
                player.playSound(loc, sound, act.soundVolume, act.soundPitch);
            } catch (Exception e) {
                plugin.getLogger().warning("[PK] Unknown sound: " + act.sound);
            }
        }

        // Particle
        if (act.particle != null && !act.particle.isEmpty()) {
            try {
                Particle particle = Particle.valueOf(act.particle.toUpperCase());
                if (particle == Particle.DUST && act.particleColor != null) {
                    Color color = hexToColor(act.particleColor);
                    player.getWorld().spawnParticle(particle, loc.add(0,1,0), act.particleCount,
                        0.5, 0.5, 0.5, new Particle.DustOptions(color, 1.5f));
                } else {
                    player.getWorld().spawnParticle(particle, loc.add(0,1,0), act.particleCount, 0.5, 0.5, 0.5);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[PK] Unknown particle: " + act.particle);
            }
        }

        // Console command
        if (act.onReceiveCommand != null && !act.onReceiveCommand.isEmpty()) {
            String cmd = act.onReceiveCommand
                .replace("%player%", player.getName())
                .replace("%uuid%", player.getUniqueId().toString())
                .replace("{player}", player.getName())
                .replace("{kit}", kit.getName())
                .replace("%kit_id%", kit.getId())
                .replace("%kit_count%", String.valueOf(getTotalKitsReceived(player)))
                .replace("%player_balance%", plugin.getEconomy() != null
                    ? String.valueOf((int) plugin.getEconomy().getBalance(player)) : "0")
                .replace("%player_streak%", String.valueOf(
                    plugin.getKillStreakListener() != null
                        ? plugin.getKillStreakListener().getStreak(player.getUniqueId()) : 0));
            plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd));
        }
    }

    // ── Cooldown ───────────────────────────────────────────────────────────────
    public long getRemainingCooldown(Player player, String kitId) {
        String key = player.getUniqueId() + ":" + kitId;
        Long exp = cooldowns.get(key);
        if (exp == null) return 0;
        long rem = exp - System.currentTimeMillis();
        if (rem <= 0) { cooldowns.remove(key); return 0; }
        return rem / 1000;
    }

    public void resetCooldown(Player player, String kitId) {
        if (kitId != null) cooldowns.remove(player.getUniqueId() + ":" + kitId);
        else cooldowns.entrySet().removeIf(e -> e.getKey().startsWith(player.getUniqueId() + ":"));
    }

    // ── One-time tracking (stored in config/data YAML) ─────────────────────────
    private Set<String> oneTimeReceived = new HashSet<>();
    private final Map<UUID, Integer> totalKitsReceived = new HashMap<>();

    private int getTotalKitsReceived(Player player) {
        return totalKitsReceived.getOrDefault(player.getUniqueId(), 0);
    }
    private void incrementTotalKits(Player player) {
        totalKitsReceived.merge(player.getUniqueId(), 1, Integer::sum);
    }
    private boolean hasReceivedOneTime(Player player, String kitId) {
        return oneTimeReceived.contains(player.getUniqueId() + ":" + kitId);
    }
    private void markOneTime(Player player, String kitId) {
        oneTimeReceived.add(player.getUniqueId() + ":" + kitId);
    }
    public void loadOneTime(Set<String> data) { oneTimeReceived = new HashSet<>(data); }
    public Set<String> getOneTimeData() { return Collections.unmodifiableSet(oneTimeReceived); }

    // ── Helpers ────────────────────────────────────────────────────────────────
    private String colorize(String s) { return s == null ? "" : s.replace("&", "\u00a7"); }

    private Color hexToColor(String hex) {
        try {
            hex = hex.replace("#", "");
            int r = Integer.parseInt(hex.substring(0,2), 16);
            int g = Integer.parseInt(hex.substring(2,4), 16);
            int b = Integer.parseInt(hex.substring(4,6), 16);
            return Color.fromRGB(r, g, b);
        } catch (Exception e) { return Color.WHITE; }
    }

    /** Format cooldown in Xm Xs */
    public static String formatCooldown(long seconds) {
        long h = seconds/3600, m = (seconds%3600)/60, s = seconds%60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }
}
