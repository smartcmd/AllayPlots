<div align="center">

<img src="logo.svg" alt="AllayPlots Logo"/>

</div>

## 📖 Overview

**AllayPlots** is a comprehensive plot world plugin designed specifically for [AllayMC](https://github.com/AllayMC/Allay), a high-performance Minecraft server software. It provides a complete plot management system with claiming, permissions, economy integration, and robust protection features.

Perfect for survival servers, creative building communities, and any server needing organized player building areas with full protection and management capabilities.

## ✨ Features

### 🏗️ Plot Management
- **Claim System** - Claim plots by standing in them or use auto-claim for instant assignment
- **Plot Deletion** - Remove your plots with optional refund
- **Plot Info** - Display detailed plot information including owner, trusted players, and flags
- **Plot Home** - Set and teleport to plot homes, even visit other players' plots
- **Plot Merging** - Combine adjacent plots to create larger building spaces

### 🔐 Access Control & Protection
- **Trust System** - Grant specific players access to build on your plot
- **Deny System** - Block specific players from entering your plot
- **Plot Flags** - Fine-grained control over plot behavior (entry, build, pvp, pve, damage)
- **Road Protection** - Prevent griefing in road areas
- **Build Protection** - Automatically prevent non-members from modifying plots

### 💰 Economy Integration
- Full **EconomyAPI** support
- Configurable claim prices
- Refund system when deleting plots
- Custom currency support

### 🌍 Internationalization
- Built-in support for **English** and **Chinese** (简体中文)
- Easy to add new languages
- **PlaceholderAPI** integration for custom messages

### 💾 Storage Backends
- **YAML** - Human-readable file storage (default)
- **SQLite** - Lightweight database storage
- **H2** - High-performance embedded database

## 📦 Dependencies

| Dependency                                                  | Version         | Required |
|-------------------------------------------------------------|-----------------|----------|
| [AllayMC](https://github.com/AllayMC/Allay)                 | 0.20.0-SNAPSHOT | ✅ Yes    |
| [EconomyAPI](https://github.com/AllayMC/EconomyAPI)         | Latest          | ✅ Yes    |
| [PlaceholderAPI](https://github.com/AllayMC/PlaceholderAPI) | Latest          | ✅ Yes    |

## 🚀 Installation

1. **Download** the latest `AllayPlots-*.jar` from the [releases](../../releases) page
2. **Place** the jar file in your server's `plugins/` directory
3. **Restart** your server
4. **Configure** the generated `plugins/AllayPlots/config.yml` file
5. **Create** a plot world using the plot generator preset

## 📜 Commands

| Command                     | Description                                      | Permission                |
|-----------------------------|--------------------------------------------------|---------------------------|
| `/plot claim`               | Claim the plot you are standing in               | `allayplots.claim`        |
| `/plot auto`                | Find and claim the next free plot                | `allayplots.auto`         |
| `/plot delete`              | Delete the current plot (owner only)             | `allayplots.delete`       |
| `/plot merge [direction]`   | Merge with adjacent plot                         | `allayplots.merge`        |
| `/plot unmerge [direction]` | Unmerge from adjacent plot                       | `allayplots.unmerge`      |
| `/plot info`                | Show current plot information                    | `allayplots.info`         |
| `/plot list`                | List your plots                                  | `allayplots.command.plot` |
| `/plot visit <player>`      | Visit another player's plot home                 | `allayplots.command.plot` |
| `/plot visit <x> <z>`       | Visit a plot by coordinates                      | `allayplots.command.plot` |
| `/plot home [player]`       | Teleport to your plot home (or another player's) | `allayplots.home`         |
| `/plot sethome`             | Set plot home to current location                | `allayplots.sethome`      |
| `/plot setowner <player>`   | Set plot owner (admin)                           | `allayplots.admin.bypass` |
| `/plot trust <player>`      | Trust a player on your plot                      | `allayplots.trust`        |
| `/plot untrust <player>`    | Remove a trusted player                          | `allayplots.untrust`      |
| `/plot deny <player>`       | Deny a player from your plot                     | `allayplots.deny`         |
| `/plot undeny <player>`     | Remove a denied player                           | `allayplots.undeny`       |
| `/plot flag [flag] [value]` | List, view, or set plot flags                    | `allayplots.flag`         |

**Merge Directions:** `north`, `east`, `south`, `west` (defaults to your facing direction)

## 🚩 Plot Flags

| Flag     | Description                               |
|----------|-------------------------------------------|
| `entry`  | 🚶 Allow players to enter the plot        |
| `build`  | 🔨 Allow non-members to build in the plot |
| `pvp`    | ⚔️ Allow player vs player damage          |
| `pve`    | 🗡️ Allow player damage to entities       |
| `damage` | 💥 Allow players to receive damage        |

## ⚙️ Configuration

The config file is created at `plugins/AllayPlots/config.yml`.

### 🌍 World Configuration

```yaml
worlds:
  plotworld:  # World name
    plot-size: 35              # Plot size (inside area, excluding roads)
    road-size: 7               # Road width between plots
    ground-y: 64               # Y-level for plot generation
    max-plots-per-player: 2    # Plots limit per player
    claim-price: 100.0         # Cost to claim a plot
    sell-refund: 50.0          # Refund when deleting
    teleport-on-claim: true    # TP to plot on claim
    plot-block: minecraft:grass_block
    road-block: minecraft:oak_planks
    road-edge-block: minecraft:smooth_stone_slab
    road-corner-block: minecraft:smooth_stone_slab
```

### 💰 Economy Settings

```yaml
economy:
  enabled: false   # Enable economy features
  currency: ""     # Custom currency name (empty = default)
```

### 💾 Storage Options

```yaml
storage:
  type: yaml   # Options: yaml, sqlite, h2
```

### 🔧 General Settings

```yaml
settings:
  protect-roads: true             # Prevent breaking road blocks
  auto-save-interval-ticks: 6000  # Auto-save every 5 minutes
  use-action-bar: true            # Use action bar for messages
```

## 🎯 Events

AllayPlots provides custom events for other plugins to hook into:

| Event            | Description                       |
|------------------|-----------------------------------|
| `PlotClaimEvent` | Fired when a plot is claimed      |
| `PlotEnterEvent` | Fired when a player enters a plot |
| `PlotLeaveEvent` | Fired when a player leaves a plot |

## 🏗️ Building from Source

```bash
# Clone the repository
git clone https://github.com/smartcmd/AllayPlots.git
cd AllayPlots

# Build with Gradle
./gradlew build

# The jar file will be in build/libs/
```

**Requirements:**
- Java 21 or higher
- Gradle wrapper (`./gradlew`)

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Built for the [AllayMC](https://github.com/AllayMC/Allay) server software
- Inspired by popular plot plugins like PlotSquared and PlotMe

<div align="center">

Made with ❤️ by the AllayMC community

</div>
