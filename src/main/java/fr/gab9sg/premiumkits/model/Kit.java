package fr.gab9sg.premiumkits.model;

import org.bukkit.inventory.ItemStack;
import java.util.*;

public class Kit {

    public enum AccessType { EVERYONE, GROUP, PERMISSION, PLAYER, WORLD }

    private String id;
    private String name;
    private boolean enabled = true;
    private String icon = "CHEST";
    private Map<Integer, ItemStack> items = new HashMap<>();
    private KitAccess access = new KitAccess();
    private KitConditions conditions = new KitConditions();
    private KitActions actions = new KitActions();
    private int priority = 0;
    // Flexible tags: auto-join, weight, no-random, auto-region, mystery
    private Map<String, Object> tags = new HashMap<>();

    public Kit() {}
    public Kit(String id, String name) { this.id = id; this.name = name; }

    // Getters/setters
    public String getId()                         { return id; }
    public void   setId(String v)                 { this.id = v; }
    public String getName()                       { return name; }
    public void   setName(String v)               { this.name = v; }
    public boolean isEnabled()                    { return enabled; }
    public void   setEnabled(boolean v)           { this.enabled = v; }
    public String getIcon()                       { return icon; }
    public void   setIcon(String v)               { this.icon = v; }
    public Map<Integer, ItemStack> getItems()     { return items; }
    public void   setItems(Map<Integer, ItemStack> v) { this.items = v; }
    public void   setItem(int slot, ItemStack i)  { if (i == null) items.remove(slot); else items.put(slot, i); }
    public KitAccess getAccess()                  { return access; }
    public void   setAccess(KitAccess v)          { this.access = v; }
    public KitConditions getConditions()          { return conditions; }
    public void   setConditions(KitConditions v)  { this.conditions = v; }
    public KitActions getActions()                { return actions; }
    public void   setActions(KitActions v)        { this.actions = v; }
    public int    getPriority()                   { return priority; }
    public void   setPriority(int v)              { this.priority = v; }
    public Map<String, Object> getTags()          { return tags != null ? tags : new HashMap<>(); }
    public void   setTags(Map<String, Object> v)  { this.tags = v != null ? v : new HashMap<>(); }
    public boolean isEmpty()                      { return items.isEmpty(); }

    // ── Access helper ──────────────────────────────────────────────────────────
    public static class KitAccess {
        public AccessType type = AccessType.EVERYONE;
        // Single (legacy compat)
        public String group;
        public String permission;
        public String player;
        public List<String> worlds = new ArrayList<>();
        // Multi
        public List<String> groups = new ArrayList<>();      // logic via groupLogic
        public List<String> permissions = new ArrayList<>(); // logic via permLogic
        public String groupLogic = "OR";   // OR = any match, AND = all required
        public String permLogic  = "AND";  // AND = all required, OR = any match
    }

    // ── Conditions ─────────────────────────────────────────────────────────────
    public static class KitConditions {
        public long cooldownSeconds = 0;
        public int minLevel = 0;
        public double minMoney = 0;
        public double cost = 0;
        public boolean oneTime = false;
        public boolean requiresPreviewAccept = false;
        public String worldGuardRegion;
        // Event kit — active only between these dates (epoch millis, 0 = no restriction)
        public long eventStart = 0;
        public long eventEnd = 0;
        // Legacy single check
        public PlaceholderCheck placeholderCheck;
        // Multi checks (new)
        public List<PlaceholderCheck> placeholderChecks = new ArrayList<>();

        public static class PlaceholderCheck {
            public String placeholder;
            public String operator = ">=";
            public String value;
            public String logic = "AND"; // AND | OR (for chaining multiple checks)
        }

        /** Returns true if player passes ALL placeholder conditions */
        public boolean checkPlaceholders(org.bukkit.entity.Player player) {
            // Combine legacy + new
            List<PlaceholderCheck> checks = new ArrayList<>(placeholderChecks);
            if (placeholderCheck != null && placeholderCheck.placeholder != null) checks.add(0, placeholderCheck);
            if (checks.isEmpty()) return true;

            boolean result = evalCheck(player, checks.get(0));
            for (int i = 1; i < checks.size(); i++) {
                PlaceholderCheck pc = checks.get(i);
                boolean val = evalCheck(player, pc);
                if ("OR".equalsIgnoreCase(pc.logic)) result = result || val;
                else result = result && val;
            }
            return result;
        }

        private boolean evalCheck(org.bukkit.entity.Player player, PlaceholderCheck pc) {
            if (pc == null || pc.placeholder == null) return true;
            String resolved;
            try {
                resolved = (String) Class.forName("me.clip.placeholderapi.PlaceholderAPI")
                    .getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class)
                    .invoke(null, player, pc.placeholder);
            } catch (Exception e) { return true; }
            try {
                double actual   = Double.parseDouble(resolved.trim());
                double expected = Double.parseDouble(pc.value.trim());
                return switch (pc.operator) {
                    case ">=" -> actual >= expected;
                    case "<=" -> actual <= expected;
                    case ">"  -> actual > expected;
                    case "<"  -> actual < expected;
                    case "==" -> actual == expected;
                    case "!=" -> actual != expected;
                    default   -> true;
                };
            } catch (NumberFormatException e) {
                return switch (pc.operator) {
                    case "==" -> resolved.equals(pc.value);
                    case "!=" -> !resolved.equals(pc.value);
                    default   -> true;
                };
            }
        }

        // Legacy compat
        public boolean checkPlaceholder(org.bukkit.entity.Player player) {
            return checkPlaceholders(player);
        }

        /** True if no event restriction, or current time is within event window */
        public boolean isEventActive() {
            if (eventStart == 0 && eventEnd == 0) return true;
            long now = System.currentTimeMillis();
            if (eventStart > 0 && now < eventStart) return false;
            if (eventEnd > 0 && now > eventEnd) return false;
            return true;
        }
    }

    // ── Actions ────────────────────────────────────────────────────────────────
    public static class KitActions {
        public String onReceiveCommand;
        public String customMessage;
        public String broadcast;
        public String sound;
        public float soundVolume = 1.0f;
        public float soundPitch  = 1.0f;
        public String particle;
        public int particleCount = 20;
        public String particleColor;
    }
}
