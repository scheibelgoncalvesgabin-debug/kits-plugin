package fr.gab9sg.premiumkits.service;

import fr.gab9sg.premiumkits.PremiumKits;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.*;
import java.util.logging.Level;

public class CustomItemService {

    private final PremiumKits plugin;
    private boolean mythicHooked      = false;
    private boolean itemsAdderHooked  = false;
    private boolean oraxenHooked      = false;

    public CustomItemService(PremiumKits plugin) {
        this.plugin = plugin;
        if (plugin.getServer().getPluginManager().getPlugin("MythicMobs")  != null) { mythicHooked     = true; plugin.getLogger().info("[AWK] MythicMobs hooked.");  }
        if (plugin.getServer().getPluginManager().getPlugin("ItemsAdder")  != null) { itemsAdderHooked = true; plugin.getLogger().info("[AWK] ItemsAdder hooked.");  }
        if (plugin.getServer().getPluginManager().getPlugin("Oraxen")      != null) { oraxenHooked     = true; plugin.getLogger().info("[AWK] Oraxen hooked.");      }
    }

    @SuppressWarnings("unchecked")
    public ItemStack buildItem(Map<String, Object> data) {
        if (data == null) return null;
        String type   = (String) data.getOrDefault("type", "STONE");
        int    amount = ((Number) data.getOrDefault("amount", 1)).intValue();
        Map<String, Object> meta = (Map<String, Object>) data.get("meta");

        // External plugin items
        if (meta != null) {
            if (meta.containsKey("mythicId")     && mythicHooked)     { ItemStack i = getMythicItem    ((String)meta.get("mythicId"),     amount); if (i != null) return i; }
            if (meta.containsKey("itemsAdderId") && itemsAdderHooked) { ItemStack i = getItemsAdderItem((String)meta.get("itemsAdderId"), amount); if (i != null) return i; }
            if (meta.containsKey("oraxenId")     && oraxenHooked)     { ItemStack i = getOraxenItem    ((String)meta.get("oraxenId"),     amount); if (i != null) return i; }
        }

        // Standard material
        Material mat;
        try { mat = Material.valueOf(type.toUpperCase()); }
        catch (IllegalArgumentException e) { plugin.getLogger().warning("[AWK] Unknown material: " + type); mat = Material.STONE; }

        ItemStack item = new ItemStack(mat, Math.max(1, Math.min(64, amount)));
        if (meta == null) return item;

        ItemMeta im = item.getItemMeta();
        if (im == null) return item;

        // Display name
        if (meta.containsKey("displayName"))
            im.setDisplayName(colorize((String) meta.get("displayName")));

        // Lore
        if (meta.containsKey("lore"))
            im.setLore(((List<String>) meta.get("lore")).stream().map(this::colorize).toList());

        // Custom Model Data
        if (meta.containsKey("customModelData"))
            try { im.setCustomModelData(((Number) meta.get("customModelData")).intValue()); }
            catch (Exception ignored) {}

        // Enchant glow
        if (Boolean.TRUE.equals(meta.get("enchantGlow"))) {
            im.addEnchant(Enchantment.UNBREAKING, 1, true);
            im.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        // Enchantments — lowercase NamespacedKey is critical
        if (meta.containsKey("enchants")) {
            for (Map<String, Object> ench : (List<Map<String, Object>>) meta.get("enchants")) {
                try {
                    String enchId = ((String) ench.get("id")).toLowerCase().trim();
                    int level = ((Number) ench.getOrDefault("level", 1)).intValue();
                    // Primary: NamespacedKey (Paper 1.21)
                    Enchantment e = Enchantment.getByKey(NamespacedKey.minecraft(enchId));
                    // Fallback: legacy name
                    if (e == null) e = Enchantment.getByName(enchId.toUpperCase());
                    if (e != null) {
                        im.addEnchant(e, level, true); // unsafe=true bypasses level limits
                        plugin.getLogger().fine("[AWK] Applied enchant " + enchId + " lvl " + level);
                    } else {
                        plugin.getLogger().warning("[AWK] Unknown enchantment: " + enchId);
                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning("[AWK] Enchant error: " + ex.getMessage());
                }
            }
        }

        // Potion meta
        if (meta.containsKey("potionEffect") && im instanceof PotionMeta pm) {
            String effectName = ((String) meta.get("potionEffect")).toUpperCase().trim();
            int amp       = meta.containsKey("potionAmplifier") ? ((Number)meta.get("potionAmplifier")).intValue() : 0;
            int dur       = meta.containsKey("potionDuration")  ? ((Number)meta.get("potionDuration")).intValue()  : 3600;
            boolean parts = !Boolean.FALSE.equals(meta.get("potionParticles"));

            PotionEffectType pet = PotionEffectType.getByName(effectName);
            if (pet != null) {
                pm.addCustomEffect(new PotionEffect(pet, dur, amp, false, parts, true), true);
            } else {
                plugin.getLogger().warning("[AWK] Unknown potion effect: " + effectName);
            }
            item.setItemMeta(pm);
            return item;
        }

        item.setItemMeta(im);
        return item;
    }

    private ItemStack getMythicItem(String id, int amount) {
        try {
            Class<?> api = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object inst  = api.getMethod("inst").invoke(null);
            Object mgr   = inst.getClass().getMethod("getItemManager").invoke(inst);
            Optional<?> opt = (Optional<?>) mgr.getClass().getMethod("getItem", String.class).invoke(mgr, id);
            if (opt.isEmpty()) return null;
            return (ItemStack) opt.get().getClass().getMethod("generateItemStack", int.class).invoke(opt.get(), amount);
        } catch (Exception e) { plugin.getLogger().log(Level.WARNING, "[AWK] MythicMobs: " + id, e); return null; }
    }

    private ItemStack getItemsAdderItem(String id, int amount) {
        try {
            Class<?> cls = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object cs = cls.getMethod("getInstance", String.class).invoke(null, id);
            if (cs == null) return null;
            ItemStack item = ((ItemStack) cs.getClass().getMethod("getItemStack").invoke(cs)).clone();
            item.setAmount(amount); return item;
        } catch (Exception e) { plugin.getLogger().log(Level.WARNING, "[AWK] ItemsAdder: " + id, e); return null; }
    }

    private ItemStack getOraxenItem(String id, int amount) {
        try {
            Class<?> cls = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            Object b = cls.getMethod("getItemById", String.class).invoke(null, id);
            if (b == null) return null;
            ItemStack item = ((ItemStack) b.getClass().getMethod("build").invoke(b)).clone();
            item.setAmount(amount); return item;
        } catch (Exception e) { plugin.getLogger().log(Level.WARNING, "[AWK] Oraxen: " + id, e); return null; }
    }

    private String colorize(String s) { return s == null ? "" : s.replace("&", "\u00a7"); }

    public boolean isMythicHooked()     { return mythicHooked; }
    public boolean isItemsAdderHooked() { return itemsAdderHooked; }
    public boolean isOraxenHooked()     { return oraxenHooked; }
}
