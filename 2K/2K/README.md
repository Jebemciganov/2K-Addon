# 2K - Meteor Client addon (Minecraft 1.21.11)

Two categories are registered: **PVP** (20 modules) and **Utility** (1 module so far).

## Build

### Easiest: let GitHub build the jar
1. Create a GitHub repo and push this folder to it.
2. Open the **Actions** tab, run **Build jar** (it also runs on every push).
3. Download the `2K-jar` artifact. Use `2K-1.0.0.jar` (not the `-sources` jar).

### Locally
1. Install JDK 21.
2. Either install Gradle 9.x, or copy the `gradle/` folder, `gradlew` and `gradlew.bat` from the official Meteor addon template
   (https://github.com/MeteorDevelopment/meteor-addon-template) into this folder (Loom 1.14 needs a recent Gradle wrapper).
3. Run `./gradlew build` (Windows: `gradlew.bat build`), or `gradle build` if you installed Gradle.
4. Put `build/libs/2K-1.0.0.jar` in your `mods` folder next to Meteor Client for 1.21.11.

If Gradle says a version does not exist, update `yarn_mappings` / `loader_version` in `gradle.properties`
to the latest 1.21.11 values from https://fabricmc.net/develop and make sure Meteor's 1.21.11 snapshot is published.

## Modules
| Module | What it does |
|---|---|
| anti-bot | Flags fake players (not in tab list, bad/duplicate name, optional 0 ping). Other modules skip them. |
| silent-swap | Makes every 2K module swap server-side only (packet swap). |
| aim-optimizer | Chooses the aim point on a hitbox (closest to crosshair / head / body). AimAssist uses it when on. |
| aim-assist | Smoothly pulls aim toward players. FOV, speed, smoothing, randomness, weapon-only, require-click. |
| trigger-bot | Attacks when the crosshair is on a target. Cooldown, delay, crit-only, weapon-only. |
| shield-breaker | Axe-swaps to hit players who are blocking, then swaps back. |
| auto-totem | Keeps a totem in the offhand (optional health threshold). |
| auto-inventory-totem | Refills offhand / a hotbar slot with totems while the inventory is open. |
| auto-crystal | Places and breaks crystals using an estimated-damage check (min damage, max self damage, face-place, anti-suicide). |
| anchor-macro | Look at an anchor: charges with glowstone, then detonates. |
| auto-safe-anchor | Places an anchor beside a target, puts a block between you and it, charges, detonates; aborts if self-damage is too high. |
| auto-cart | Rail + TNT minecart + flint/fire charge near a target. |
| cart-protection | Warns, equips a totem and breaks slow, unlit TNT minecarts near you. |
| auto-mace | Mace smash while falling onto a player; optional wind-charge boost. |
| mace-swap | Swaps to a mace for falling hits, then back. |
| breach-swap | Swaps to a Breach mace when hitting armored targets, then back. |
| spear-lunge | Swaps to your best Lunge spear, jabs, swaps back. |
| fake-lag | Holds movement packets for a random 80-160 ms window (configurable). |
| lag-range | Holds packets while an enemy approaches, releases when they are in range. |
| auto-drain | Picks up nearby water/lava source blocks with an empty bucket. |

## Utility
| Module | What it does |
|---|---|
| sus-chunk-finder | Scores chunks for signs of long-term player loading (overgrown kelp/bamboo/vines/berries/dripstone, plus hidden amethyst revealed by server light data), merges nearby hits into zones, announces new zones in chat and outlines them. Light data is collected from the moment you join, so enable it early or re-enter chunks. |

## Notes
- Written from scratch against the Meteor addon API; not compiled in the environment it was written in (no network), so expect to fix a few Yarn name differences on first build.
- Crystal/anchor damage is an estimate (ignores enchantments and status effects).
- Servers with anticheat may flag several of these; use at your own risk.
