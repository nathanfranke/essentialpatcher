# EssentialPatcher

![Unlocked cosmetics](https://raw.githubusercontent.com/nathanfranke/essentialpatcher/main/dist/cosmetics.png)

### Blocks malicious features from [Essential](https://essential.gg/) and unlocks all cosmetics.

- Telemetry blocked.
- Bloatware (Notifications, Announcements, Direct Messages) blocked.
- Cosmetics unlocked and saved locally.

(Note: Cosmetics are not visible to other players in multiplayer.)

### Installation

1) Understand the security implications of downloading a jar from an unknown source.
2) Download [Essential](https://essential.gg/) or have it loaded by another mod.
3) Download the latest [release of EssentialPatcher](https://github.com/nathanfranke/essentialpatcher/releases).
4) Install both to `mods` on `Fabric 1.19+`.

### Building

1) `./gradlew shadowJar`.

The resulting jar will be in `build/libs/`.

### How it works

This plugin spoofs the Essential websocket server and intercepts all packets set to/from it. Only some packets are whitelisted, such as the login handshake packets. Some packets have custom implementations; for example, the outfit change packet will save changes locally.
