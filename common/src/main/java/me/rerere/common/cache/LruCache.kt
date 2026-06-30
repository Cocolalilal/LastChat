package me.rerere.common.cache

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class LruCache<K, V>(
    private val capacity: Int,
    private val store: CacheStore<K, V>,
    private val deleteOnEvict: Boolean = false,
    preloadFromStore: Boolean = false,
    private val expireAfterWriteMillis: Long? = null
) where K : Any {
    private val state = AtomicReference(CacheState<K, V>())

    init {
        if (preloadFromStore) {
            try {
                val all = store.loadAllEntries()
                val now = now()
                val entries = mutableMapOf<K, CacheEntry<V>>()
                val order = mutableListOf<K>()
                for ((k, entry) in all) {
                    if (!entry.isExpired(now)) {
                        entries[k] = entry
                        order += k
                    } else {
                        runCatching { store.remove(k) }
                    }
                    if (entries.size >= capacity) break
                }
                state.store(CacheState(entries = entries, order = order))
            } catch (_: Exception) {
            }
        }
    }

    fun get(key: K): V? {
        while (true) {
            val current = state.load()
            val entry = current.entries[key] ?: break
            if (entry.isExpired(now())) {
                if (state.compareAndSet(current, current.remove(key))) {
                    runCatching { store.remove(key) }
                    return null
                }
            } else {
                if (state.compareAndSet(current, current.touch(key))) {
                    return entry.value
                }
            }
        }
        val entry = store.loadEntry(key)
        if (entry != null) {
            return if (!entry.isExpired(now())) {
                putInMemory(key, entry)
                entry.value
            } else {
                runCatching { store.remove(key) }
                null
            }
        }
        return null
    }

    fun put(key: K, value: V) = put(key, value, expireAfterWriteMillis)

    fun put(key: K, value: V, ttlMillis: Long?) {
        val entry = CacheEntry(value = value, expiresAt = ttlMillis?.let { now() + it })
        putInMemory(key, entry)
        try {
            store.saveEntry(key, entry)
        } catch (_: Exception) {
        }
    }

    fun remove(key: K) {
        removeFromMemory(key)
        try {
            store.remove(key)
        } catch (_: Exception) {
        }
    }

    fun clear() {
        state.store(CacheState())
        try {
            store.clear()
        } catch (_: Exception) {
        }
    }

    fun containsKey(key: K): Boolean {
        val inMem = get(key) != null
        if (inMem) return true
        val entry = store.loadEntry(key)
        if (entry != null && !entry.isExpired(now())) return true
        if (entry != null) runCatching { store.remove(key) }
        return false
    }

    fun size(): Int = state.load().entries.size

    fun keysInMemory(): Set<K> {
        val current = state.load()
        val now = now()
        return current.order
            .filter { key -> current.entries[key]?.isExpired(now) == false }
            .toSet()
    }

    private fun putInMemory(key: K, entry: CacheEntry<V>) {
        while (true) {
            val current = state.load()
            val (next, evicted) = current.put(key, entry, capacity)
            if (state.compareAndSet(current, next)) {
                if (deleteOnEvict) {
                    evicted.forEach { evictedKey ->
                        runCatching { store.remove(evictedKey) }
                    }
                }
                return
            }
        }
    }

    private fun removeFromMemory(key: K) {
        while (true) {
            val current = state.load()
            val next = current.remove(key)
            if (current === next || state.compareAndSet(current, next)) return
        }
    }
}

private data class CacheState<K : Any, V>(
    val entries: Map<K, CacheEntry<V>> = emptyMap(),
    val order: List<K> = emptyList()
) {
    fun touch(key: K): CacheState<K, V> {
        if (key !in entries) return this
        return copy(order = order.filterNot { it == key } + key)
    }

    fun remove(key: K): CacheState<K, V> {
        if (key !in entries) return this
        return CacheState(
            entries = entries - key,
            order = order.filterNot { it == key }
        )
    }

    fun put(key: K, entry: CacheEntry<V>, capacity: Int): Pair<CacheState<K, V>, List<K>> {
        val nextEntries = entries + (key to entry)
        val nextOrder = order.filterNot { it == key } + key
        val overflow = (nextEntries.size - capacity).coerceAtLeast(0)
        if (overflow == 0) {
            return CacheState(nextEntries, nextOrder) to emptyList()
        }
        val evicted = nextOrder.take(overflow)
        return CacheState(
            entries = nextEntries - evicted.toSet(),
            order = nextOrder.drop(overflow)
        ) to evicted
    }
}

private fun now(): Long = System.currentTimeMillis()

