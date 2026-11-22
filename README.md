<p align="center">
  <img src="docs/assets/zernyth-ffa.png" alt="Zernyth FFA with the silver and violet Zernyth emblem beside a torchlit PvP arena" width="100%">
</p>

<div align="center">

# Zernyth FFA

**Multi-arena free-for-all for Minecraft: Bedrock Edition**

Run PvP arenas on PowerNukkitX with configurable kits, protected spawn areas, and in-game menus for players and admins.

**PowerNukkitX 3.0.6 · Java 21+ · English / German**

[Quick start](#quick-start) · [Arena setup](#arena-setup) · [Commands](#commands) · [Configuration](#configuration) · [Development](#development)

</div>

---

## Quick start

Build with a JDK 21 or newer. Choose either build tool.

| Build tool | Command | Plugin JAR |
| --- | --- | --- |
| Gradle Wrapper (Windows PowerShell) | `.\gradlew.bat build` | `build/libs/ZernythFFA.jar` |
| Gradle Wrapper (Linux/macOS) | `sh ./gradlew build` | `build/libs/ZernythFFA.jar` |
| Maven | `mvn clean package` | `target/ZernythFFA.jar` |

The Gradle Wrapper downloads the pinned Gradle version on first use. The Maven command uses a local Maven installation.

1. Stop the PowerNukkitX 3.0.6 server and copy `ZernythFFA.jar` into its `plugins/` directory. When upgrading, replace the previous plugin JAR. Start the server again.
2. In game, open `/ffaadmin` as an operator or with the `ffa.admin` permission. Follow the arena setup below, then join through `/ffa`.

On first launch, the plugin creates `plugins/FFAGameV2/` and a **Soldier** kit if no kits exist. Player profiles use JSON until MySQL is configured.

## Arena setup

1. In **Arena Management → Create Arena**, enter an arena ID, a display name, and the name of an existing server world.
2. Stand at a spawn point in that world and use **Add Spawn** to record your position and facing direction. Repeat for each spawn you want to add.
3. To protect an area, open **Arena Management → Zone Tools** and start safe-zone placement. Select the arena and click two opposite corner blocks in its world. The cuboid saves automatically.
4. Open **Set Arena Status** and choose **Live**. An arena needs a loadable world and at least one spawn before players can join.

Use **Kit Management → Create / Update Kit → Snapshot from inventory** to save your current items and armor. The admin menu also lets you edit individual kit slots and armor pieces.

## Commands

| Command | Opens | Access |
| --- | --- | --- |
| `/ffa` | Arena, stats, and settings menus. Inside an arena, it opens the leave, kit, and settings options. | In-game players |
| `/ffaadmin` | Arena and kit management forms. | Operators by default, or players with `ffa.admin` |

Players can choose English or German and toggle the arena scoreboard in **Settings**. The scoreboard shows the arena, player count, kills, deaths, and current streak. Arena sessions block block breaking, block placement, and item drops. Safe zones block combat and damage.

## Configuration

Edit `plugins/FFAGameV2/config.json` and restart the server to apply changes.

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
| `config.json` | Storage and scoreboard settings |
| `arenas/` | Arena definitions, spawn points, and zones |
| `kits/` | Kit inventories and armor |
| `lang/en.json`, `lang/de.json` | English and German text |
| `players/` | UUID-keyed profiles in JSON mode |

Kits use item IDs such as `minecraft:iron_sword`. Replace any numeric IDs in existing kit JSON files with their PowerNukkitX equivalents. Items with unrecognized IDs appear as **Unknown Item** and are skipped when equipping.

## Development

Keep the Java target and dependency versions in sync between [`build.gradle.kts`](build.gradle.kts) and [`pom.xml`](pom.xml) when changing the build.

The Java sources use the `net.zernyth.ffa` namespace. [`FFABootstrap`](src/main/java/net/zernyth/ffa/FFABootstrap.java) wires storage, services, commands, and listeners. The [PowerNukkitX descriptor](src/main/resources/powernukkitx.yml) declares the entry point and admin permission.

| Package | Responsibility |
| --- | --- |
| `arena` | Arena definitions, persistence, sessions, spawns, and safe zones |
| `kit` | Kit storage, selection, and inventory application |
| `player` | Profiles, statistics, preferences, and JSON/MySQL repositories |
| `ui.form`, `command`, `listener` | Menus, command entry points, and server events |
| `scoreboard`, `i18n`, `config` | Live scoreboard, translations, and settings |
