package fr.gab9sg.premiumkits.managers;

import fr.gab9sg.premiumkits.PremiumKits;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class LangManager {

    private final PremiumKits plugin;
    private YamlConfiguration lang;
    private String prefix;

    public LangManager(PremiumKits plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        String locale = plugin.getConfig().getString("language", "en").toLowerCase();

        // Try external file first (plugins/PremiumKits/lang/xx.yml)
        File external = new File(plugin.getDataFolder(), "lang/" + locale + ".yml");
        if (!external.exists()) {
            plugin.saveResource("lang/" + locale + ".yml", false);
        }

        if (external.exists()) {
            lang = YamlConfiguration.loadConfiguration(external);
            // Merge defaults from jar so new keys are always available
            InputStream defStream = plugin.getResource("lang/" + locale + ".yml");
            if (defStream == null) defStream = plugin.getResource("lang/en.yml");
            if (defStream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defStream, StandardCharsets.UTF_8));
                lang.setDefaults(defaults);
            }
        } else {
            // Fallback: load en.yml directly from jar
            InputStream fallback = plugin.getResource("lang/en.yml");
            if (fallback != null) {
                lang = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(fallback, StandardCharsets.UTF_8));
            } else {
                lang = new YamlConfiguration();
            }
        }

        prefix = colorize(lang.getString("prefix", "&8[&6PK&8] "));
        plugin.getLogger().info("[PK] Language loaded: " + locale);
    }

    /**
     * Get a message by key, applying color codes and variable replacements.
     * Replacement pairs: "key", "value", "key2", "value2", ...
     */
    public String get(String key, String... replacements) {
        String msg = lang.getString(key, "&c[PK] Missing message: " + key);
        msg = colorize(prefix + msg);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return msg;
    }

    /** Get a message without the prefix */
    public String getRaw(String key, String... replacements) {
        String msg = lang.getString(key, "&c[PK] Missing message: " + key);
        msg = colorize(msg);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return msg;
    }

    /** Send a translated message to a player */
    public void send(Player player, String key, String... replacements) {
        player.sendMessage(get(key, replacements));
    }

    /** Send a translated message without prefix */
    public void sendRaw(Player player, String key, String... replacements) {
        player.sendMessage(getRaw(key, replacements));
    }

    public String getPrefix() { return prefix; }

    private String colorize(String s) {
        return s == null ? "" : s.replace("&", "\u00a7");
    }

    /** Format seconds into Xh Xm Xs */
    public static String formatCooldown(long seconds) {
        long h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }
}
