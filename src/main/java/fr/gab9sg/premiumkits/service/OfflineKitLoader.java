package fr.gab9sg.premiumkits.service;

import fr.gab9sg.premiumkits.PremiumKits;
import fr.gab9sg.premiumkits.model.Kit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

/**
 * Loads kits from local YAML files in plugins/PremiumKits/kits/
 * Used when premiumkits.enabled = false (offline mode, no panel required).
 * See resources/kits/example_kit.yml for the full commented format.
 */
public class OfflineKitLoader {

    private final PremiumKits plugin;
    private final Map<String, Long> lastModified = new HashMap<>();
    private BukkitTask watchTask;

    public OfflineKitLoader(PremiumKits plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        File kitsDir = new File(plugin.getDataFolder(), "kits");
        if (!kitsDir.exists()) {
            kitsDir.mkdirs();
            // Copy example kit on first run
            plugin.saveResource("kits/example_kit.yml", false);
            plugin.getLogger().info("[PK] Created kits/ folder with example_kit.yml");
        }

        File[] files = kitsDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().info("[PK] No kit files found in kits/ folder.");
            return;
        }

        List<Kit> kits = new ArrayList<>();
        for (File file : files) {
            try {
                Kit kit = loadKitFile(file);
                if (kit != null) {
                    kits.add(kit);
                    lastModified.put(file.getName(), file.lastModified());
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[PK] Error loading kit file " + file.getName(), e);
            }
        }

        plugin.getKitRegistry().setKits(kits);
        plugin.getLogger().info("[PK] Loaded " + kits.size() + " kits from local YAML files (offline mode).");

        startWatcher();
    }

    @SuppressWarnings("unchecked")
    private Kit loadKitFile(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        String id   = yaml.getString("id", file.getName().replace(".yml", "").replace(".yaml", ""));
        String name = yaml.getString("name", id);

        Kit kit = new Kit(id, name);
        kit.setEnabled(yaml.getBoolean("enabled", true));
        kit.setIcon(yaml.getString("icon", "CHEST"));
        kit.setPriority(yaml.getInt("priority", 0));

        // Access
        Kit.KitAccess access = new Kit.KitAccess();
        String accType = yaml.getString("access.type", "EVERYONE");
        try { access.type = Kit.AccessType.valueOf(accType.toUpperCase()); } catch (Exception ignored) {}
        access.groups      = yaml.getStringList("access.groups");
        access.groupLogic  = yaml.getString("access.group-logic", "OR");
        access.permissions = yaml.getStringList("access.permissions");
        access.permLogic   = yaml.getString("access.perm-logic", "AND");
        access.player       = yaml.getString("access.player", null);
        access.worlds       = yaml.getStringList("access.worlds");
        kit.setAccess(access);

        // Conditions
        Kit.KitConditions cond = new Kit.KitConditions();
        cond.cooldownSeconds       = yaml.getLong("conditions.cooldown-seconds", 0);
        cond.minLevel              = yaml.getInt("conditions.min-level", 0);
        cond.minMoney              = yaml.getDouble("conditions.min-money", 0);
        cond.cost                  = yaml.getDouble("conditions.cost", 0);
        cond.oneTime               = yaml.getBoolean("conditions.one-time", false);
        cond.worldGuardRegion      = yaml.getString("conditions.worldguard-region", null);
        cond.requiresPreviewAccept = yaml.getBoolean("conditions.requires-preview-accept", false);

        String eventStart = yaml.getString("conditions.event-start", "");
        String eventEnd   = yaml.getString("conditions.event-end", "");
        if (eventStart != null && !eventStart.isEmpty()) {
            try { cond.eventStart = java.time.LocalDateTime.parse(eventStart)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(); } catch (Exception ignored) {}
        }
        if (eventEnd != null && !eventEnd.isEmpty()) {
            try { cond.eventEnd = java.time.LocalDateTime.parse(eventEnd)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(); } catch (Exception ignored) {}
        }

        // Placeholder checks
        List<?> checksList = yaml.getList("conditions.placeholder-checks");
        if (checksList != null) {
            for (Object o : checksList) {
                if (o instanceof Map) {
                    Map<String, Object> m = (Map<String, Object>) o;
                    Kit.KitConditions.PlaceholderCheck pc = new Kit.KitConditions.PlaceholderCheck();
                    pc.placeholder = (String) m.get("placeholder");
                    pc.operator    = (String) m.getOrDefault("operator", ">=");
                    pc.value       = String.valueOf(m.get("value"));
                    pc.logic       = (String) m.getOrDefault("logic", "AND");
                    if (pc.placeholder != null) cond.placeholderChecks.add(pc);
                }
            }
        }
        kit.setConditions(cond);

        // Actions
        Kit.KitActions act = new Kit.KitActions();
        act.onReceiveCommand = yaml.getString("actions.on-receive-command", null);
        act.customMessage    = yaml.getString("actions.custom-message", null);
        act.broadcast        = yaml.getString("actions.broadcast", null);
        act.sound            = yaml.getString("actions.sound", null);
        act.soundVolume      = (float) yaml.getDouble("actions.sound-volume", 1.0);
        act.soundPitch       = (float) yaml.getDouble("actions.sound-pitch", 1.0);
        act.particle         = yaml.getString("actions.particle", null);
        act.particleCount    = yaml.getInt("actions.particle-count", 20);
        act.particleColor    = yaml.getString("actions.particle-color", null);
        kit.setActions(act);

        // Tags
        Map<String, Object> tags = new HashMap<>();
        tags.put("auto-join",   yaml.getBoolean("tags.auto-join", false));
        tags.put("auto-region", yaml.getBoolean("tags.auto-region", false));
        tags.put("weight",      yaml.getInt("tags.weight", 100));
        tags.put("no-random",   yaml.getBoolean("tags.no-random", false));
        tags.put("mystery",     yaml.getBoolean("tags.mystery", false));
        kit.setTags(tags);

        // Items
        if (yaml.isConfigurationSection("items")) {
            for (String slotStr : yaml.getConfigurationSection("items").getKeys(false)) {
                try {
                    int slot = Integer.parseInt(slotStr);
                    String path = "items." + slotStr;
                    Map<String, Object> data = new HashMap<>();
                    data.put("type", yaml.getString(path + ".type", "STONE"));
                    data.put("amount", yaml.getInt(path + ".amount", 1));

                    if (yaml.isConfigurationSection(path + ".meta")) {
                        Map<String, Object> meta = new HashMap<>();
                        String dn = yaml.getString(path + ".meta.display-name", null);
                        if (dn != null) meta.put("displayName", dn);
                        List<String> lore = yaml.getStringList(path + ".meta.lore");
                        if (!lore.isEmpty()) meta.put("lore", lore);
                        if (yaml.contains(path + ".meta.enchant-glow"))
                            meta.put("enchantGlow", yaml.getBoolean(path + ".meta.enchant-glow"));
                        if (yaml.contains(path + ".meta.custom-model-data"))
                            meta.put("customModelData", yaml.getInt(path + ".meta.custom-model-data"));
                        // External item plugins
                        String mythicId = yaml.getString(path + ".meta.mythic-id", null);
                        if (mythicId != null && !mythicId.isEmpty()) meta.put("mythicId", mythicId);
                        String itemsAdderId = yaml.getString(path + ".meta.itemsadder-id", null);
                        if (itemsAdderId != null && !itemsAdderId.isEmpty()) meta.put("itemsAdderId", itemsAdderId);
                        String oraxenId = yaml.getString(path + ".meta.oraxen-id", null);
                        if (oraxenId != null && !oraxenId.isEmpty()) meta.put("oraxenId", oraxenId);

                        List<?> enchList = yaml.getList(path + ".meta.enchants");
                        if (enchList != null) {
                            List<Map<String, Object>> enchants = new ArrayList<>();
                            for (Object o : enchList) {
                                if (o instanceof Map) {
                                    Map<String, Object> em = (Map<String, Object>) o;
                                    Map<String, Object> e = new HashMap<>();
                                    e.put("id", String.valueOf(em.get("id")).toLowerCase());
                                    e.put("level", em.getOrDefault("level", 1));
                                    enchants.add(e);
                                }
                            }
                            if (!enchants.isEmpty()) meta.put("enchants", enchants);
                        }

                        String potionEffect = yaml.getString(path + ".meta.potion-effect", null);
                        if (potionEffect != null) {
                            meta.put("potionEffect", potionEffect);
                            meta.put("potionAmplifier", yaml.getInt(path + ".meta.potion-amplifier", 0));
                            meta.put("potionDuration", yaml.getInt(path + ".meta.potion-duration", 3600));
                            meta.put("potionParticles", yaml.getBoolean(path + ".meta.potion-particles", true));
                        }

                        if (!meta.isEmpty()) data.put("meta", meta);
                    }

                    org.bukkit.inventory.ItemStack item = plugin.getCustomItemService().buildItem(data);
                    if (item != null) kit.setItem(slot, item);
                } catch (NumberFormatException ignored) {}
            }
        }

        return kit;
    }

    /** Watches kits/ folder for changes and auto-reloads if enabled */
    private void startWatcher() {
        if (watchTask != null) watchTask.cancel();
        if (!plugin.getConfig().getBoolean("offline-mode.auto-reload", true)) return;

        int interval = plugin.getConfig().getInt("offline-mode.watch-interval", 10) * 20;
        watchTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            File kitsDir = new File(plugin.getDataFolder(), "kits");
            File[] files = kitsDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
            if (files == null) return;

            boolean changed = false;
            for (File f : files) {
                Long last = lastModified.get(f.getName());
                if (last == null || f.lastModified() != last) { changed = true; break; }
            }
            if (changed) {
                plugin.getServer().getScheduler().runTask(plugin, this::loadAll);
            }
        }, interval, interval);
    }

    public void stop() {
        if (watchTask != null) watchTask.cancel();
    }
}
