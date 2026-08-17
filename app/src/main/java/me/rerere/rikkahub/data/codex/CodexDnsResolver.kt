package me.rerere.rikkahub.data.codex

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

private const val DOH_CLIENT_TIMEOUT_MS = 8_000L

/**
 * DNS resolver for Codex traffic that falls back to DNS-over-HTTPS when the
 * system resolver fails (e.g. polluted/broken DNS, private DNS misconfig).
 * Tries: system DNS -> Google DoH -> AliDNS DoH (China-reachable).
 */
fun createCodexDnsResolver(): Dns {
    val cloudflare = DnsOverHttps.Builder()
        .client(newDohClient())
        .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByName("1.1.1.1"),
            InetAddress.getByName("1.0.0.1"),
        )
        .build()
    val google = DnsOverHttps.Builder()
        .client(newDohClient())
        .url("https://dns.google/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByName("8.8.8.8"),
            InetAddress.getByName("8.8.4.4"),
        )
        .build()
    val alidns = DnsOverHttps.Builder()
        .client(newDohClient())
        .url("https://dns.alidns.com/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByName("223.5.5.5"),
            InetAddress.getByName("223.6.6.6"),
        )
        .build()
    return FallbackDns(Dns.SYSTEM, FallbackDns(cloudflare, FallbackDns(google, alidns)))
}

private fun newDohClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(DOH_CLIENT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    .readTimeout(DOH_CLIENT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    .fastFallback(true)
    .build()

internal class FallbackDns(
    private val primary: Dns,
    private val fallback: Dns,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val primaryResult = runCatching { primary.lookup(hostname) }
        val addresses = primaryResult.getOrNull()
        if (!addresses.isNullOrEmpty()) {
            return addresses
        }
        val primaryError = primaryResult.exceptionOrNull()
        try {
            return fallback.lookup(hostname)
        } catch (fallbackError: Throwable) {
            val finalError = if (fallbackError is UnknownHostException) {
                fallbackError
            } else {
                UnknownHostException("Failed to resolve $hostname: ${fallbackError.message}").apply {
                    initCause(fallbackError)
                }
            }
            if (primaryError != null && primaryError !== finalError) {
                finalError.addSuppressed(primaryError)
            }
            throw finalError
        }
    }
}
