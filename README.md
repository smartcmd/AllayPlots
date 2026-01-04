# 🌾 AllayPlots

AllayPlots is a plot plugin for AllayMC. It provides plot claiming, deletion, permissions, and enter/leave
handling with EconomyAPI integration.

## ✨ Features

- Plot claim, delete, and info queries
- Trust/deny access control
- Plot home and plot flags
- Enter/leave/claim plot events
- Plot protection for building and interactions
- EconomyAPI integration for claim/refund pricing
- Built-in i18n (en_US/zh_CN) and PlaceholderAPI support

## 📦 Dependencies

- EconomyAPI (required)
- PlaceholderAPI (required)

## 📜 Commands

- `/plot claim` - Claim the plot you are standing in
- `/plot auto` - Find and claim the next free plot
- `/plot delete` - Delete the current plot (owner only)
- `/plot info` - Show information about the current plot
- `/plot home [player]` - Teleport to your plot home (or another player's)
- `/plot sethome` - Set your plot home to the current plot
- `/plot trust <player>` - Trust a player on your plot
- `/plot untrust <player>` - Remove a trusted player
- `/plot deny <player>` - Deny a player on your plot
- `/plot undeny <player>` - Remove a denied player
- `/plot flag [flag] [value]` - List, view, or set plot flags

Supported flags:

- `entry` - allow players to enter the plot.
- `build` - allow non-members to build in the plot.
- `pvp` - allow player vs player damage.
- `pve` - allow player damage to entities.
- `damage` - allow players to receive damage.

## ⚙️ Configuration

Config file is created at `plugins/AllayPlots/config.yml`. A full example is available in `config.yml`.
All keys use hyphen style.

```yaml
worlds:
  plotworld:
    plot-size: 35
    road-size: 7
    ground-y: 64
    max-plots-per-player: 2
    claim-price: 100.0
    sell-refund: 50.0
    teleport-on-claim: true
    road-edge-block: minecraft:smooth_stone_slab
    road-corner-block: minecraft:smooth_stone_slab
```

## 💰 EconomyAPI

EconomyAPI is a required dependency. Enable pricing in config:

```yaml
economy:
  enabled: true
  currency: ""
```

When enabled, claiming deducts `claim-price` and deleting refunds `sell-refund`.

Storage backends are configurable:

```yaml
storage:
  type: yaml # yaml, sqlite, h2
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
