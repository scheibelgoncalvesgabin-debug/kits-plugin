package fr.gab9sg.premiumkits.hooks;

import fr.gab9sg.premiumkits.PremiumKits;
import fr.gab9sg.premiumkits.model.Kit;
import fr.gab9sg.premiumkits.service.CustomItemService;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

public class PanelHook {

    private final PremiumKits plugin;
    private String apiKey;
    private String baseUrl;
    private boolean enabled;
    private BukkitTask pullTask;

    public PanelHook(PremiumKits plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("premiumkits.enabled", false);
        this.apiKey  = plugin.getConfig().getString("premiumkits.api-key", "");
        this.baseUrl = plugin.getConfig().getString("premiumkits.url", "https://vertex-panel-kits.onrender.com");
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        if (enabled && !apiKey.isEmpty()) {
            plugin.getLogger().info("[PK] Panel enabled → " + baseUrl);
            pullKitsFromPanel();
            startHeartbeat();
        }
    }

    public boolean isEnabled() { return enabled && !apiKey.isEmpty(); }

    // ── Pull all kits ──────────────────────────────────────────────────────────
    public void pullKitsFromPanel() {
        if (!isEnabled()) return;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getLogger().info("[PK] Pulling kits from panel...");
            String resp = get("/api/plugin/kits");
            if (resp == null) { plugin.getLogger().warning("[PK] No response from panel"); return; }
            debug("Response: " + resp.substring(0, Math.min(300, resp.length())));

            // Parse {"kits":[...]}
            int arrStart = resp.indexOf("[");
            int arrEnd   = resp.lastIndexOf("]");
            if (arrStart < 0 || arrEnd < 0) { plugin.getLogger().warning("[PK] No kits array in response"); return; }

            String arr = resp.substring(arrStart + 1, arrEnd).trim();
            if (arr.isEmpty()) { plugin.getLogger().info("[PK] 0 kits on panel"); return; }

            List<Kit> kits = new ArrayList<>();
            int depth = 0, objStart = -1;
            for (int i = 0; i < arr.length(); i++) {
                char c = arr.charAt(i);
                if (c == '{') { if (depth++ == 0) objStart = i; }
                else if (c == '}') {
                    if (--depth == 0 && objStart >= 0) {
                        Kit kit = parseKit("{" + arr.substring(objStart + 1, i) + "}");
                        if (kit != null) kits.add(kit);
                        objStart = -1;
                    }
                }
            }

            // Push to main thread
            final List<Kit> finalKits = kits;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getKitRegistry().setKits(finalKits);
                plugin.getLogger().info("[PK] ✅ Pulled " + finalKits.size() + " kits from panel.");
            });
        });
    }

    // ── Heartbeat ──────────────────────────────────────────────────────────────
    private void startHeartbeat() {
        if (pullTask != null) pullTask.cancel();
        pullTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            post("/api/plugin/heartbeat", "{}");
        }, 600L, 600L); // every 30s
    }

    // ── Report kit given ───────────────────────────────────────────────────────
    public void reportKitGiven(org.bukkit.entity.Player player, String kitId) {
        if (!isEnabled()) return;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String body = String.format("{\"kitId\":\"%s\",\"playerUuid\":\"%s\",\"playerName\":\"%s\"}",
                kitId, player.getUniqueId(), player.getName());
            post("/api/plugin/kit-given", body);
        });
    }

    // ── Parse kit from JSON object string ──────────────────────────────────────
    private Kit parseKit(String json) {
        try {
            String id   = extractString(json, "id");
            String name = extractString(json, "name");
            if (id == null || name == null) return null;

            boolean enabled = !"false".equals(extractRaw(json, "enabled"));
            String icon = extractString(json, "icon");
            int priority = parseInt(extractRaw(json, "priority"), 0);

            Kit kit = new Kit(id, name);
            kit.setEnabled(enabled);
            if (icon != null) kit.setIcon(icon);
            kit.setPriority(priority);

            // Access
            String accessJson = extractObject(json, "access");
            if (accessJson != null) kit.setAccess(parseAccess(accessJson));

            // Conditions
            String condJson = extractObject(json, "conditions");
            if (condJson != null) kit.setConditions(parseConditions(condJson));

            // Actions
            String actJson = extractObject(json, "actions");
            if (actJson != null) kit.setActions(parseActions(actJson));

            // Items
            String itemsJson = extractObject(json, "items");
            if (itemsJson != null) parseItems(kit, itemsJson);

            return kit;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[PK] Error parsing kit: " + e.getMessage(), e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void parseItems(Kit kit, String itemsJson) {
        // format: {"0":{"type":"DIAMOND_SWORD","amount":1,"meta":{...}},...}
        int i = 0;
        while (i < itemsJson.length()) {
            int sq = itemsJson.indexOf('"', i);
            if (sq < 0) break;
            int se = itemsJson.indexOf('"', sq + 1);
            if (se < 0) break;
            String slotStr = itemsJson.substring(sq + 1, se);
            int slot;
            try { slot = Integer.parseInt(slotStr); } catch (NumberFormatException e) { i = se + 1; continue; }

            int ob = itemsJson.indexOf('{', se);
            if (ob < 0) break;
            int oe = findClose(itemsJson, ob, '{', '}');
            if (oe < 0) break;
            String itemJson = itemsJson.substring(ob, oe + 1);

            // Build item data map
            String type   = extractString(itemJson, "type");
            int amount    = parseInt(extractRaw(itemJson, "amount"), 1);
            String metaJs = extractObject(itemJson, "meta");

            if (type != null) {
                Map<String, Object> data = new HashMap<>();
                data.put("type", type);
                data.put("amount", amount);
                if (metaJs != null) data.put("meta", parseMetaMap(metaJs));

                org.bukkit.inventory.ItemStack item = plugin.getCustomItemService().buildItem(data);
                if (item != null) {
                    kit.setItem(slot, item);
                    debug("  Slot " + slot + ": " + type + " x" + amount + (metaJs!=null?" [meta]":""));
                }
            }
            i = oe + 1;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetaMap(String metaJson) {
        Map<String, Object> map = new HashMap<>();
        // displayName
        String dn = extractString(metaJson, "displayName");
        if (dn != null) map.put("displayName", dn);
        // customModelData
        String cmd = extractRaw(metaJson, "customModelData");
        if (cmd != null) try { map.put("customModelData", Integer.parseInt(cmd.trim())); } catch (Exception ignored) {}
        // enchantGlow
        if ("true".equals(extractRaw(metaJson, "enchantGlow"))) map.put("enchantGlow", true);
        // External item plugins
        String mythicId = extractString(metaJson, "mythicId");
        if (mythicId != null && !mythicId.isEmpty()) map.put("mythicId", mythicId);
        String itemsAdderId = extractString(metaJson, "itemsAdderId");
        if (itemsAdderId != null && !itemsAdderId.isEmpty()) map.put("itemsAdderId", itemsAdderId);
        String oraxenId = extractString(metaJson, "oraxenId");
        if (oraxenId != null && !oraxenId.isEmpty()) map.put("oraxenId", oraxenId);
        // lore
        String loreArr = extractArray(metaJson, "lore");
        if (loreArr != null) map.put("lore", parseStringArray(loreArr));
        // enchants
        String enchArr = extractArray(metaJson, "enchants");
        if (enchArr != null) {
            List<Map<String, Object>> enchList = new ArrayList<>();
            int j = 0;
            while (j < enchArr.length()) {
                int ob = enchArr.indexOf('{', j);
                if (ob < 0) break;
                int oe = findClose(enchArr, ob, '{', '}');
                if (oe < 0) break;
                String eJson = enchArr.substring(ob, oe + 1);
                String enchId = extractString(eJson, "id");
                int level = parseInt(extractRaw(eJson, "level"), 1);
                if (enchId != null) {
                    Map<String, Object> e = new HashMap<>();
                    e.put("id", enchId.toLowerCase());
                    e.put("level", level);
                    enchList.add(e);
                }
                j = oe + 1;
            }
            if (!enchList.isEmpty()) map.put("enchants", enchList);
        }
        // potionEffect
        String pe = extractString(metaJson, "potionEffect");
        if (pe != null) {
            map.put("potionEffect", pe);
            String amp = extractRaw(metaJson, "potionAmplifier");
            String dur = extractRaw(metaJson, "potionDuration");
            String ptcl= extractRaw(metaJson, "potionParticles");
            map.put("potionAmplifier", parseInt(amp, 0));
            map.put("potionDuration",  parseInt(dur, 3600));
            map.put("potionParticles", !"false".equals(ptcl));
        }
        return map;
    }

    private Kit.KitAccess parseAccess(String json) {
        Kit.KitAccess a = new Kit.KitAccess();
        String type = extractString(json, "type");
        if (type != null) try { a.type = Kit.AccessType.valueOf(type.toUpperCase()); } catch (Exception ignored) {}
        a.group      = extractString(json, "group");
        a.permission = extractString(json, "permission");
        a.player     = extractString(json, "player");
        // Multi-groups (array)
        String groupsArr = extractArray(json, "groups");
        if (groupsArr != null) a.groups = parseStringArray(groupsArr);
        String gLogic = extractString(json, "groupLogic");
        if (gLogic != null) a.groupLogic = gLogic;
        // Multi-permissions (array)
        String permsArr = extractArray(json, "permissions");
        if (permsArr != null) a.permissions = parseStringArray(permsArr);
        String pLogic = extractString(json, "permLogic");
        if (pLogic != null) a.permLogic = pLogic;
        // Worlds
        String worldsArr = extractArray(json, "worlds");
        if (worldsArr != null) a.worlds = parseStringArray(worldsArr);
        return a;
    }

    private Kit.KitConditions parseConditions(String json) {
        Kit.KitConditions c = new Kit.KitConditions();
        c.cooldownSeconds      = parseLong(extractRaw(json, "cooldownSeconds"), 0);
        c.minLevel             = parseInt(extractRaw(json, "minLevel"), 0);
        c.minMoney             = parseDouble(extractRaw(json, "minMoney"), 0);
        c.cost                 = parseDouble(extractRaw(json, "cost"), 0);
        c.oneTime              = "true".equals(extractRaw(json, "oneTime"));
        c.requiresPreviewAccept= "true".equals(extractRaw(json, "requiresPreviewAccept"));
        c.worldGuardRegion     = extractString(json, "worldGuardRegion");
        c.eventStart           = parseLong(extractRaw(json, "eventStart"), 0);
        c.eventEnd             = parseLong(extractRaw(json, "eventEnd"), 0);

        // Legacy single placeholder check
        String phJson = extractObject(json, "placeholderCheck");
        if (phJson != null) {
            Kit.KitConditions.PlaceholderCheck pc = new Kit.KitConditions.PlaceholderCheck();
            pc.placeholder = extractString(phJson, "placeholder");
            pc.operator    = extractString(phJson, "operator");
            pc.value       = extractString(phJson, "value");
            if (pc.operator == null) pc.operator = ">=";
            if (pc.placeholder != null) c.placeholderCheck = pc;
        }

        // Multi placeholder checks array
        String checksArr = extractArray(json, "placeholderChecks");
        if (checksArr != null) {
            int i = 0;
            while (i < checksArr.length()) {
                int ob = checksArr.indexOf('{', i);
                if (ob < 0) break;
                int oe = findClose(checksArr, ob, '{', '}');
                if (oe < 0) break;
                String cJson = checksArr.substring(ob, oe + 1);
                Kit.KitConditions.PlaceholderCheck pc = new Kit.KitConditions.PlaceholderCheck();
                pc.placeholder = extractString(cJson, "placeholder");
                pc.operator    = extractString(cJson, "operator");
                pc.value       = extractString(cJson, "value");
                pc.logic       = extractString(cJson, "logic");
                if (pc.operator == null) pc.operator = ">=";
                if (pc.logic == null) pc.logic = "AND";
                if (pc.placeholder != null) c.placeholderChecks.add(pc);
                i = oe + 1;
            }
        }
        return c;
    }

    private Kit.KitActions parseActions(String json) {
        Kit.KitActions a = new Kit.KitActions();
        a.onReceiveCommand = extractString(json, "onReceiveCommand");
        a.customMessage    = extractString(json, "customMessage");
        a.broadcast        = extractString(json, "broadcast");
        a.sound            = extractString(json, "sound");
        a.soundVolume      = (float) parseDouble(extractRaw(json, "soundVolume"), 1.0);
        a.soundPitch       = (float) parseDouble(extractRaw(json, "soundPitch"), 1.0);
        a.particle         = extractString(json, "particle");
        a.particleCount    = parseInt(extractRaw(json, "particleCount"), 20);
        a.particleColor    = extractString(json, "particleColor");
        return a;
    }

    // ── HTTP helpers ───────────────────────────────────────────────────────────
    String get(String path) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + path).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-API-Key", apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            int code = conn.getResponseCode();
            debug("GET " + path + " → " + code);
            if (code != 200) return null;
            return readStream(conn.getInputStream());
        } catch (Exception e) {
            plugin.getLogger().warning("[PK] GET " + path + " failed: " + e.getMessage());
            return null;
        }
    }

    void post(String path, String body) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + path).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("X-API-Key", apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            debug("POST " + path + " → " + conn.getResponseCode());
        } catch (Exception e) {
            debug("POST " + path + " failed: " + e.getMessage());
        }
    }

    private String readStream(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line; while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    // ── JSON mini-parser helpers ───────────────────────────────────────────────
    String extractString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + pattern.length());
        if (colon < 0) return null;
        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) return null;
        StringBuilder sb = new StringBuilder();
        int i = q1 + 1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) { sb.append(json.charAt(i+1)); i += 2; }
            else if (c == '"') break;
            else { sb.append(c); i++; }
        }
        return sb.toString();
    }

    String extractRaw(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + pattern.length());
        if (colon < 0) return null;
        int start = colon + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length()) return null;
        char fc = json.charAt(start);
        if (fc == '"') return extractString(json, key);
        int end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != ']') end++;
        return json.substring(start, end).trim();
    }

    String extractObject(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + pattern.length());
        if (colon < 0) return null;
        int ob = json.indexOf('{', colon);
        if (ob < 0) return null;
        int oe = findClose(json, ob, '{', '}');
        if (oe < 0) return null;
        return json.substring(ob, oe + 1);
    }

    String extractArray(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + pattern.length());
        if (colon < 0) return null;
        int ab = json.indexOf('[', colon);
        if (ab < 0) return null;
        int ae = findClose(json, ab, '[', ']');
        if (ae < 0) return null;
        return json.substring(ab + 1, ae);
    }

    List<String> parseStringArray(String arr) {
        List<String> list = new ArrayList<>();
        int i = 0;
        while (i < arr.length()) {
            int q1 = arr.indexOf('"', i);
            if (q1 < 0) break;
            int q2 = arr.indexOf('"', q1 + 1);
            if (q2 < 0) break;
            list.add(arr.substring(q1 + 1, q2));
            i = q2 + 1;
        }
        return list;
    }

    int findClose(String s, int open, char openChar, char closeChar) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            if (s.charAt(i) == openChar) depth++;
            else if (s.charAt(i) == closeChar) { if (--depth == 0) return i; }
        }
        return -1;
    }

    int    parseInt(String s, int def)    { if (s==null) return def; try { return (int)Double.parseDouble(s.trim()); } catch (Exception e) { return def; } }
    long   parseLong(String s, long def)  { if (s==null) return def; try { return (long)Double.parseDouble(s.trim()); } catch (Exception e) { return def; } }
    double parseDouble(String s, double d){ if (s==null) return d;   try { return Double.parseDouble(s.trim()); } catch (Exception e) { return d; } }

    private void debug(String msg) {
        if (plugin.getConfig().getBoolean("debug", false)) plugin.getLogger().info("[PK-DEBUG] " + msg);
    }

    public void onDisable() {
        if (pullTask != null) pullTask.cancel();
        post("/api/plugin/disconnect", "{}");
    }
}
