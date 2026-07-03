package fr.gab9sg.premiumkits.gui;

import fr.gab9sg.premiumkits.PremiumKits;
import fr.gab9sg.premiumkits.model.Kit;
import fr.gab9sg.premiumkits.service.GiveService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public class PlayerKitMenuGUI {

    private final PremiumKits plugin;
    static final String TITLE_PREFIX = "\u00a76\u00a7lPremiumKits \u00a78\u00bb\u00a7r ";
    private final Map<UUID, Map<Integer, String>> slotToKit = new HashMap<>();

    public PlayerKitMenuGUI(PremiumKits plugin) { this.plugin = plugin; }

    public void open(Player player) {
        List<Kit> kits = plugin.getKitRegistry().getAccessibleKits(player);

        if (kits.isEmpty()) {
            player.sendMessage("\u00a78[\u00a76PK\u00a78] \u00a7cNo kits available for you.");
            return;
        }

        int size = Math.min(54, Math.max(9, ((kits.size()-1)/9+1)*9));
        String title = TITLE_PREFIX + kits.size() + " kit(s)";
        Inventory inv = Bukkit.createInventory(null, size, title);

        Map<Integer, String> map = new HashMap<>();
        for (int i = 0; i < kits.size() && i < 54; i++) {
            Kit kit = kits.get(i);
            inv.setItem(i, buildIcon(player, kit));
            map.put(i, kit.getId());
        }

        slotToKit.put(player.getUniqueId(), map);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.7f, 1.0f);
    }

    private ItemStack buildIcon(Player player, Kit kit) {
        Material mat;
        try { mat = Material.valueOf(kit.getIcon().toUpperCase()); }
        catch (Exception e) { mat = getFirstItemMat(kit); }

        long rem = plugin.getGiveService().getRemainingCooldown(player, kit.getId());
        boolean ready = rem == 0;
        boolean passesPH = kit.getConditions().checkPlaceholder(player);

        if (!ready || !passesPH) mat = Material.CLOCK;

        ItemStack icon = new ItemStack(mat);
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return icon;

        String status = !passesPH ? "\u00a7c\u2716 " : !ready ? "\u00a7e\u23f3 " : "\u00a7a\u25ba ";
        meta.setDisplayName(status + "\u00a7l\u00a7f" + kit.getName());

        List<String> lore = new ArrayList<>();
        lore.add("\u00a78\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");

        // Access
        Kit.KitAccess ac = kit.getAccess();
        String accessStr = switch (ac.type) {
            case GROUP      -> "\u00a7d\uD83C\uDFF7\uFE0F " + (ac.group != null ? ac.group : "group");
            case PERMISSION -> "\u00a7e\uD83D\uDD11 " + (ac.permission != null ? ac.permission : "perm");
            case PLAYER     -> "\u00a7b\uD83D\uDC64 Personal";
            case WORLD      -> "\u00a72\uD83C\uDF0D " + String.join(", ", ac.worlds);
            default         -> "\u00a77\uD83D\uDC65 Everyone";
        };
        lore.add("\u00a77Access: " + accessStr);
        lore.add("\u00a77Items:  \u00a7e" + kit.getItems().size());

        // Conditions
        Kit.KitConditions c = kit.getConditions();
        if (c.cooldownSeconds > 0) lore.add("\u00a77Cooldown: \u00a7e" + GiveService.formatCooldown(c.cooldownSeconds));
        if (c.minLevel > 0)        lore.add("\u00a77Min Level: \u00a7e" + c.minLevel);
        if (c.minMoney > 0)        lore.add("\u00a77Min Money: \u00a76$\u00a7e" + String.format("%.0f", c.minMoney));
        if (c.cost > 0)            lore.add("\u00a77Cost: \u00a76$\u00a7e" + String.format("%.0f", c.cost));
        if (c.oneTime)             lore.add("\u00a76\u26a0 One-time kit");
        if (c.placeholderCheck != null && c.placeholderCheck.placeholder != null) {
            lore.add("\u00a77Requires: \u00a7e" + c.placeholderCheck.placeholder
                + " " + c.placeholderCheck.operator + " " + c.placeholderCheck.value);
        }
        if (kit.getPriority() > 0) lore.add("\u00a77Priority: \u00a7d" + kit.getPriority());

        lore.add("\u00a78\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");

        if (!passesPH) {
            lore.add("\u00a7c\u2716 Conditions not met");
        } else if (!ready) {
            lore.add("\u00a7e\u23f3 Cooldown: \u00a7c" + GiveService.formatCooldown(rem));
            lore.add(buildBar(player, kit));
            lore.add("\u00a77Right-click \u00a78\u00bb \u00a77Preview");
        } else {
            lore.add("\u00a7a\u2714 Available!");
            lore.add("\u00a7eLeft-click \u00a78\u00bb \u00a77Receive");
            lore.add("\u00a7eRight-click \u00a78\u00bb \u00a77Preview");
        }

        meta.setLore(lore);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES, org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        icon.setItemMeta(meta);
        return icon;
    }

    private Material getFirstItemMat(Kit kit) {
        for (int s = 0; s <= 8; s++) {
            ItemStack i = kit.getItems().get(s);
            if (i != null && !i.getType().isAir()) return i.getType();
        }
        return kit.getItems().values().stream().filter(i -> !i.getType().isAir()).map(ItemStack::getType).findFirst().orElse(Material.CHEST);
    }

    private String buildBar(Player player, Kit kit) {
        long cd = kit.getConditions().cooldownSeconds;
        if (cd <= 0) return "";
        long rem = plugin.getGiveService().getRemainingCooldown(player, kit.getId());
        int bars = (int) ((1.0 - (double) rem / cd) * 20);
        StringBuilder sb = new StringBuilder("\u00a78[");
        for (int i = 0; i < 20; i++) sb.append(i < bars ? "\u00a7a|" : "\u00a77|");
        return sb + "\u00a78]";
    }

    public void handleClick(Player player, int slot, boolean right) {
        Map<Integer, String> map = slotToKit.get(player.getUniqueId());
        if (map == null) return;
        String kitId = map.get(slot);
        if (kitId == null) return;
        player.closeInventory();

        Kit kit = plugin.getKitRegistry().getKit(kitId);
        if (kit == null) return;

        if (right) {
            // Preview — open preview GUI
            player.sendMessage("\u00a78[\u00a76PK\u00a78] \u00a7ePreview: \u00a7f" + kit.getName() + " \u00a78(\u00a77" + kit.getItems().size() + " items\u00a78)");
            // TODO: open preview inventory if needed
            return;
        }

        GiveService.Result result = plugin.getGiveService().give(player, kitId);
        boolean failed = result != GiveService.Result.SUCCESS;
        if (failed) player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 0.9f);

        switch (result) {
            case SUCCESS -> player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
            case DISABLED       -> player.sendMessage(c("&8[&6PK&8] &cThis kit is disabled."));
            case NO_PERMISSION  -> player.sendMessage(c("&8[&6PK&8] &cYou don't have access to this kit."));
            case COOLDOWN       -> {
                long rem = plugin.getGiveService().getRemainingCooldown(player, kitId);
                player.sendMessage(c("&8[&6PK&8] &eKit on cooldown: &c" + GiveService.formatCooldown(rem)));
            }
            case ONE_TIME       -> player.sendMessage(c("&8[&6PK&8] &cYou already received this one-time kit."));
            case MIN_LEVEL      -> player.sendMessage(c("&8[&6PK&8] &cYou don't meet the minimum level."));
            case MIN_MONEY      -> player.sendMessage(c("&8[&6PK&8] &cYou don't have enough money."));
            case COST_FAIL      -> player.sendMessage(c("&8[&6PK&8] &cYou can't afford this kit ($" + (int)kit.getConditions().cost + ")."));
            case PLACEHOLDER_FAIL -> player.sendMessage(c("&8[&6PK&8] &cYou don't meet the required conditions."));
            case WORLD_MISMATCH -> player.sendMessage(c("&8[&6PK&8] &cThis kit is not available in this world."));
            default -> player.sendMessage(c("&8[&6PK&8] &cCould not give kit."));
        }
    }

    public boolean isKitMenu(String title) { return title != null && title.startsWith(TITLE_PREFIX); }
    public void cleanup(Player player) { slotToKit.remove(player.getUniqueId()); }

    private String c(String s) { return s.replace("&", "\u00a7"); }
}
