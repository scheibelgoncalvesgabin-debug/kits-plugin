package fr.gab9sg.premiumkits;

import fr.gab9sg.premiumkits.commands.KitCommand;
import fr.gab9sg.premiumkits.gui.PlayerKitMenuGUI;
import fr.gab9sg.premiumkits.hooks.PanelHook;
import fr.gab9sg.premiumkits.hooks.WorldGuardHook;
import fr.gab9sg.premiumkits.listeners.KitListener;
import fr.gab9sg.premiumkits.listeners.KillStreakListener;
import fr.gab9sg.premiumkits.managers.KitRegistry;
import fr.gab9sg.premiumkits.service.CustomItemService;
import fr.gab9sg.premiumkits.service.GiveService;
import fr.gab9sg.premiumkits.service.RandomKitService;
import fr.gab9sg.premiumkits.service.QueueService;
import fr.gab9sg.premiumkits.service.OfflineKitLoader;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class PremiumKits extends JavaPlugin {

    private static PremiumKits instance;

    private KitRegistry      kitRegistry;
    private GiveService      giveService;
    private CustomItemService customItemService;
    private PanelHook        panelHook;
    private WorldGuardHook   worldGuardHook;
    private PlayerKitMenuGUI playerKitMenuGUI;
    private KillStreakListener killStreakListener;
    private RandomKitService randomKitService;
    private QueueService     queueService;
    private OfflineKitLoader offlineKitLoader;
    private Economy          economy;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Economy (Vault)
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) { economy = rsp.getProvider(); getLogger().info("[PK] Vault economy hooked."); }
        }

        // WorldGuard
        worldGuardHook = new WorldGuardHook(this);

        // Core
        kitRegistry      = new KitRegistry(this);
        customItemService = new CustomItemService(this);
        giveService      = new GiveService(this);
        playerKitMenuGUI = new PlayerKitMenuGUI(this);
        randomKitService  = new RandomKitService(this);
        queueService      = new QueueService(this);
        offlineKitLoader  = new OfflineKitLoader(this);

        // Panel connection OR offline mode
        if (getConfig().getBoolean("premiumkits.enabled", false)) {
            panelHook = new PanelHook(this);
        } else {
            getLogger().info("[PK] Panel disabled — loading kits from local YAML files.");
            offlineKitLoader.loadAll();
        }

        // Kill streak + revenge
        killStreakListener = new KillStreakListener(this);

        // Register listeners
        getServer().getPluginManager().registerEvents(new KitListener(this), this);
        getServer().getPluginManager().registerEvents(killStreakListener, this);
        getServer().getPluginManager().registerEvents(new fr.gab9sg.premiumkits.listeners.AutoKitListener(this), this);
        getServer().getPluginManager().registerEvents(queueService, this);

        // Commands
        getCommand("kit").setExecutor(new KitCommand(this));

        getLogger().info("[PK] PremiumKits v" + getDescription().getVersion() + " enabled!");
        getLogger().info("[PK] " + kitRegistry.size() + " kits loaded.");
    }

    @Override
    public void onDisable() {
        if (panelHook != null) panelHook.onDisable();
        if (offlineKitLoader != null) offlineKitLoader.stop();
        getLogger().info("[PK] PremiumKits disabled.");
    }

    public void reload() {
        reloadConfig();
        if (panelHook != null) panelHook.reload();
        else if (offlineKitLoader != null) offlineKitLoader.loadAll();
    }

    // Getters
    public static PremiumKits getInstance() { return instance; }
    public KitRegistry       getKitRegistry()       { return kitRegistry; }
    public GiveService       getGiveService()        { return giveService; }
    public CustomItemService getCustomItemService()  { return customItemService; }
    public PanelHook         getPanelHook()          { return panelHook; }
    public WorldGuardHook    getWorldGuardHook()     { return worldGuardHook; }
    public PlayerKitMenuGUI  getPlayerKitMenuGUI()   { return playerKitMenuGUI; }
    public KillStreakListener getKillStreakListener(){ return killStreakListener; }
    public RandomKitService  getRandomKitService()   { return randomKitService; }
    public QueueService      getQueueService()        { return queueService; }
    public OfflineKitLoader  getOfflineKitLoader()    { return offlineKitLoader; }
    public Economy           getEconomy()             { return economy; }
}
