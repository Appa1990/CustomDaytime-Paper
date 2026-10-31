# CustomDaytime Paper

Paper 26.3 adaptation of [SeedimV/CustomDaytime](https://github.com/SeedimV/CustomDaytime),
based on upstream version 2.1.2. Original authorship and GPL-3.0 licensing are preserved.
This repository contains the 2.1.4 adaptation with longer days and iron-farm sleep compensation.
The upstream Modrinth project is not the distribution channel for this adaptation.

## Build

Use Java 25 and run `./gradlew :common:test :paper:test :paper:shadowJar`
(`gradlew.bat` on Windows). The installable JAR is written to `paper/build/libs/`.
The Sponge module is retained from upstream; this adaptation is tested and built for Paper.
FarmLoader is maintained separately in [Appa1990/FarmLoader](https://github.com/Appa1990/FarmLoader).

Customize Minecraft's day/night cycle and experience smoother nights with this lightweight plugin/mod!

## Paper 26.3 build

Requires Paper 26.3 and Java 25. New configurations default to a 40-minute
day and a 10-minute night, with vanilla sleep-to-skip behavior. Durations are
measured at 20 TPS; this plugin does not change the server tick rate.
Day covers world time 0–11999, and night covers 12000–23999, including twilight.
Existing `plugins/CustomDaytime/config.conf` files are preserved: change
`dayLength=40.0`, `nightLength=10.0`, and `accelerationEnabled=false` in your
world's block, then restart the server. Values must be finite and at least
1/1200 minute (one server tick).

### Iron farms (Paper 2.1.4)

Long daylight cycles can outlast villagers' vanilla 24,000-game-tick sleep
requirement for summoning iron golems. `ironFarmSleepCompensation=true` in each
world block keeps a real sleep valid for the configured day + night duration,
with a minimum of the vanilla 20 minutes. At 40 + 10 minutes, the allowance is
50 minutes at 20 TPS. Villagers still need to actually sleep; golem detection,
spawn cooldowns, beds, and other farm conditions are unchanged.

This Paper feature defaults to enabled even in existing configs where the key
is missing. Set `ironFarmSleepCompensation=false` and restart to disable it.
The true sleep timestamp is saved on the villager, so unloading or restarting
does not renew the allowance. Loaded villagers' original timestamps are restored
on normal Paper plugin shutdown. Unloaded villagers may retain the adjusted
timestamp until reloaded with this plugin; after removing the plugin entirely,
the remaining vanilla sleep allowance expires within 20 ticking minutes.
After installing, let the villagers sleep once if they have not slept recently.

> [!IMPORTANT]
> Since **v2.0.0**, the configuration file was changed from `config.yml` to `config.conf`.
> **Old `config.yml` files are not compatible and will not work.**
> The configuration format has also changed, so the old structure/syntax must be migrated manually.

## Features

- **Configurable Day & Night Lengths**  
  Set the duration of Minecraft's day and night in **minutes** via the config. Accepts decimal values for more precise timings (e.g., `0.5` for 30 seconds).

- **Accelerate Night While Sleeping**  
  Instead of instantly skipping the night, time will **smoothly accelerate** when enough players sleep. This feature can be disabled in config.

- **Easy-to-Use Config**  
  All values are adjustable in the `config.conf` file. All values are configured for each world separately.
  Custom Daytime supports multiple worlds. Copy the `"minecraft:overworld"` block, paste it at the end of the config, rename it to the target world key, and edit the values.
    ```hocon
    "minecraft:overworld" {
    # Length of the daytime cycle in minutes
    # Default: 40.0
    dayLength=40.0

    # Length of the nighttime cycle in minutes
    # Default: 10.0
    nightLength=10.0

    # Paper: extend real sleep validity to the configured cycle for iron farms
    # Default: true
    ironFarmSleepCompensation=true

    # Enable or disable night acceleration when enough players sleep
    # False: use vanilla sleep-to-skip-night mechanic
    # Default: false
    accelerationEnabled=false

    # Multiplier for night acceleration during sleep
    # Example: nightLength=10.0 (= 600 seconds) and AccelerationMultiplier=100.0 -> Acceleration lasts 6 seconds
    # Default: 100.0
    AccelerationMultiplier=100.0
  }

## License
This project is licensed under the [GNU General Public License v3.0](https://github.com/SeedimV/CustomDaytime/blob/master/LICENSE). You are free to use, modify, and distribute the project, but any derivative works must also be licensed under the same terms.
