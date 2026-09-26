# AltarTestClient

A Fabric client mod that automates PvP actions so the AltarSMP anti-cheat can be tested against
known, logged input. Every module has a **humanlike** mode (randomized delays) and a **blatant** mode
(no added delay). Every automated action is written to a CSV log with timestamps, so you can line it up
against your anti-cheat's flag log.

The client stays inactive except on servers listed in `allowedServers` (and in singleplayer, if
enabled). Everywhere else no module ticks, no hooks fire, and the HUD shows **Inactive**.

| | |
|---|---|
| Minecraft | 1.21.11 (Java Edition) |
| Mappings | Mojang official (`loom.officialMojangMappings()`) |
| Fabric Loader | ≥ 0.19.5 |
| Fabric API | 0.141.6+1.21.11 |
| Java | 21 |
| Build | Gradle 9.5.1 wrapper, Fabric Loom 1.17 (`net.fabricmc.fabric-loom-remap`) |

## Build and run

```sh
./gradlew build        # jar -> build/libs/altartestclient-1.0.0.jar (also runs the unit tests)
./gradlew runClient    # dev client with the mod loaded
```

Put the jar and Fabric API in `.minecraft/mods/` of a Fabric 1.21.11 profile.

Every `build` also copies the mod jar and the matching Fabric API jar into `Desktop/mods-claude/`
(older copies there are replaced). Use `-PmodsDir=<folder>` for a different folder or `-PskipModsCopy` to
skip the copy.

## First launch

1. Start the game once. `config/altartestclient.json` is created from the bundled default.
2. Add your test server's address to the allow-list, in either of these places:
   - **In game:** press **Right Shift**, open **Global settings**, and type into *Allowed servers*
     (comma separated).
   - **In the file:** edit `"allowedServers"`. Entries ignore the port, and `*.altarsmp.net` matches every
     subdomain as well as `altarsmp.net` itself.
3. Join the server. Chat shows `Active on <host>` or `Inactive: '<host>' is not in allowedServers`.

## Keys

| Key | Default | |
|---|---|---|
| Open settings | Right Shift | Module list with ON/OFF toggles, per-module settings, key binding, allow-list |
| Panic | End | Disables every module instantly, aborts in-flight sequences and restores the held slot. It also works while the settings screen is open. |
| One per module | unbound | Bind in the settings screen (select module, then **Key:**) or in Options → Controls → Key Binds → AltarTestClient |

For toggle modules, the key turns the module on or off. **Pearl Macro** and **Elytra Swap** are one-shot
actions instead: their ON/OFF toggle *arms* the key, and each key press then performs the action once.

## Modules

"Packets" lists what the server receives, in order, which is what your checks will see.

| Module | What it does | Packets |
|---|---|---|
| **Stun Slam** | Target (crosshair or nearest) is blocking with a shield: select axe and hit, **next tick** select mace and hit, swap back. | `SetCarriedItem(axe)`, `Interact(ATTACK)`, `Swing` → next tick → `SetCarriedItem(mace)`, `Interact(ATTACK)`, `Swing`, `SetCarriedItem(orig)` |
| **Trigger Bot** | Crosshair on a player and attack cooldown ≥ threshold: vanilla left click after a reaction delay (min/max ms ± jitter). | `Interact(ATTACK)`, `Swing` |
| **Aim Assist** | Every frame, turns the camera toward the best player in the FOV cone and range (turn speed in °/s, aim point, vertical on/off). | Rotation inside normal movement packets |
| **Auto Totem** | Offhand isn't a totem (e.g. right after a pop): moves one from the inventory to the offhand after a delay. | `ContainerClick(SWAP, button 40)` |
| **Auto Crystal** | Breaks crystals near the target, and places crystals on the obsidian/bedrock closest to the target within reach. Separate place/break delays; optional rotation. | `[Rot]`, `UseItemOn(top face)`, `Swing` / `[Rot]`, `Interact(ATTACK crystal)`, `Swing` |
| **Anchor Macro** | Places a respawn anchor next to the target, charges it with glowstone, detonates it with a non-glowstone item, and swaps back. Skipped in the Nether and while sneaking. | `SetCarriedItem` + `UseItemOn` ×(2 + charges) |
| **Breach Swap** | Hooks every attack (yours, Trigger Bot's, Autoclicker's): selects a Breach/Density mace just before the attack packet, then swaps back. | `SetCarriedItem(mace)`, `Interact(ATTACK)`, `Swing`, `SetCarriedItem(orig)` (same tick when blatant) |
| **Shield Breaker** | Target raises a shield: select an axe after a reaction delay. Optional auto hit, swap back when the shield drops. | `SetCarriedItem(axe)` [+ `Interact(ATTACK)`] … `SetCarriedItem(orig)` |
| **Pearl Macro** | One key: select pearl, throw, swap back. Uses the offhand directly if it holds pearls. | `SetCarriedItem(pearl)`, `UseItem`, `Swing`, `SetCarriedItem(orig)` |
| **Elytra Swap** | One key: chestplate ↔ elytra. A hotbar item takes 1 click; a main-inventory item takes 3 clicks. | `ContainerClick(SWAP 6↔hotbar)` or `ContainerClick(PICKUP)` ×3 |
| **Autoclicker** | Vanilla left clicks at the configured CPS while attack is held (or always). Pauses on blocks so mining still works. | `Interact(ATTACK)` / `Swing` per click |

Each module file begins with a short comment describing how it works
(`src/main/java/net/altarsmp/testclient/module/...`).

### Speed modes

Every module has a `speed` setting:

- **HUMANLIKE**: each step waits a delay drawn from a normal distribution centred between the module's
  min and max settings (sd = range / 4, clamped). Aim Assist caps the turn rate, eases out near the
  target, and adds noise. Autoclicker varies each interval by the *Randomization* % and occasionally
  hesitates.
- **BLATANT**: no added delay. Steps run in the same tick. The exceptions are steps the game mechanic
  forces to be separate (Stun Slam's mace hit is always the next tick), and Rotate = *Camera*, which
  needs one tick per turn. Aim Assist snaps; Autoclicker uses perfectly even intervals.

### Rotate (Auto Crystal, Anchor Macro)

- `OFF`: no rotation.
- `CAMERA`: turns the camera and acts on the next tick, after the rotation has been sent normally.
- `PACKET`: turns the camera and sends an extra `MovePlayer.Rot` packet immediately before acting.

These modules deliberately have no self-damage protection.

## HUD

The top-left overlay lists enabled modules with `[H]`/`[B]` tags (green = humanlike, orange =
blatant), plus warnings for *Inactive* and *PANIC*. It is hidden by F1 and while the F3 debug screen is open.
It can be turned off under Global settings.

## Action log

Log files go to `.minecraft/altartestclient/logs/actions_<date>_<time>_<host>.csv`, one file per server
session (**Open logs** in the settings screen opens the folder).

```
epoch_ms,iso_time,client_tick,game_time,module,speed,action,target,detail
1790288465123,2026-09-24T22:21:05.123Z,48211,1893342,Client,,SESSION_START,localhost,
1790288471003,2026-09-24T22:21:11.003Z,48329,1893460,Stun Slam,HUMANLIKE,AXE_HIT,Steve,slot 0->1 reach 2.71
1790288471102,2026-09-24T22:21:11.102Z,48331,1893462,Stun Slam,HUMANLIKE,MACE_HIT,Steve,slot 2 fall 3.40 shieldUp false
1790288471171,2026-09-24T22:21:11.171Z,48332,1893463,Stun Slam,HUMANLIKE,SWAP_BACK,Steve,slot 0
```

| Column | Meaning |
|---|---|
| `epoch_ms`, `iso_time` | Wall clock of the client machine. Keep client and server clocks NTP-synced when comparing. |
| `client_tick` | Client ticks since launch. |
| `game_time` | World game time as last synced from the server: the closest client-side value to the server's tick counter. |
| `module`, `speed` | Which module acted and in which mode. `Client` rows are observed events (session start/end, allow-list result, totem pops, panic). |
| `action`, `target`, `detail` | What was done, to whom, and parameters (slots, reach, delays, CPS intervals, positions). |

Module ENABLE/DISABLE toggles are logged too, so a flag can be matched against the exact configuration
that was active.

## Configuration

`config/altartestclient.json` has the same structure as the bundled default,
[`src/main/resources/altartestclient-default.json`](src/main/resources/altartestclient-default.json).
Settings changed in game are saved automatically. Values out of range are clamped, and unknown or
malformed keys are ignored. Key bindings are stored by vanilla in `options.txt`.

The bundled default is generated from the Java setting declarations, so it can't drift from the code:

```sh
python3 tools/gen_default_config.py          # regenerate after changing a setting
python3 tools/gen_default_config.py --check  # fails if out of date (also covered by the unit tests)
```

## Project layout

```
src/main/java/net/altarsmp/testclient/
├── AltarTestClient.java          entry point: wiring, tick loop, join/leave handling
├── config/                       ConfigManager (JSON load/save), GlobalConfig (allow-list, HUD, logging)
├── gui/                          ConfigScreen + setting widgets (buttons, sliders)
├── hud/                          ModuleListHud (Fabric HudElementRegistry)
├── input/                        Keybinds (vanilla KeyMappings, panic, GUI)
├── log/                          ActionLogger (CSV)
├── mixin/                        attack hook, frame hook, totem-pop hook, two invokers
├── module/                       Module base, ModuleManager, ActionLock, SpeedMode, RotateMode
│   ├── setting/                  Boolean/Int/Double/Enum settings
│   ├── combat/                   Stun Slam, Trigger Bot, Aim Assist, Auto Totem, Auto Crystal,
│   │                             Anchor Macro, Breach Swap, Shield Breaker, Autoclicker
│   └── utility/                  Pearl Macro, Elytra Swap
└── util/                         targeting, rotation, inventory, blocks, timing, server allow-list
src/main/resources/               fabric.mod.json, mixin config, lang, default config
src/test/java/                    unit tests (settings, allow-list matching, delays, config round-trip)
```

### Mixins

| Mixin | Target | Why |
|---|---|---|
| `MultiPlayerGameModeMixin` | `attack` HEAD/RETURN | Breach Swap wraps every attack packet |
| `MouseHandlerMixin` | `turnPlayer` TAIL | Per-frame hook so Aim Assist is as smooth as mouse input |
| `ClientPacketListenerMixin` | `handleEntityEvent` TAIL | Totem pop detection (entity event 35) for Auto Totem and the log |
| `MinecraftInvoker` | `startAttack` | Trigger Bot / Autoclicker click through vanilla's own left-click code |
| `MultiPlayerGameModeInvoker` | `ensureHasSentCarriedItem` | Send the held-item packet immediately after a hotbar swap |

## Porting to another 1.21.x

The code targets 1.21.11 Mojang names. The main breakpoints if you need an older 1.21.x:

- **< 1.21.11**: `net.minecraft.resources.Identifier` was `ResourceLocation`.
- **< 1.21.9**: `KeyMapping` took a `String` category instead of `KeyMapping.Category`, and
  `Screen.keyPressed` took `(int, int, int)` instead of `KeyEvent`.
- **< 1.21.6**: `HudElementRegistry` doesn't exist; use `HudRenderCallback`.
- **< 1.21.5**: `Inventory.selected` was a public field instead of `getSelectedSlot()/setSelectedSlot()`;
  shields used `ShieldItem`/`AxeItem` checks instead of the `blocks_attacks`/`weapon` components.

Update `minecraft_version` and `fabric_api_version` in `gradle.properties`
(see <https://fabricmc.net/develop>).
