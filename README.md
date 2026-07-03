# PremiumKits — Minecraft Plugin

**PremiumKits** is a powerful and flexible kit management plugin for Paper 1.21+.  
Kits can be created and managed either through a **web panel** (Render + Vercel + Neon) or directly via **local YAML files** without any external service.

---

## Features

- **Flexible access rules** — give kits to everyone, a specific LuckPerms group (OR / AND logic), a specific permission node, a specific player (UUID), or only in certain worlds
- **Multi-group & multi-permission** — require multiple groups or permissions with configurable AND / OR logic
- **PlaceholderAPI conditions** — multiple PAPI checks with AND / OR chaining (`%vault_balance% >= 1000`, etc.)
- **Full cooldown system** — per-kit cooldown, one-time kits, bypass permissions
- **Actions on receive** — console command, custom message, server-wide broadcast, sound, particle effect
- **Economy integration** — minimum balance requirement, cost deducted on receive (Vault)
- **External item plugins** — MythicMobs, ItemsAdder, Oraxen items supported inside kits
- **Enchantments & potion effects** — full enchant support (all levels), splash/lingering potions with custom effects
- **Custom Model Data** — resource pack custom items via CMD
- **Kill streak & revenge kits** — bonus kit after X kills, consolation kit after X deaths
- **Auto-join kit** — automatically given when a player joins the server
- **Auto-region kit** — automatically given when a player enters a WorldGuard region
- **Random kit** — `/kit random` picks a weighted random kit from the player's accessible kits
- **Queue system** — if inventory is full, the kit is queued and delivered automatically when space frees up
- **Anti-spam protection** — prevents double-click exploit (1.5s lockout per kit)
- **Event kits** — active only between two configured dates
- **Offline mode** — no panel required, configure kits entirely via local YAML files with auto-reload
- **Web panel sync** — real-time push from the panel via WebSocket, no server restart needed

---

## Requirements

| Dependency | Required | Used for |
|---|---|---|
| Paper 1.21+ | ✅ Yes | Server software |
| Vault | Recommended | Economy (cost, min-money) |
| LuckPerms | Recommended | Group-based access |
| PlaceholderAPI | Recommended | Placeholder condition checks |
| WorldGuard | Optional | Region-based restrictions |
| MythicMobs | Optional | Custom MythicMobs items in kits |
| ItemsAdder | Optional | Custom ItemsAdder items in kits |
| Oraxen | Optional | Custom Oraxen items in kits |

---

## Installation

1. Drop `PremiumKits-2.0.0.jar` into your server's `plugins/` folder
2. Start the server once — `plugins/PremiumKits/` will be created with `config.yml` and `kits/example_kit.yml`
3. Choose a mode:

### Mode A — Web panel
Edit `config.yml`:
```yaml
premiumkits:
  enabled: true
  api-key: "your_api_key"
  url: "https://your-backend.onrender.com"
```
Create kits from the panel, click **Save & Push** — the plugin receives them instantly.

### Mode B — Local YAML (no panel)
Keep `config.yml` with `enabled: false`.  
Copy `kits/example_kit.yml`, rename it (e.g. `kits/kit_vip.yml`), and edit it.  
The plugin watches the `kits/` folder and reloads automatically when a file changes.

---

## Commands

| Command | Permission | Description |
|---|---|---|
| `/kit` | `premiumkits.use` | Open the kit selection GUI |
| `/kit <id>` | `premiumkits.admin` | Give a specific kit to yourself |
| `/kit <id> <player>` | `premiumkits.admin` | Give a specific kit to another player |
| `/kit random` | `premiumkits.use` | Receive a random weighted kit |
| `/kit mystery` | `premiumkits.use` | Receive a mystery kit (contents hidden until received) |
| `/kit reload` | `premiumkits.admin` | Reload all kits from panel or YAML files |

**Aliases:** `/kits`, `/premiumkit`, `/pk`

---

## Permissions

| Permission | Default | Description |
|---|---|---|
| `premiumkits.use` | Everyone | Open the `/kit` menu |
| `premiumkits.admin` | OP | Admin commands (give, reload) |
| `premiumkits.bypass.cooldown` | OP | Bypass all kit cooldowns |
| `premiumkits.bypass.conditions` | OP | Bypass all kit conditions (level, money, PAPI checks) |

---

## Kit Slot Reference

```
Slot 0–8   → Hotbar
Slot 9–35  → Main inventory (rows 1–3)
Slot 36    → Boots
Slot 37    → Leggings
Slot 38    → Chestplate
Slot 39    → Helmet
Slot 40    → Off-hand
```

---

## Variables in commands & messages

These placeholders are replaced at runtime when a kit is given:

| Variable | Value |
|---|---|
| `%player%` or `{player}` | Player's username |
| `%uuid%` | Player's UUID |
| `%kit_id%` | Kit internal ID |
| `{kit}` | Kit display name |
| `%kit_count%` | Total kits received by this player this session |
| `%player_balance%` | Player's Vault balance |
| `%player_streak%` | Player's current kill streak |

---

## Source File Reference

### Root

| File | Role |
|---|---|
| `PremiumKits.java` | Main plugin class. Initialises all services, registers listeners and commands, hooks into Vault/LuckPerms/PAPI/WorldGuard. Chooses between panel mode and offline mode on startup. |

---

### `commands/`

| File | Role |
|---|---|
| `KitCommand.java` | Handles `/kit`. Opens the player GUI with no args, gives a specific kit by ID as admin, supports `/kit random`, `/kit mystery`, and `/kit reload`. |

---

### `gui/`

| File | Role |
|---|---|
| `PlayerKitMenuGUI.java` | The in-game kit selection inventory GUI (`/kit`). Shows all kits the player can access, sorted by priority. Displays cooldown progress bar, live status (available / on cooldown / conditions not met), and handles left-click (receive) and right-click (preview). |

---

### `hooks/`

| File | Role |
|---|---|
| `PanelHook.java` | Connects to the web backend via HTTP and WebSocket. Pulls all kits on startup, listens for real-time `KIT_UPDATE` / `KIT_DELETE` messages from the panel, reports kit gives back, and sends heartbeats. Uses a hand-written JSON mini-parser (no external library) to avoid shading Gson. |
| `WorldGuardHook.java` | WorldGuard integration using **full reflection** — never references WorldGuard/WorldEdit classes at the bytecode level. This means the plugin loads correctly even if WorldGuard is not installed. Checks if a player is inside a named region. |

---

### `listeners/`

| File | Role |
|---|---|
| `KitListener.java` | Handles `InventoryClickEvent` (routes clicks to `PlayerKitMenuGUI`), `InventoryCloseEvent` (cleanup), and `PlayerQuitEvent` (cleanup queued state and kill streak data). |
| `KillStreakListener.java` | Tracks kill streaks and death counts per player. Fires the configured kit after X consecutive kills (kill streak) and after X consecutive deaths (revenge kit). Resets on death/quit. |
| `AutoKitListener.java` | Listens for `PlayerJoinEvent` to give auto-join kits (with a 2-second delay for LuckPerms to load), and `PlayerMoveEvent` to detect WorldGuard region entry and give auto-region kits. Tracks which regions a player is currently in to avoid spam. |

---

### `managers/`

| File | Role |
|---|---|
| `KitRegistry.java` | In-memory store for all loaded kits. Provides `getAccessibleKits(player)` (filters by enabled, event window, access rules), `resolveBest(player)` (highest-priority kit), and `canAccess(player, kit)` (evaluates EVERYONE / GROUP / PERMISSION / PLAYER / WORLD with configurable AND/OR logic). Uses LuckPerms API for group checks with a Vault permission fallback. |

---

### `model/`

| File | Role |
|---|---|
| `Kit.java` | Data model representing a single kit. Contains: `KitAccess` (type, groups with groupLogic, permissions with permLogic, player UUID, worlds), `KitConditions` (cooldown, min level, min money, cost, one-time, WorldGuard region, event window dates, multi-PlaceholderAPI checks with AND/OR logic), `KitActions` (command, message, broadcast, sound, particle), and a `tags` map (auto-join, auto-region, weight, no-random, mystery). |

---

### `service/`

| File | Role |
|---|---|
| `GiveService.java` | Core kit delivery logic. Checks all conditions in order (access, event window, world restriction, one-time, cooldown, min level, economy, WorldGuard, PAPI checks), deducts cost, gives all items to the correct inventory slots (0–40 including armor and off-hand), executes actions, reports to the panel, starts cooldown tracking. Also contains the **anti-spam** protection (1.5s lock per player+kit). |
| `CustomItemService.java` | Builds `ItemStack` objects from a data map. Handles vanilla items with enchants, display names, lore, custom model data, and potion effects. Also delegates to **MythicMobs**, **ItemsAdder**, and **Oraxen** via reflection if those plugins are present. |
| `OfflineKitLoader.java` | Loads kits from local `.yml` files in `plugins/PremiumKits/kits/` when panel mode is disabled. Supports all kit features including access rules, conditions, actions, tags, items with enchants/potions/plugin IDs. Watches the folder for file changes and auto-reloads if `offline-mode.auto-reload: true`. |
| `RandomKitService.java` | Implements `/kit random` and `/kit mystery`. Builds a weighted pool of accessible kits (weight from the `tags.weight` field or priority), rolls a random number, and gives the selected kit. Mystery adds a 3-second dramatic delay before delivery. |
| `QueueService.java` | Queues a kit if the player's inventory is full. Listens for `PlayerDropItemEvent` and checks if there is now enough space (3+ free slots) to deliver any queued kit automatically. Notifies the player with a sound and message on delivery. |

---

### `resources/`

| File | Role |
|---|---|
| `plugin.yml` | Bukkit plugin descriptor. Declares main class, version, commands (`/kit`), aliases, permissions, and soft-dependencies (Vault, LuckPerms, PlaceholderAPI, WorldGuard, MythicMobs, ItemsAdder, Oraxen). |
| `config.yml` | Main configuration file. Contains panel connection settings, offline mode settings, kill streak config, revenge kit config. Fully commented in English. |
| `kits/example_kit.yml` | A fully commented example kit file. Covers every available field: access, conditions (including PAPI checks and event dates), actions (command, message, broadcast, sound, particle), tags, items (vanilla enchants, potion effects, custom model data, MythicMobs/ItemsAdder/Oraxen IDs). Copy and rename to create new kits in offline mode. |

---

## Architecture overview

```
Player types /kit
      │
      ▼
KitCommand.java
      │
      ├─ /kit random / mystery ──► RandomKitService.java
      │
      └─ no args ──► PlayerKitMenuGUI.java
                           │
                    Player clicks a kit
                           │
                           ▼
                    GiveService.java
                    ┌──────────────────────────────┐
                    │ 1. Check access (KitRegistry) │
                    │ 2. Check event window          │
                    │ 3. Check world restriction     │
                    │ 4. Check one-time              │
                    │ 5. Check cooldown              │
                    │ 6. Check min level             │
                    │ 7. Check economy (Vault)       │
                    │ 8. Check WorldGuard region     │
                    │ 9. Check PAPI conditions       │
                    │ 10. Deduct cost                │
                    │ 11. Give items (slots 0–40)    │
                    │ 12. Execute actions            │
                    │ 13. Report to panel            │
                    └──────────────────────────────┘
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
     CustomItemService  PanelHook   QueueService
  (build ItemStacks)  (HTTP report)  (if full inv)
```

---

## Building

```bash
cd plugin
mvn package -DskipTests
# Output: target/PremiumKits-2.0.0.jar
```

Java 21 required.
