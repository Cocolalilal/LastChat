# LastChat iOS Port + One-Project Unification Plan (AI-Agent Ready)

## Executive Assessment

**Difficulty: High (8.5/10).**

LastChat is currently Android-centric in build system, storage, background services, haptics, and feature integrations (Room, WorkManager, Android notification/app control, Chaquopy, QuickJS Android bindings). A direct "compile for iOS" path is not realistic without staged modularization.

## Recommended Target Architecture (Best Path)

### Chosen strategy: **Native UI per platform + shared Kotlin Multiplatform core**

1. **Shared KMP core** for:
   - conversation domain logic
   - provider clients + streaming parser logic
   - memory policies and ranking logic
   - serialization models and prompt pipelines
2. **Android app** keeps Compose UI and existing platform integrations.
3. **iOS app** uses SwiftUI for shell/UI + haptics, calls shared core via generated Kotlin framework.
4. **Single mono-repo** with one CI pipeline, platform-specific apps, and one shared domain engine.

### Why this is best for LastChat

- Preserves Android velocity while reducing duplicate logic.
- iOS gets native interactions (critical for fidget-toy feel and premium haptics).
- Limits risky rewrite surface (compared to a full Swift rewrite or full Compose Multiplatform UI migration).
- Fits AI-agent iterative delivery via small, verifiable extraction PRs.

## Migration Options Compared

| Option | Difficulty | Risk | UX fit | Time-to-first-iOS | Recommendation |
|---|---:|---:|---:|---:|---|
| Full native Swift rewrite | 9/10 | High | Excellent | Slow | ❌ No |
| Compose Multiplatform UI + shared logic | 8/10 | Medium/High | Medium (haptics/motion parity work) | Medium | ⚠️ Maybe later |
| **Shared KMP core + native UIs** | **8.5/10** | **Medium** | **Excellent** | **Fastest safe path** | ✅ **Yes** |

## Baseline Refactoring Started in This PR

To begin KMP extraction safely, logging in provider/network code was decoupled from `android.util.Log` behind `AppLogger`.

- New lightweight logger: `ai/.../util/AppLogger.kt`
- Provider + proxy code now calls `AppLogger` instead of Android Log.

This lowers Android coupling and allows these files to move to common source sets with less churn.

## Agent-Optimized Program Plan

## Phase 0 — Inventory & Guards (1-2 days)

### Objectives
- Build a **platform dependency map** (Android-only APIs per package).
- Freeze behavior with parsing and provider protocol tests.

### Agent Tasks
1. Generate API import inventory (`android.*`, `androidx.*`, `java.io.File`, Room, WorkManager, etc.).
2. Tag packages into `portable`, `portable-with-adapter`, `android-only`.
3. Add golden tests for:
   - SSE/event parsing
   - JSON model mapping
   - provider request builders

### Definition of Done
- Dependency inventory committed.
- Core protocol tests green and deterministic.

## Phase 1 — Shared-Core Skeleton (2-4 days)

### Objectives
- Create `shared-core` KMP module (`commonMain`, `androidMain`, `iosMain`).
- Move only **pure logic** first.

### Agent Tasks
1. Create module + CI compile checks for Android + iOS simulator target.
2. Introduce boundary interfaces:
   - `PlatformLogger`
   - `KeyValueStore`
   - `ClockProvider`
   - `SecureKeyStore`
3. Move serialization models + request/response parsers.

### Definition of Done
- Shared module compiles for both targets.
- No Android imports in moved files.

## Phase 2 — Network + Streaming Core (4-7 days)

### Objectives
- Move provider HTTP contracts and stream parsers to shared.
- Keep platform HTTP engines behind adapters.

### Agent Tasks
1. Build provider client interfaces in shared.
2. Implement adapters:
   - Android: OkHttp adapter
   - iOS: Ktor Darwin/NSURLSession adapter
3. Keep custom headers/proxy logic as adapter features.

### Definition of Done
- Same test vectors pass on Android/iOS for chunk streaming and usage parsing.

## Phase 3 — Memory/Data Architecture Split (4-8 days)

### Objectives
- Separate domain memory logic from Room entities/DAO glue.

### Agent Tasks
1. Define shared repositories (interfaces + domain models).
2. Android implementation remains Room.
3. iOS implementation starts with SQLite/GRDB parity schema.
4. Keep embedding sync invariant (entity + cache store).

### Definition of Done
- Domain memory tests pass in shared.
- Android implementation remains behavior-compatible.

## Phase 4 — Platform Feature Adapters (parallel track)

### Android-only features to gate
- app launch/tooling control
- notifications integration
- alarms/reminders
- Chaquopy Python integration
- Android QuickJS binding details

### Agent Tasks
1. Add capability flags in shared (`supportsDeviceControl`, `supportsPythonTooling`, etc.).
2. Hide unsupported tools on iOS gracefully.
3. Add stubs and analytics for unsupported action attempts.

## Phase 5 — iOS App Shell + UX Fidelity (3-6 weeks)

### Objectives
- SwiftUI app that mirrors IA/flows.
- Implement premium tactile behavior with iOS haptics.

### UX constraints mapping
- Press scale targets match Android (`0.85f` equivalent).
- Motion uses spring physics only.
- Haptic mappings:
  - Pop: impact light/rigid
  - Thud: heavy impact
  - Success: notification success

### Definition of Done
- Core chat flows functional end-to-end on iOS.
- Interaction feel signed off against Android references.

## Phase 6 — Unified CI/CD + Release Ops (2-4 days)

### Objectives
- One repo, one branch strategy, dual-platform quality gates.

### Agent Tasks
1. Add matrix CI:
   - Android assemble + unit tests
   - Shared common tests
   - iOS shared compile tests
2. Add generated API snapshots for provider contracts.
3. Add migration docs and rollback playbooks.

## Detailed Backlog Template for AI Agents

Use one issue per extraction unit:

```md
### Goal
Move <package/file set> to shared-core without behavior change.

### Inputs
- files:
- tests:
- risks:

### Constraints
- no android imports in shared
- preserve JSON field behavior
- preserve stream chunk semantics

### Validation
- android unit tests
- shared common tests
- contract snapshots unchanged

### Deliverables
- code diff
- migration note
- follow-up tasks
```

## Risk Register and Mitigations

1. **Streaming regressions**
   - Mitigation: golden SSE fixtures and cross-platform parser parity tests.
2. **Persistence drift (Room vs iOS store)**
   - Mitigation: schema contract tests and migration fixtures.
3. **Tool capability mismatch**
   - Mitigation: capability flags + graceful degradation.
4. **UX feel mismatch (haptics/spring)**
   - Mitigation: interaction checklist + side-by-side capture review.
5. **AI agent refactor entropy**
   - Mitigation: tiny PR slices, strict DoD, and ownership map.

## Suggested Team Topology for Agents

- **Planner Agent:** creates extraction DAG and ordering.
- **Refactor Agent:** moves one boundary at a time.
- **Test Agent:** writes/updates fixtures and contracts.
- **Release Agent:** updates CI and migration docs.

Each PR should touch only one logical boundary and include before/after dependency snapshots.

## Proposed Milestone Timeline

- M0 (Week 1): Inventory + tests + shared skeleton
- M1 (Week 2): Provider parsing and request builders moved
- M2 (Weeks 3-4): Streaming + memory domain split
- M3 (Weeks 5-6): iOS shell + core chat flows
- M4 (Week 7): hardening + beta rollout

## Success Metrics

- Shared-core line coverage >70% on moved packages.
- iOS chat p95 latency within +15% of Android baseline.
- Crash-free sessions ≥99.5% both platforms.
- Zero regression in provider contract snapshots.
