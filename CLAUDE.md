# AltarTestClient

Fabric client mod for Minecraft 1.21.11 (Java 21, Gradle wrapper, Fabric Loom). See README.md.

## After every code change: build and send the jar

The user tests every change on their own PC, so after finishing any edit to the mod:

1. Run `./gradlew build` (compiles and runs the unit tests).
2. If it fails, fix the errors and rebuild. Never send a jar from a failed build.
3. Send `build/libs/altartestclient-<version>.jar` (not the `-sources.jar`) to the user with
   `SendUserFile`, with a one-line caption saying what changed.
4. Commit and push the change.

The build downloads from `maven.fabricmc.net` and Mojang's servers (`piston-meta.mojang.com`,
`piston-data.mojang.com`, `libraries.minecraft.net`, `resources.download.minecraft.net`). If the
environment's network policy blocks them, tell the user which host was denied instead of skipping
the build silently.
