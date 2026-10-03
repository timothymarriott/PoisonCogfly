# PoisonCogfly

A personal fork of [Cogfly](https://github.com/Nix-main/Cogfly) by Nix/Ambershadow. This is not an official
release and is not affiliated with the upstream project; please don't report issues with this fork upstream.

> **AI disclosure:** The changes in this fork were written with the assistance of generative AI (Claude, by Anthropic).

A cross-platform mod manager for [Hollow Knight: Silksong](https://hollowknightsilksong.com/). Currently only supports English.

## Usage
- Download a build from the [CI workflow](https://github.com/timothymarriott/PoisonCogfly/actions) of this repository
- If you have the original Cogfly installed, PoisonCogfly offers to copy its profiles, game versions and settings on first launch (your original data is left untouched)
- Select game path if not automatically found
  - Xbox Games users will need to change their file type to "All Files" in the file dialog if the game is not automatically found
    - They must also select a **non-exe** file in their game folder to avoid file permission issues
    - The game installs by default to `C:\XboxGames\Hollow Knight Silksong\Content`. If your game is installed here, it *should* auto-detect.
- Confirm profile path
- Create a profile (onboarding should guide you)
- Click the profile's icon to manage it and install mods to it
- Click 'Launch' to launch the modded profile

## Additional info
- If you rely on Steam controller compatability or would like to keep your save files, you can either:
  - Launch with steam enabled
  - Set `Launch With Steam` in `Settings` to true
- Linux users must have Zenity or Kdialog installed for file and folder pickers to work properly. PoisonCogfly will fall back to manual input fields if neither is found.


## Building
If you're just looking for a jar file, it can be found in the release artifacts.
The latest CI build can be retrieved from the workflow, but building manually is basic.

Windows:\
__To build Cogfly on Windows, you'll need both GCC and G++ in your PATH, as they are invoked to compile the Windows file dialog libraries.__\
`.\gradlew.bat clean shadowJar`

Unix/OSX:\
__To build a cross-platform jar that works on Windows, you'll need a Windows cross-compiler, such as MinGW-w64. If not using MinGW-w64, you'll need to modify `build.gradle.kts` to match your compiler's command. If you do not have this, the Windows file dialog libraries will be skipped.__\
`./gradlew clean shadowJar`

The output will be in __/build/libs__

<details>
<summary><h3>Credits</h3></summary>

This fork builds on [Cogfly](https://github.com/Nix-main/Cogfly). Nix (Ambershadow) very likely did anything not listed here,
and the contributors below worked on the upstream project.

- Reese
    - [Slaurent](https://github.com/slaurent22) - Pictures of his lovely cat 
- Contributions
    - [Hien Ngo](https://github.com/hien-ngo29) 
      - RPM build in the workflow
      - Nicer info page icons
      - Fix dialog positions
    - [FabBeyond](https://github.com/FabBeyond) 
      - Profile icon switching
      - "Show installed mods on top" setting
      - Bug fixes
      - Hover Color Change & Profile Loading fixes
    - [jakobhellermann](https://github.com/jakobhellermann)
      - Fix game startup on macOS when arch or sh are shadowed
      - Improved error showcasing
      - Fix profile creation crash when no icon is selected
    - [elijw](https://github.com/elijw)
      - Fixing [#35](https://github.com/Nix-main/Cogfly/issues/35)
      - Fixed an issue where profiles could be created before BepInEx was available
      - Cleaned up some swing UI behavior
</details>

## License
PoisonCogfly is licensed under the [GNU GPL v3](LICENSE). The bundled downloader in `tools/` is built on a modified
copy of [DepotDownloader](https://github.com/SteamRE/DepotDownloader), which is licensed under GPL-2.0 only;
see [tools/README.md](tools/README.md) and [tools/DepotDownloader/NOTICE.md](tools/DepotDownloader/NOTICE.md).

## Logo
The Poison Cogfly artwork is © Team Cherry and is used here as fan art; it is not covered by this project's license.
