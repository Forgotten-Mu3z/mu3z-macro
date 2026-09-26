# AltarTestClient

Fabric client mod for Minecraft 1.21.11 (Java 21, Gradle wrapper, Fabric Loom). See README.md.

## After every code change: build and send the jar

The user tests every change on their own PC, so after finishing any edit to the mod:

1. Bump `mod_version` in `gradle.properties` for every release (patch for fixes, minor for new
   features), so each jar the user gets has a new version number.
2. Run `./gradlew build` (compiles and runs the unit tests). The build also copies the new jar plus
   Fabric API into `Desktop/mods-claude` and deletes any older `altartestclient-*.jar` there; in a cloud
   session use `./gradlew build -PmodsDir=mods-claude`.
3. If it fails, fix the errors and rebuild. Never send a jar from a failed build.
4. Send `build/libs/altartestclient-<version>.jar` (not the `-sources.jar`) to the user with
   `SendUserFile`, with a one-line caption saying what changed.
5. Commit and push the change. Work on one branch; `BUILDING-ON-WINDOWS.md` links the ZIP of that branch.

The build downloads from `maven.fabricmc.net` and Mojang's servers (`piston-meta.mojang.com`,
`piston-data.mojang.com`, `libraries.minecraft.net`, `resources.download.minecraft.net`). If the
environment's network policy blocks them, tell the user which host was denied instead of skipping
the build silently.
