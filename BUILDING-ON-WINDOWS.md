# Building and installing on Windows

## One-time setup

1. Install Java 21 (in PowerShell):
   ```powershell
   winget install EclipseAdoptium.Temurin.21.JDK
   ```
   Close PowerShell and open a new one afterwards.
2. In the Modrinth app, make a Fabric instance for Minecraft **1.21.11** and add **Fabric API** to it.
   If the game says the Fabric Loader is too old, open the instance → **Settings → Installation** and
   pick the newest loader version.

## Get the code

1. Download the ZIP (log into GitHub if asked):
   https://github.com/Forgotten-Mu3z/mu3z-macro/archive/refs/heads/claude/funny-bardeen-ob23p6.zip
2. Right-click it → **Extract All** → extract to your Desktop.
3. Open the extracted folder. `gradlew.bat`, `build.gradle` and `src` must be directly inside. If you
   only see another folder, use that inner folder instead.

## Quick way: build-and-install.bat

Double-click `build-and-install.bat` in the folder. It builds the mod and copies the jar into your
Modrinth instance's `mods` folder, deleting any older version of the mod there first. The first time, it lists your Modrinth
instances and asks which one to use (it remembers the answer in `mods-folder.txt`). Close Minecraft
before running it.

The steps below do the same thing by hand.

## Build

1. Inside that folder, right-click an empty spot → **Open in Terminal** (on Windows 11 you may need
   **Show more options** first).
2. Run:
   ```powershell
   .\gradlew.bat build
   ```
   The first build takes a few minutes. It must end with **BUILD SUCCESSFUL**.
3. The mod is `build\libs\altartestclient-<version>.jar` (ignore the `-sources.jar`). The build also
   copies it, together with Fabric API, into `Desktop\mods-claude`, replacing any older version there.

If you type a path yourself and your username has a space in it, put the path in quotes:
```powershell
cd "C:\Users\<your name>\Desktop\<folder>"
```

## Install

1. In the Modrinth app, open the instance → **⋮ / Open folder** → `mods`.
2. Delete any older `altartestclient-*.jar` there, then copy the new jar in.
3. Start the game. Press **Right Shift** for settings and add your test server to *Allowed servers*.

## Updating after a change

Download the ZIP again, extract it over the old folder, and double-click `build-and-install.bat`
(or run `.\gradlew.bat build` and replace the jar in `mods` yourself). Close Minecraft first; Windows won't let you replace a jar the game has loaded.

## Common errors

| Error | Fix |
|---|---|
| `'git' is not recognized` | You don't need Git; use the ZIP download above. |
| `Cannot find path ...\Desktop\...` | Your Desktop may be under OneDrive. Use **Open in Terminal** from the folder instead of typing the path. |
| `A positional parameter cannot be found` | Two commands got pasted on one line. Run one command per line. |
| `Incompatible mods found! ... Fabric Loader` | Update the loader in the instance's **Settings → Installation**, or rebuild from the latest ZIP. |
