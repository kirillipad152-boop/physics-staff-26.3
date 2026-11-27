# Physics Staff — Minecraft 26.3

An unofficial port of the Creative Physics Staff from [Simulated-Project](https://github.com/Creators-of-Aeronautics/Simulated-Project) for Minecraft 26.3 on NeoForge. The staff lets you move, rotate, and lock Sable structures in place. Its default range is 128 blocks, and locks are saved with the world.

## Installation

Requires Minecraft **26.3**, NeoForge **26.3.0.118**, and the Sable port **2.0.7+26.3-port**. Place the Physics Staff and Sable JARs in the `mods/` folder. For multiplayer, install both mods on the client and the server.

## Controls

The staff is in the Tools & Utilities tab of the Creative inventory.

| Default input | Action |
| --- | --- |
| Right-click | Grab or release a structure |
| Left-click | Lock or unlock a structure |
| Hold `Tab` and move the mouse | Rotate the structure you're holding |
| Mouse wheel | Adjust the distance to the structure you're holding; hold the sprint key for larger steps |

## Building from source

Install **JDK 25**. You'll also need a prebuilt JAR of the Sable port for 26.3, which is not included in this repository. Place it at `../sable-26.3/build/libs/sable-2.0.7+26.3-port.jar` relative to the Physics Staff project root.

- Linux / macOS: `./gradlew build --no-daemon`
- Windows: `.\gradlew.bat build --no-daemon`

Build output: `build/libs/physics_staff-26.3-1.0.0+26.3.jar`.

## License

[MIT](LICENSE.md). Original implementation by The Simulated Team / The Creators of Aeronautics.
