package fr.gab9sg.premiumkits.managers;

import fr.gab9sg.premiumkits.PremiumKits;
import fr.gab9sg.premiumkits.model.Kit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class KitRegistry {

    private final PremiumKits plugin;
    private final Map<String, Kit> kits = new ConcurrentHashMap<>();

    public KitRegistry(PremiumKits plugin) { this.plugin = plugin; }

    public void setKits(List<Kit> kitList) {
        kits.clear();
        kitList.forEach(k -> kits.put(k.getId(), k));
    }

    public Kit getKit(String id) { return kits.get(id); }
    public Collection<Kit> getAllKits() { return Collections.unmodifiableCollection(kits.values()); }
    public int size() { return kits.size(); }

    /**
     * Get all kits accessible to a player, sorted by priority desc.
     * Access rules: EVERYONE, GROUP, PERMISSION, PLAYER, WORLD
     */
    public List<Kit> getAccessibleKits(Player player) {
        return kits.values().stream()
            .filter(Kit::isEnabled)
            .filter(k -> k.getConditions().isEventActive())
            .filter(k -> canAccess(player, k))
            .sorted(Comparator.comparingInt(Kit::getPriority).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Resolve single best kit for player (highest priority).
     */
    public Kit resolveBest(Player player) {
        return kits.values().stream()
            .filter(Kit::isEnabled)
            .filter(k -> canAccess(player, k))
            .max(Comparator.comparingInt(Kit::getPriority))
            .orElse(null);
    }

    /**
     * Check if a player can access a kit based on access rules.
     */
    public boolean canAccess(Player player, Kit kit) {
        Kit.KitAccess access = kit.getAccess();
        if (access == null) return true;
        return switch (access.type) {
            case EVERYONE -> true;
            case GROUP -> {
                List<String> groups = new ArrayList<>(access.groups);
                if (access.group != null && !access.group.isEmpty()) groups.add(0, access.group);
                if (groups.isEmpty()) yield true;
                yield "AND".equalsIgnoreCase(access.groupLogic)
                    ? groups.stream().allMatch(g -> isInGroup(player, g))
                    : groups.stream().anyMatch(g -> isInGroup(player, g));
            }
            case PERMISSION -> {
                List<String> perms = new ArrayList<>(access.permissions);
                if (access.permission != null && !access.permission.isEmpty()) perms.add(0, access.permission);
                if (perms.isEmpty()) yield true;
                yield "OR".equalsIgnoreCase(access.permLogic)
                    ? perms.stream().anyMatch(player::hasPermission)
                    : perms.stream().allMatch(player::hasPermission);
            }
            case PLAYER -> access.player != null && access.player.equals(player.getUniqueId().toString());
            case WORLD  -> access.worlds.isEmpty() || access.worlds.stream()
                              .anyMatch(w -> w.equalsIgnoreCase(player.getWorld().getName()));
        };
    }

    private boolean isInGroup(Player player, String group) {
        try {
            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = lp.getUserManager().getUser(player.getUniqueId());
            if (user == null) return false;
            net.luckperms.api.query.QueryOptions opts = net.luckperms.api.query.QueryOptions.nonContextual();
            return user.getInheritedGroups(opts).stream()
                .anyMatch(g -> g.getName().equalsIgnoreCase(group));
        } catch (Exception e) {
            return player.hasPermission("group." + group.toLowerCase());
        }
    }
}
