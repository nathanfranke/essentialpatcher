### ⚠️ Archived. Consider using [EssentialCosmeticsUnlocker by dxxxxy](https://github.com/dxxxxy/EssentialCosmeticsUnlocker)

# EssentialPatcher

![Unlocked cosmetics](https://raw.githubusercontent.com/nathanfranke/essentialpatcher/main/dist/cosmetics.png)

### Blocks malicious features from [Essential](https://essential.gg/) and unlocks all cosmetics.

- Telemetry blocked.
- Bloatware (Notifications, Announcements, Direct Messages) blocked.
- Cosmetics unlocked and saved locally.

(Note: Cosmetics are not visible to other players in multiplayer.)

### Installation

1) Understand the security implications of downloading a jar from an unknown source.
2) Download and install [Essential](https://essential.gg/) (or have it loaded by another mod).
3) Download and install [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api).
4) Download and install the latest [release of EssentialPatcher](https://github.com/nathanfranke/essentialpatcher/releases).

### Building

1) `./gradlew shadowJar`.

The resulting jar will be in `build/libs/`.

### How it works

This plugin spoofs the Essential websocket server and intercepts all packets set to/from it. Only some packets are whitelisted, such as the login handshake packets. Some packets have custom implementations; for example, the outfit change packet will save changes locally.

### Note on support for 1.8-1.18

I don't know how to make a Forge mod, so I can't support Forge 1.8 or other versions. However, this mod supports running standalone.

1) `./gradlew run`.
2) Add `-Dessential.cm.host=ws://localhost:13372` to Minecraft's launch arguments.
