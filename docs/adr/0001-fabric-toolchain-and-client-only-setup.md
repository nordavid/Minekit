# ADR 0001: Fabric toolchain and client-only project setup

- **Status:** Accepted
- **Date:** 2026-11-06
- **Phase:** 0 (technical validation)

## Context

MINEKIT targets Minecraft Java Edition 26.3 with Fabric and must run with no server-side component
(PROJECT_PLAN.md §3). The plan requires verified versions instead of versions copied from tutorials.

Sources checked on 2026-11-06:

| Source | Result |
|---|---|
| https://fabricmc.net/develop/ (version selector, 26.3) | `loader_version=0.20.1`, `loom_version=1.19-SNAPSHOT`, `fabric_api_version=0.165.2+26.3` |
| https://meta.fabricmc.net/v2/versions/game | `26.3` is the latest **stable** game version (26.4 is still in snapshots) |
| https://meta.fabricmc.net/v2/versions/loader | `0.20.1` is the latest stable loader |
| Fabric Maven, `net.fabricmc.fabric-loom` | latest release `1.19.3` (2026-10-22); `1.19-SNAPSHOT` last built the same day; `1.20.0` is alpha only |
| Fabric Maven, `fabric-api` | newest build for 26.3 is `0.165.2+26.3` |
| Official example mod, branch `26.3` | plugin `net.fabricmc.fabric-loom`, Gradle 9.8.2, Java 25, `implementation` for mod dependencies |
| Mojang version manifest, 26.3 | requires Java 25 (`java-runtime-epsilon`) |
| services.gradle.org | Gradle `9.8.2` is the current release |

Minecraft 26.x ships unobfuscated. Loom therefore no longer needs mappings or a remap step, and the
official template uses the non-remapping plugin ID `net.fabricmc.fabric-loom`.

## Decisions

1. **Toolchain:** Minecraft 26.3, Fabric Loader 0.20.1, Fabric API 0.165.2+26.3, Gradle 9.8.2 (wrapper with
   checksum verification), Java 25. All versions live in `gradle.properties`. `fabric.mod.json` gets its
   dependency ranges from there through `processResources`, so the versions are defined in one place.
2. **Loom pinned to release 1.19.3** instead of the recommended `1.19-SNAPSHOT`. The snapshot is a moving
   target, and the plan asks for reproducible builds (§13). On the decision date the release and the latest
   snapshot were published on the same day, so nothing is lost. When upgrading, re-check the version selector
   and move to the newest 1.19.x (or whatever line it recommends then).
3. **One `main` source set, no `splitEnvironmentSourceSets()`.** The split protects common code from
   accidentally using client-only classes. MINEKIT has no common or server code, so the split would only add
   an empty source set. This matches the package layout in PROJECT_PLAN.md §4.1.
4. **Client-only enforcement:** `"environment": "client"`, a single `client` entrypoint, no `main`/`server`
   entrypoint, no mixins, no networking. `ClientOnlyMetadataTest` checks the processed `fabric.mod.json`
   on every `gradle build`, so a regression fails the build.
5. **Fabric API is a declared dependency from the start**, as required by the plan, with the build version as
   the minimum. Phase 0 code does not call it yet. Phase 1 needs it for key bindings, screens and HUD.
6. **Java and Groovy DSL** as in the official template. No Kotlin and no extra Gradle plugins.
7. **Tests:** JUnit Jupiter 6.1.3 (EPL-2.0, maintained by the JUnit team, latest release on Maven Central).
   Test-only, not shipped in the mod JAR.
8. **Launcher integration:** `installToModrinth` copies the JAR into a folder that is set per machine
   (`minekit.modsDir`). No launcher path is committed.

## Consequences

- Building requires a JDK 25 for Gradle (`JAVA_HOME`). The README documents this.
- Loom upgrades are deliberate: change `loom_version` after checking the version selector.
- Loom still creates a `runServer` task. Running it starts a Fabric dedicated server on which Fabric Loader
  skips MINEKIT because of its client environment. It is not part of the workflow and is not documented as one.
- If MINEKIT ever needs code that also runs on a server, revisit decision 3. The plan currently rules that out.
