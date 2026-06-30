package me.rerere.rikkahub.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.rikkahub.data.model.Sponsor
import me.rerere.rikkahub.utils.JsonInstant

class SponsorAPI private constructor(
    private val httpClient: PlatformHttpClient,
) {
    suspend fun getSponsors(): List<Sponsor> = withContext(Dispatchers.IO) {
        val response = httpClient.execute(
            PlatformHttpRequest(
                method = "GET",
                url = "https://sponsors.rikka-ai.com/sponsors",
            )
        )
        if (response.statusCode !in 200..299) {
            error("Sponsor request failed: HTTP ${response.statusCode}")
        }
        JsonInstant.decodeFromString(
            deserializer = ListSerializer(Sponsor.serializer()),
            string = response.body.decodeToString(),
        )
    }

    companion object {
        fun create(httpClient: PlatformHttpClient): SponsorAPI {
            return SponsorAPI(httpClient)
        }
    }
}
