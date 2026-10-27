# Grounding and native component map

Checked 27 October 2026. No app feature implementation is included in this review.

## Upstream RikkaHub reference

Pinned commit: `a7d1e6efb50bb781e49bc26c5145640ad6a7ea97`.
Prefix: https://github.com/rikkahub/rikkahub/blob/a7d1e6efb50bb781e49bc26c5145640ad6a7ea97/

| File under app/src/main/java/me/rerere/rikkahub/ | Evidence |
| --- | --- |
| data/ai/prompts/MemoryPrompts.kt:116,222 | Explicit requested writes; background learning; profile/preferences and directory/read retrieval |
| data/ai/MemoryConsolidationScheduler.kt:111 | Idle/turn scheduling, pending work, failure behavior |
| data/ai/MemoryConsolidator.kt:294,360 | Model fallback; seven-day discovery, initial 12 turns, timestamp cursor |
| data/ai/tools/MemoryTools.kt:86 | Read/list/write/replace/append/delete; if_version tool contract |
| data/repository/MemoryRepository.kt:117 | Hash version, serialized writes, 48 KiB note bound |
| data/db/entity/MemoryFileEntity.kt | Note owner/path/content/date without source revision support |
| ui/pages/assistant/detail/AssistantMemoryPage.kt:490 | You, Topics, Areas, People and Dreaming state |
| ui/pages/memory/MemoryFileVM.kt:52 | Manual editor saves bypass version checks |

Read the pinned source before changing Basic behavior. This is a behavioral reference,
not a recommendation to copy its file framing, icons, permissions or history shortcuts.
Existing licensing obligations remain applicable to any later source reuse.

## Existing LastChat components

Baseline: `db906721` on `lastchat_dev`; upstream base `e7b0d276`.
Paths below are under app/src/main/java/me/rerere/rikkahub/ unless noted otherwise.

| UI requirement | Reuse/reference |
| --- | --- |
| Overlay shell, surface/height | ui/activity/AssistantOverlayScreen.kt:530; use presentation only, not its persisted-chat VM |
| Real composer capsule/actions | ui-core/src/commonMain/kotlin/me/rerere/rikkahub/ui/components/chat/LastChatComposerShell.kt:43 |
| Status/reasoning affordance | ui/components/chat/ActivityPill.kt:104,477 |
| Expandable work history | ui/components/chat/ActivityTimelineSheet.kt:325 |
| Tool details | ui/components/message/ChatMessageTools.kt:87 |
| Floating content fade | ui/modifier/FadeEdges.kt |
| Grouped list shapes | ui/components/ui/PhysicsSwipeToDelete.kt:81; shape vocabulary only, not compulsory swipe behavior |
| Google Sans Flex | ui/theme/Type.kt and res/font/google_sans_flex.ttf |
| Motion and haptics | ui/motion/MotionPolicy.kt; ui/hooks/PremiumHaptics.kt |

Use the user's actual screen composition as the visual contract. No V1/home statistics,
agent avatar or separate agent conversation is implied by this component map. Keep the
popup under bounded height; the existing overlay explicitly guards infinite-height
scroll layouts at AssistantOverlayScreen.kt:334. Tool-only loading must remain visible.

## Context/time limits in this checkout

`data/model/Conversation.kt:32` has one assistant owner and no full persona/world/scene
participant runtime. `PreferencesModels.kt:521` includes an assistant fallback that is
not safe as an authorization boundary. Nickname replacement in `Assistant.kt:318` is
not a multi-persona permission model. `ai/.../ui/MessageUtils.kt:23` stores message time
as timezone-less LocalDateTime; historical offsets must not be invented. Propose normal
character-wide continuity and explicit story/context separation. Full future group and
persona knowledge rules require their own implementation and review.
