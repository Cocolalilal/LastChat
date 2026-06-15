package me.rerere.highlight.android

import android.content.Context
import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.QuickJSArray
import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.QuickJSObject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import me.rerere.highlight.Highlighter
import me.rerere.highlight.HighlightToken
import me.rerere.highlight.HighlightTokenSerializer
import me.rerere.highlight.R
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AndroidHighlighter(ctx: Context) : Highlighter {
    private val executor = Executors.newSingleThreadExecutor()

    init {
        executor.submit {
            QuickJSLoader.init()
            context
        }
    }

    private val script: String by lazy {
        ctx.resources.openRawResource(R.raw.prism).use {
            it.bufferedReader().readText()
        }
    }

    private val context: QuickJSContext by lazy {
        QuickJSContext.create().also {
            it.evaluate(script)
        }
    }

    private val highlightFn by lazy {
        context.globalObject.getJSFunction("highlight")
    }

    override suspend fun highlight(code: String, language: String): List<HighlightToken> =
        suspendCancellableCoroutine { continuation ->
            executor.submit {
                runCatching {
                    val result = highlightFn.call(code, language)
                    require(result is QuickJSArray) {
                        "highlight result must be an array"
                    }
                    val tokens = arrayListOf<HighlightToken>()
                    for (i in 0 until result.length()) {
                        when (val element = result[i]) {
                            is String -> tokens.add(
                                HighlightToken.Plain(
                                    content = element,
                                )
                            )

                            is QuickJSObject -> {
                                val json = element.stringify()
                                val token = format.decodeFromString<HighlightToken.Token>(
                                    HighlightTokenSerializer, json
                                )
                                tokens.add(token)
                            }

                            else -> error("Unknown type: ${element::class.java.name}")
                        }
                    }
                    result.release()
                    continuation.resume(tokens)
                }.onFailure {
                    it.printStackTrace()
                    if (continuation.isActive) {
                        continuation.resumeWithException(it)
                    }
                }
            }
        }

    override fun destroy() {
        context.destroy()
    }

    private companion object {
        val format: Json by lazy {
            Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            }
        }
    }
}
