# Bolt's Journal

## 2024-05-24 - Optimizing Compose Recomposition in Lists
**Learning:** Passing large, frequently changing data objects (like `Conversation` which updates on every token) to list items (`ChatMessage`) causes massive unnecessary recomposition. Even if `LazyColumn` is used, visible items recompose because the data object identity changes. This also triggers expensive O(N) calculations inside every item (e.g. `conversation.currentMessages.indexOf(message)`).
**Action:** Pass only primitive/stable data (e.g., `previousRole`, `isLast`) to list items. Derive necessary context in the parent composable during the iteration loop. For callbacks depending on changing state, use `rememberUpdatedState` to create stable lambdas to prevent recreating them on every parent recomposition.

## 2024-05-24 - Room Composite Indices & Sort Order
**Learning:** Composite indices (e.g., `["A", "B"]`) are ineffective for queries that sort by the second column (`ORDER BY B`) without filtering on the first. This caused full table scans in `ConversationEntity` despite having an index. Also, missing foreign key indices (`MemoryEntity.assistant_id`) causes scan-on-filter.
**Action:** Always verify that indices match the query's `WHERE` and `ORDER BY` structure exactly. For global sorting, ensure the sort column is the *prefix* of an index.
