package me.rerere.highlight.android

import android.content.Context
import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.QuickJSArray
import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.QuickJSObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import me.rerere.highlight.Highlighter
import me.rerere.highlight.HighlightToken
import me.rerere.highlight.HighlightTokenSerializer
import me.rerere.highlight.R

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidHighlighter(ctx: Context) : Highlighter {
    private val dispatcher = Dispatchers.Default.limitedParallelism(1)

    private val script: String by lazy {
        ctx.resources.openRawResource(R.raw.prism).use {
            it.bufferedReader().readText()
        }
    }

    private val contextLazy = lazy {
        QuickJSLoader.init()
        QuickJSContext.create().also {
            it.evaluate(script)
        }
    }
    private val context: QuickJSContext by contextLazy

    private val highlightFn by lazy {
        context.globalObject.getJSFunction("highlight")
    }

    private val cache = android.util.LruCache<Pair<String, String>, List<HighlightToken>>(128)

    override suspend fun highlight(code: String, language: String): List<HighlightToken> =
        withContext(dispatcher) {
            val cacheKey = code to language
            val cached = cache.get(cacheKey)
            if (cached != null) return@withContext cached

            try {
                val result = highlightFn.call(code, language)
                require(result is QuickJSArray) {
                    "highlight result must be an array"
                }
                try {
                    val tokens = arrayListOf<HighlightToken>()
                    for (i in 0 until result.length()) {
                        when (val element = result[i]) {
                            is String -> tokens.add(
                                HighlightToken.Plain(
                                    content = element,
                                )
                            )

                            is QuickJSObject -> {
                                try {
                                    val json = element.stringify()
                                    val token = format.decodeFromString<HighlightToken.Token>(
                                        HighlightTokenSerializer, json
                                    )
                                    tokens.add(token)
                                } finally {
                                    element.release()
                                }
                            }

                            else -> error("Unknown type: ${element?.let { it::class.qualifiedName } ?: "null"}")
                        }
                    }
                    cache.put(cacheKey, tokens)
                    tokens
                } finally {
                    result.release()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                listOf(HighlightToken.Plain(content = code))
            }
        }

    override fun destroy() {
        runBlocking(dispatcher) {
            cache.evictAll()
            if (contextLazy.isInitialized()) {
                context.destroy()
            }
        }
    }

    private companion object {
        val format: Json by lazy {
            Json {
                ignoreUnknownKeys = true
            }
        }
    }
}
