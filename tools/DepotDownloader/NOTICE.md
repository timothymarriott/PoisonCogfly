# DepotDownloader (modified)

This directory contains a modified copy of **DepotDownloader** by the SteamRE Team.

- Upstream: https://github.com/SteamRE/DepotDownloader
- Based on: tag `DepotDownloader_3.4.0` (commit `c553ef4d60c00a4f5fd16c9fe017f569001589ff`, 2025-05-09)
- License: **GNU GPL version 2** (see [LICENSE](LICENSE)), unmodified from upstream.

The rest of Cogfly is GPLv3. This directory (and anything that links against it, see
`../README.md`) remains under GPLv2 only; it is not relicensed.

## Modifications (Cogfly project, 2026-10-03)

- `Bridge.cs` (new): line-based JSON protocol on stdout/stdin so Cogfly can drive the downloader
  and answer Steam Guard prompts.
- `Program.cs`: the command-line `Main` was replaced by `AppDownloader.Download(...)`, a library entry point.
- `ConsoleAuthenticator.cs`, `Steam3Session.cs`: console prompts for Steam Guard codes were replaced with
  `Bridge.Request(...)`; some console logging was removed; `includechildren` set on published-file requests;
  HTTP client factory call adjusted.
- `AccountSettingsStore.cs`: uses a host-provided `ConfigDir` and plain files instead of IsolatedStorage.
- `DepotConfigStore.cs`: file-backed loading removed.
- `ContentDownloader.cs`: install directory always taken from the config; `CreateDirectories` takes the
  manifest id instead of a depot version; added a set of supported workshop file types.
- `DepotDownloader.csproj`: output type changed from `Exe` to `Library`; application icon and LICENSE
  copy-to-output removed; package versions bumped (SteamKit2 3.3.1, protobuf-net 3.2.56, QRCoder 1.8.0,
  CsWin32 0.3.269).

The authoritative record of changes is the git history of this repository; diff against the upstream tag above.
