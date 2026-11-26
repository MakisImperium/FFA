<p align="center">
  <img src="docs/assets/zernyth-ffa.png" alt="Zernyth FFA with the silver and violet Zernyth emblem beside a torchlit PvP arena" width="100%">
</p>

<div align="center">

# Zernyth FFA

**Multi-arena free-for-all for Minecraft: Bedrock Edition**

Run PvP arenas on PowerNukkitX with configurable kits, protected spawn areas, levels, seasons, a cosmetics shop, duels and team arenas, all from in-game menus for players and admins.

**PowerNukkitX 3.0.6 · Java 21+ · English / German**

[Quick start](#quick-start) · [Arena setup](#arena-setup) · [Gameplay](#gameplay) · [Commands](#commands) · [Configuration](#configuration) · [Development](#development)

</div>

---

## Quick start

Build with a JDK 21 or newer.

| Build tool | Command | Plugin JAR |
| --- | --- | --- |
| Gradle Wrapper (Windows PowerShell) | `.\gradlew.bat build` | `build/libs/ZernythFFA.jar` |
| Gradle Wrapper (Linux/macOS) | `sh ./gradlew build` | `build/libs/ZernythFFA.jar` |

The Gradle Wrapper downloads the pinned Gradle version on first use.

1. Stop the PowerNukkitX 3.0.6 server and copy `ZernythFFA.jar` into its `plugins/` directory. When upgrading, replace the previous plugin JAR. Start the server again.
2. In game, open `/ffaadmin` as an operator or with the `ffa.admin` permission. Follow the arena setup below, then join through `/ffa`.

On first launch, the plugin creates `plugins/FFAGameV2/` and a **Soldier** kit if no kits exist. Player profiles use JSON until MySQL is configured.

## Arena setup

1. In **Arena Management → Create Arena**, enter an arena ID, a display name, and the name of an existing server world.
2. Stand at a spawn point in that world and use **Add Spawn** to record your position and facing direction. Repeat for each spawn you want to add.
3. To protect an area, open **Arena Management → Zone Tools** and start safe-zone placement. Select the arena and click two opposite corner blocks in its world. The cuboid saves automatically.
4. Open **Set Arena Status** and choose **Live**. An arena needs a loadable world and at least one spawn before players can join.

Use **Kit Management → Create / Update Kit → Snapshot from inventory** to save your current items and armor. The admin menu also lets you edit individual kit slots and armor pieces. `/ffaadmin kitlevel <kit> <level>` locks a kit until players reach that level.

To make an arena a team arena, run `/ffaadmin mode <arena> team 2` (team size 2 to 4); `/ffaadmin mode <arena> ffa` switches back.

## Gameplay

| Feature | How it works |
| --- | --- |
| Combat tag | A hit marks both players for 10 seconds. Logging out or leaving the arena in that time counts as a death, and the last attacker gets the kill. |
| Kill farming | More than 3 kills on the same victim within 10 minutes do not count for stats, XP, coins or tasks. |
| Streak rewards | Effects, items, healing or repair at set streaks, per arena or server-wide. Streaks from 5 are announced, breaking one is a **Shutdown**. |
| Levels and ranks | XP per kill, with bonuses for streaks and shutdowns. Ranks show in the scoreboard and the chat prefix. Kits can require a level. |
| Seasons | Monthly or weekly season stats next to the lifetime stats. The top 10 of each finished season are archived and shown in the stats menu. |
| Tasks and achievements | 3 daily and 3 weekly tasks from a configurable pool, plus built-in and custom achievements. Both pay XP and coins. |
| Coins and shop | Coins from kills, streaks, shutdowns, tasks and achievements buy kill effects (particles), death messages and join messages in the **Shop**. |
| Duels | `/ffa duel <player>` or **Duel** in the arena menu. Both fight inside the arena, and only against each other. Wins and losses go into the profile. |
| Team arenas | Teams form on joining, evenly or with your group (`/ffa team invite`). No friendly fire, team colours on name tags, team kills on the scoreboard. |
| Spectating | `/ffa spectate <arena>` in spectator mode. Run it again to return to the lobby with your game mode restored. |
| Events | Double XP and coins (or any factor) started by an admin or on a schedule, shown on the scoreboard. |
| Leaderboard | A floating top list at a place an admin sets, rotating through kills, K/D, level and season kills. |
| Discord | Optional webhook posts for long streaks, season ends with the top three, and achievements. |

Profiles load in the background when a player joins. Leaderboards come from a cache that refreshes every minute, so menus and the scoreboard never wait for the database.

## Commands

| Command | Does | Access |
| --- | --- | --- |
| `/ffa` | Arena, kit, stats, shop and settings menus. Inside an arena: leave, kits, settings and duels. | In-game players |
| `/ffa duel <player>` · `accept` · `decline` | Challenge a player in your arena, or answer a challenge (valid 30 seconds). | Players |
| `/ffa spectate <arena>` | Watch an arena; again to stop. | Players |
| `/ffa team invite <player>` · `accept` · `leave` · `list` | Groups that play together in team arenas. | Players |
| `/ffaadmin` | Arena and kit management forms. | Operators by default, or players with `ffa.admin` |
| `/ffaadmin reload` | Reloads config, arenas, kits and languages without a restart. A broken file keeps the running state. | Admins |
| `/ffaadmin arena <id> spawns` · `tp <n>` · `delspawn <n>` · `zones` · `showzone <zone>` · `delzone <zone>` · `copy <newId> [name]` | Arena tools. `showzone` draws the zone's edges for 10 seconds. | Admins |
| `/ffaadmin mode <arena> <ffa\|team> [size]` | Free for all or teams of 2 to 4. | Admins |
| `/ffaadmin kick <player>` · `announce <arena\|all> <message>` | Remove a player from their arena, or message one arena or all of them (`&` colour codes). | Admins |
| `/ffaadmin event start <xp> <coins> <minutes>` · `stop` · `status` | Boost events, e.g. `start 2 2 60`. | Admins |
| `/ffaadmin kitlevel [<kit> <level>]` | List or set the level a kit needs. | Admins |
| `/ffaadmin hologram set` · `remove` · `info` | Place the floating leaderboard where you stand. | Admins |
| `/ffaadmin migrate json-to-mysql [dry] [overwrite]` | Copy JSON profiles into MySQL. Online players are skipped. Existing rows are kept unless `overwrite` is given. `dry` only counts. | Admins |

Players can choose English or German and toggle the arena scoreboard in **Settings**. The scoreboard shows the arena, player count, kills, deaths, and current streak. Arena sessions block block breaking, block placement, and item drops. Safe zones block combat and damage.

## Configuration

Edit `plugins/FFAGameV2/config.json`, then run `/ffaadmin reload`. Storage, database, the scoreboard interval and the combat settings (`fairness`) apply only after a restart. When you upgrade from 3.x, the plugin adds the new sections with their defaults on the first start, and the values you already have stay as they are.

| Section | Settings |
| --- | --- |
| `fairness` | `combatTagSeconds`, `farmMaxKills`, `farmWindowMinutes` |
| `streaks` | Global streak rewards, announcement steps, server-wide announcements from a streak, shutdown threshold |
| `progression` | XP per kill and bonuses, level curve, ranks, chat prefix, task pool and counts, custom achievements, season length (`MONTHLY` or `WEEKLY`), `timeZone` |
| `economy` | `coinsPerKill`, `coinsPerStreakStep`, `coinStreakCap`, `coinsPerShutdown`, and the `cosmetics` catalogue with id, slot (`kill_effect`, `death_message`, `join_message`), price, effect or text |
| `events` | `schedule`: days, start time, minutes, `xp` and `coins` factors, names |
| `hologram` | `enabled`, `categories` (`KILLS`, `BEST_STREAK`, `KD`, `LEVEL`, `SEASON_KILLS`), `rotateSeconds`, `viewDistance`, `size` |
| `discord` | `webhookUrl` (empty = off), `username`, `streakFrom`, `seasonEnd`, `achievements`, `minIntervalSeconds` |

Kill effects: `flame`, `hearts`, `explosion`, `portal`, `lava`, `enchant`, `critical`, `happy`, `smoke`, `red`, `gold`, `aqua`. Death messages need `{killer}` and `{victim}`, and join messages need `{player}`.

| `storageBackend` | Player profiles and stats |
| --- | --- |
| `JSON` | Local files in `players/`. Runs without a database. |
| `MYSQL` | MySQL table keyed by player UUID. The generated configuration selects MySQL and uses JSON while the database credentials are incomplete. |

For a local JSON setup, use `"storageBackend": "JSON"`. To enable MySQL, create the database and supply its connection settings. The plugin creates the profile table.

```json
{
  "storageBackend": "MYSQL",
  "database": {
    "host": "127.0.0.1",
    "port": 3306,
    "database": "ffa",
    "username": "ffa",
    "password": "your-database-password",
    "table": "ffa_player_profiles",
    "useSsl": false,
    "allowPublicKeyRetrieval": true
  },
  "scoreboard": {
    "enabledByDefault": true,
    "updateIntervalTicks": 20
  }
}
```

If a configured MySQL connection drops while the server is running, the plugin takes stats offline, ends active arena sessions, and sets all arenas to **Offline**. It tries to reconnect up to three times at 15-second intervals. Once MySQL recovers, stats return and an admin can set each arena back to **Live**. If all three attempts fail, the plugin disables itself.

### Data files

| Path under `plugins/FFAGameV2/` | Contents |
| --- | --- |
| `config.json` | All settings above |
| `arenas/` | Arena definitions, spawn points, zones, mode and per-arena streak rewards |
| `kits/` | Kit inventories and armor |
| `lang/en.json`, `lang/de.json` | English and German text |
| `players/` | UUID-keyed profiles in JSON mode |
| `seasons/` | The running season and the archive of finished ones |
| `hologram.json` | Where the floating leaderboard stands |

Kits use item IDs such as `minecraft:iron_sword`. Replace any numeric IDs in existing kit JSON files with their PowerNukkitX equivalents. Items with unrecognized IDs appear as **Unknown Item** and are skipped when equipping.

## Development

The build is defined in [`build.gradle.kts`](build.gradle.kts); `.\gradlew.bat test` runs the tests. The MySQL repository test starts MySQL through Testcontainers and is skipped when Docker is not running.

The Java sources use the `net.zernyth.ffa` namespace. [`FFABootstrap`](src/main/java/net/zernyth/ffa/FFABootstrap.java) wires storage, services, commands, and listeners. The [PowerNukkitX descriptor](src/main/resources/powernukkitx.yml) declares the entry point and admin permission.

| Package | Responsibility |
| --- | --- |
| `arena` | Arena definitions, persistence, sessions, spawns, and safe zones |
| `kit` | Kit storage, selection, and inventory application |
| `player` | Profiles, statistics, preferences, leaderboard cache, and JSON/MySQL repositories |
| `combat`, `streak` | The kill pipeline, combat tag, farm guard, and streak rewards |
| `progression`, `economy` | XP, levels, ranks, seasons, tasks, achievements, coins, and cosmetics |
| `mode` | Duels, spectating, and team arenas |
| `admin`, `event`, `visibility` | Admin tools, boost events, floating leaderboard, and Discord |
| `ui.form`, `command`, `listener` | Menus, command entry points, and server events |
| `scoreboard`, `i18n`, `config` | Live scoreboard, translations, and settings |
