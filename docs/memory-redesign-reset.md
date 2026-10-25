# Memory redesign reset — 25 October 2026

## Working baseline

The rejected memory-v4 implementation remains on the separate, unmerged local
`feat/memory-continuity-v4` branch at `bfafd527`. It is not merged into dev.
The working `lastchat_dev` branch starts from the latest fetched
`origin/LastChat_dev`, `e7b0d276` (110 commits beyond the old main-branch base).
Upstream memory and UI remain the working baseline, not the rejected screens.

## Explicitly requested preparation

Dev already contains LiteRT-LM 0.18's EmbeddingGemma 2 Text runtime. The obsolete
EmbeddingGemma 300M catalog entry, TFLite runtime path and AI Edge RAG dependency
are removed. The supported bundled embedding model is EmbeddingGemma 2 Text 270M.

The existing pinned artifact was verified against Hugging Face metadata:

- Repository: `litert-community/embeddinggemma-2-text-270m-litert-lm`
- Revision: `9be6e8b90982095dc05c2bd162e4b954ee4dbac7`
- File: `embeddinggemma-2-text-270m.litertlm`
- Bytes: 164,626,432
- SHA-256: `2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb`
- License: Apache 2.0; self-contained tokenizer; 768 dimensions; 8K input window.

Retired installs are excluded from model selection. Existing selections move to
the new local model only if it is already installed; otherwise embeddings become
explicitly disabled. This applies to per-character overrides too and must not
select a cloud provider implicitly. The replacement retains its own model UUID,
so the existing index mismatch checks do not reuse old vectors just because both
models output 768 dimensions. No chat, canonical memory or downloaded file is
automatically erased. Old installed files remain manageable in local storage.

## Product decisions for the next design review

These are requirements, not a claim that the future memory system is implemented:

- Exactly two modes: **Basic** and **Advanced**.
- Basic has a **Dreaming on/off toggle**, rather than a third memory mode.
- Basic's interface should follow the current RikkaHub topic-memory experience,
  without Markdown-file framing. Review its actual screens before making sketches.
- Advanced uses the supported local EmbeddingGemma 2 Text model and must have a
  meaningfully different experience and behavior. Its exact structure still needs
  agreement; the previous graph renderer and screens are rejected.
- No arbitrary quota on automatic memory requests. Scheduling, pauses and bounded
  retries must not be presented as a daily usage allowance.
- Memory-agent edits stay in the existing UI; no separate agent chat.
- Preserve the previously discussed time, provenance, scope, privacy, editing,
  source-change, branching and migration requirements in the eventual design.

Discuss daily-chat behavior and failure examples first. Then review separate
Basic and Advanced layouts plus interactive transitions against current LastChat
screens. Obtain design approval before rebuilding the memory feature. A green
build or unit-test count does not demonstrate good memory quality or good UI.

## Test-build data boundary

The rejected test APK used Room schema 41. Current upstream dev uses schema 39.
Switching source branches does not downgrade an installed application's data.
Do not ship a replacement APK over that test vault without a reviewed rollback
or import path. Keep the previous verified backup and the test data intact.
No database downgrade or memory-v4 erasure migration is introduced in this reset.

## Preparation verification

All 50 local-model host tests and 14 provider/settings normalization tests pass.
The checks cover retired catalog entries, offline cached catalogs, supported ABIs,
per-character disabled selections, preservation of explicit choices and migration
to a separately identified replacement. App Kotlin compilation also passed.

LiteRT-LM 0.18's JVM artifacts require Java 21 for host test execution. This
environment used its existing JDK 17 compiler and a Java 21 test launcher; the
Android bytecode target remains 17. No live native embedding run or new APK is
claimed. Graphify refresh was attempted, but the executable is unavailable.
