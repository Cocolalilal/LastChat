package me.rerere.search

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.search.SearchResult.SearchResultItem
import me.rerere.search.SearchService.Companion.httpClient
import me.rerere.search.SearchService.Companion.json
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val TAG = "NanoGPTSearchService"

object NanoGPTSearchService : SearchService<SearchServiceOptions.NanoGPTOptions> {
    override val name: String = "NanoGPT"

    @Composable
    override fun Description() {
        val urlHandler = LocalUriHandler.current
        TextButton(
            onClick = {
                urlHandler.openUri("https://nano-gpt.com")
            }
        ) {
            Text(stringResource(R.string.click_to_get_api_key))
        }
    }

    override val parameters: InputSchema?
        get() = InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "search query")
                })
                put("outputType", buildJsonObject {
                    put("type", "string")
                    put("description", "output format (one of `searchResults`, `sourcedAnswer`, `structured`)")
                    put("enum", buildJsonArray {
                        add("searchResults")
                        add("sourcedAnswer")
                        add("structured")
                    })
                })
                put("includeImages", buildJsonObject {
                    put("type", "boolean")
                    put("description", "whether to include image results")
                })
            },
            required = listOf("query")
        )

    override val scrapingParameters: InputSchema?
        get() = InputSchema.Obj(
            properties = buildJsonObject {
                put("urls", buildJsonObject {
                    put("type", "array")
                    put("description", "list of URLs to scrape")
                    put("items", buildJsonObject {
                        put("type", "string")
                    })
                })
            },
            required = listOf("urls")
        )

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.NanoGPTOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")
            val outputType = params["outputType"]?.jsonPrimitive?.contentOrNull ?: "searchResults"
            val includeImages = params["includeImages"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false

            val body = buildJsonObject {
                put("query", query)
                put("depth", serviceOptions.depth.ifEmpty { "standard" })
                put("outputType", outputType)
                put("includeImages", includeImages)
            }

            val request = Request.Builder()
                .url("https://nano-gpt.com/api/web")
                .post(body.toString().toRequestBody())
                .addHeader("x-api-key", serviceOptions.apiKey)
                .addHeader("Content-Type", "application/json")
                .build()
                
            val response = httpClient.newCall(request).await()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: error("Empty response body")
                val responseJson = json.parseToJsonElement(bodyStr).jsonObject
                
                when (outputType) {
                    "searchResults" -> {
                        val data = responseJson["data"]?.jsonArray ?: error("No data in response")
                        val items = data.mapNotNull { item ->
                            val itemObj = item.jsonObject
                            val type = itemObj["type"]?.jsonPrimitive?.contentOrNull ?: "text"
                            
                            if (type == "text") {
                                SearchResultItem(
                                    title = itemObj["title"]?.jsonPrimitive?.contentOrNull ?: "",
                                    url = itemObj["url"]?.jsonPrimitive?.contentOrNull ?: "",
                                    text = itemObj["content"]?.jsonPrimitive?.contentOrNull ?: ""
                                )
                            } else {
                                null // Skip image results for now
                            }
                        }
                        
                        SearchResult(items = items)
                    }
                    "sourcedAnswer" -> {
                        val data = responseJson["data"]?.jsonObject ?: error("No data in response")
                        val answer = data["answer"]?.jsonPrimitive?.contentOrNull ?: ""
                        val sources = data["sources"]?.jsonArray ?: buildJsonArray { }
                        
                        val items = sources.map { source ->
                            val sourceObj = source.jsonObject
                            SearchResultItem(
                                title = sourceObj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                                url = sourceObj["url"]?.jsonPrimitive?.contentOrNull ?: "",
                                text = sourceObj["snippet"]?.jsonPrimitive?.contentOrNull ?: ""
                            )
                        }
                        
                        SearchResult(answer = answer, items = items)
                    }
                    else -> {
                        // For structured output, convert to search results as best as possible
                        SearchResult(items = emptyList())
                    }
                }
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                error("Search failed #${response.code}: $errorBody")
            }
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.NanoGPTOptions
    ): Result<ScrapedResult> = withContext(Dispatchers.IO) {
        runCatching {
            val urlsArray = params["urls"]?.jsonArray ?: error("urls is required")
            val urls = urlsArray.map { it.jsonPrimitive.content }
            
            if (urls.isEmpty()) {
                error("At least one URL is required")
            }

            val body = buildJsonObject {
                put("urls", buildJsonArray {
                    urls.forEach { add(it) }
                })
            }

            val request = Request.Builder()
                .url("https://nano-gpt.com/api/scrape-urls")
                .post(body.toString().toRequestBody())
                .addHeader("x-api-key", serviceOptions.apiKey)
                .addHeader("Content-Type", "application/json")
                .build()
                
            val response = httpClient.newCall(request).await()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: error("Empty response body")
                val responseJson = json.parseToJsonElement(bodyStr).jsonObject
                val results = responseJson["results"]?.jsonArray ?: error("No results in response")
                
                val scrapedUrls = results.map { result ->
                    val resultObj = result.jsonObject
                    ScrapedResultUrl(
                        url = resultObj["url"]?.jsonPrimitive?.contentOrNull ?: "",
                        content = resultObj["content"]?.jsonPrimitive?.contentOrNull ?: "",
                        metadata = resultObj["metadata"]?.jsonObject?.let { meta ->
                            ScrapedResultMetadata(
                                title = meta["title"]?.jsonPrimitive?.contentOrNull,
                                description = meta["description"]?.jsonPrimitive?.contentOrNull,
                                language = meta["language"]?.jsonPrimitive?.contentOrNull
                            )
                        }
                    )
                }
                
                ScrapedResult(urls = scrapedUrls)
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                error("Scraping failed #${response.code}: $errorBody")
            }
        }
    }
}
