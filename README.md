# Faith Runner for Minecraft

Mirror's Edge movement, animation and first-person body in Minecraft Java 26.3, as a Fabric
mod. Faith's movement and animation are the same Rust engine as
[faith-runner](https://github.com/tnrjns/faith-runner) (`crates/`). The mod runs it through
`faith_ffi.dll` with Java's foreign function API, and builds her world from the blocks around you.

Faith's meshes, animations and sounds are read from **your own copy of Mirror's Edge (PC)**
at startup. None of the game's files are in this repository.

## What it does

- **Movement:** Faith's running, jumping, vaults, wall runs, climbs, slides, rolls and melee,
  driving the Minecraft player. Minecraft's own movement is switched off while she's on.
- **Camera and body:** her camera animations, first-person arms in the hand pass, and legs
  in the world, so blocks hide them. Each part of her body is lit by the block it's in.
- **Blocks as Mirror's Edge fixtures:**
  - ladders and vines are ladders (she steps off the top onto a roof);
  - wooden doors are doors she barges or kicks open, and the Minecraft door opens;
  - stairs are ramps; slabs and half blocks are step-ups;
  - hay and slime are soft landings;
  - iron bars, copper bars and fences high enough off the ground are swing poles.
- **Held item:** whatever is in your main hand sits in her right hand.
- **Shaders:** works with Iris and Sodium. Under a shader pack her body, not Steve's, casts the
  shadow.

## Requirements

- Minecraft Java **26.3**, Fabric Loader **0.19.5**, Fabric API **0.161.0+26.3**.
- Java **25**. Minecraft must be started with `--enable-native-access=ALL-UNNAMED` in its JVM
  arguments.
- Windows, with Mirror's Edge (PC) installed.
- To build: Rust (stable) and a JDK 25.

## Building

```sh
# the engine, as faith_ffi.dll (target/release/)
cargo build --release -p faith_ffi

# the mod (minecraft/build/libs/faithrunner-1.0.0.jar)
cd minecraft
./gradlew build
```

`./gradlew deploy` builds both pieces and copies the jar and `faith_ffi.dll` into a profile at
`%APPDATA%\.minecraft\faithrunner-26.3`: the jar in `mods/`, the DLL in the game folder.

## Installing by hand

1. Make a Fabric 26.3 profile in the launcher. Give it its own game directory, and add
   `--enable-native-access=ALL-UNNAMED` to its JVM arguments.
2. Put Fabric API and `faithrunner-1.0.0.jar` in that directory's `mods/`.
3. Put `faith_ffi.dll` in the game directory itself.
4. Start the game once. `config/faithrunner.properties` is written there:
   - `faith_ffi`: the path to the DLL, if you keep it somewhere else;
   - `mirrors_edge`: your Mirror's Edge folder. Leave it empty and the usual Steam, EA and
     Origin locations are searched.

## Playing

- **F8:** Faith on or off.
- **Z:** 180° turn.
- **R:** melee.
- WASD, Space and Shift come from Minecraft's own key bindings.

All three keys can be rebound in Controls. She works in singleplayer: the integrated server is
told to accept her movement (no "moved wrongly" or fall damage while she's on).

## Tests

Fabric client game tests build a world, turn Faith on, run her through each kind of block and
save screenshots to `minecraft/build/run/clientGameTest/screenshots`:

```sh
cd minecraft
./gradlew runClientGameTest --no-configuration-cache
# under Iris + Sodium with a shader pack
./gradlew runClientGameTest -PwithIris -PwithShader -PshaderZip=<pack.zip> --no-configuration-cache
```

The engine's own tests: `cargo test`. The animation tests need `ME_INSTALL` set to your
Mirror's Edge folder; without it they pass without checking anything.

## Not there yet

- Multiplayer servers: the server-side relaxations only apply to the singleplayer owner.
- Reaction Time.
- Under some shader packs her arms look flat-shaded.
