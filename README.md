# MobEditor

A Paper 1.20.6 plugin for editing mobs and entities in-game through a configurable tool and GUI. MobEditor lets administrators edit equipment, copy and paste loadouts, adjust drop chances, and extract a fully configured entity into a spawn egg — without touching command syntax or NBT by hand.

## Features

- **Configurable editor tool** — set any held item as the tool with `/me tool set`
- **Right-click to act, Shift+right-click for the mode menu**
- **Four modes**
  - `open` — open the entity's equipment / inventory GUI
  - `copy` — copy the entity's equipment and inventory into a clipboard
  - `paste` — paste the clipboard onto another entity (with confirmation)
  - `egg` — extract the entity, including equipment, attributes, and name, into a spawn egg
- **Equipment editing GUI** — main hand, off hand, helmet, chestplate, leggings, boots
- **Drop chance GUI** — configure per-slot drop chances
- **Paste blanks toggle** — choose whether empty slots clear or skip target slots
- **Spawn egg listener** — spawn the extracted entity by using the egg
- **Chinese messages** included, with a messages file for customization
- **Reload command** for config and messages

## Requirements

| Requirement | Version |
|---|---|
| Server | Paper 1.20.6 |
| Java | 21 |
| Build | Gradle (wrapper included) |

## Build

```bash
./gradlew build
```

The plugin JAR is written to `build/libs/MobEditor-<version>.jar`.

## Installation

1. Put the JAR into your server's `plugins/` directory.
2. Start the server once to generate `plugins/MobEditor/config.yml`.
3. Set the editor tool in-game:

```text
/me tool set
```

Hold the item you want to use as the tool, then run the command. The item is serialized into the config.

## Commands

Main command: `/mobeditor` (alias `/me`).

| Command | Description |
|---|---|
| `/me tool set` | Use the held item as the editor tool |
| `/me tool disable` | Disable the editor tool |
| `/me tool info` | Show the current tool |
| `/me tool clear` | Clear the configured tool |
| `/me mode <open\|copy\|paste\|egg>` | Set the current mode |
| `/me pasteblanks <on\|off>` | Toggle whether empty clipboard slots clear target slots |
| `/me reload` | Reload configuration and messages |

## Usage

1. Set a tool with `/me tool set`.
2. Right-click an entity to execute the current mode.
3. Shift+right-click an entity to open the mode selection GUI.
4. Use the equipment GUI to edit slots, or the drop-chance GUI to change drop probabilities.
5. In `egg` mode, right-click an entity to receive a spawn egg containing its full configuration.

## Permissions

| Permission | Default | Description |
|---|---|---|
| `mobeditor.admin` | `op` | All admin commands (includes `mobeditor.use`) |
| `mobeditor.use` | `op` | Use the editor tool on entities |

## Project layout

```text
src/main/java/com/ohtoai/mobeditor/
├── command/      command routing and tab completion
├── config/       config and message loading
├── clipboard/    equipment / inventory clipboard
├── egg/          spawn egg extraction
├── gui/          equipment, drop chance, mode selection, paste confirmation
├── listener/     tool use, egg use, player quit
├── mode/         editor modes
└── tool/         tool item configuration
src/main/resources/
├── config.yml
├── messages_zh.yml
└── plugin.yml
```

## License

No license file is currently included in this repository.
