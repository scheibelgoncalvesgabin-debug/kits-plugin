package fr.gab9sg.premiumkits.hooks;

import fr.gab9sg.premiumkits.PremiumKits;
import org.bukkit.entity.Player;

/**
 * WorldGuard hook using full reflection — never references WorldGuard/WorldEdit
 * classes directly at the bytecode level. This prevents NoClassDefFoundError
 * if WorldGuard is not installed, since the JVM never needs to resolve those
 * classes unless isInRegion() is actually called (and only on a hooked server).
 */
public class WorldGuardHook {

    private final PremiumKits plugin;
    private boolean hooked = false;

    public WorldGuardHook(PremiumKits plugin) {
        this.plugin = plugin;
        try {
            if (plugin.getServer().getPluginManager().getPlugin("WorldGuard") != null) {
                Class.forName("com.sk89q.worldguard.WorldGuard");
                hooked = true;
                plugin.getLogger().info("[PK] WorldGuard hooked.");
            }
        } catch (Throwable t) {
            hooked = false;
            plugin.getLogger().info("[PK] WorldGuard not found — region restrictions disabled.");
        }
    }

    public boolean isHooked() { return hooked; }

    public boolean isInRegion(Player player, String region) {
        if (!hooked || region == null || region.isEmpty()) return true;
        try {
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object wgInstance = wgClass.getMethod("getInstance").invoke(null);
            Object platform = wgClass.getMethod("getPlatform").invoke(wgInstance);
            Object regionContainer = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Object query = regionContainer.getClass().getMethod("createQuery").invoke(regionContainer);

            Class<?> bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object weLocation = bukkitAdapterClass
                .getMethod("adapt", org.bukkit.Location.class)
                .invoke(null, player.getLocation());

            Object regionSet = query.getClass()
                .getMethod("getApplicableRegions", Class.forName("com.sk89q.worldedit.util.Location"))
                .invoke(query, weLocation);

            Object regionsCollection = regionSet.getClass().getMethod("getRegions").invoke(regionSet);
            for (Object r : (Iterable<?>) regionsCollection) {
                String id = (String) r.getClass().getMethod("getId").invoke(r);
                if (id.equalsIgnoreCase(region)) return true;
            }
            return false;
        } catch (Throwable t) {
            plugin.getLogger().warning("[PK] WorldGuard check failed: " + t.getMessage());
            return true;
        }
    }
}
