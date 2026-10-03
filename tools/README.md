# tools/

- `DepotDownloader/` — modified copy of SteamRE's DepotDownloader, **GPL-2.0-only**. See its `NOTICE.md` and `LICENSE`.
- `CogflyDownloader/` — console front-end that references `DepotDownloader` as a library. Because it is linked
  with GPL-2.0 code, the resulting `CogflyDownloader` binary is a combined work distributable under GPL-2.0.

Cogfly's Java application talks to `CogflyDownloader` only as a separate process, so the rest of the
repository stays under GPLv3 (see the top-level `LICENSE`).
