package me.rerere.rikkahub.data.codex

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Collections
import java.util.concurrent.TimeUnit

private const val DOH_CLIENT_TIMEOUT_MS = 3_000L
private const val RACING_DNS_TIMEOUT_MS = 4_000L

/**
 * DNS resolver for Codex traffic that races system DNS and public DNS-over-HTTPS
 * (Cloudflare, Google, AliDNS) concurrently with dual-stack IPv4/IPv6 bootstrap hosts.
 * Returns the first successful resolution, avoiding long serial timeouts.
 */
fun createCodexDnsResolver(): Dns {
    val cloudflare = DnsOverHttps.Builder()
        .client(newDohClient())
        .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByName("1.1.1.1"),
            InetAddress.getByName("1.0.0.1"),
            InetAddress.getByName("2606:4700:4700::1111"),
            InetAddress.getByName("2606:4700:4700::1001"),
        )
        .build()
    val google = DnsOverHttps.Builder()
        .client(newDohClient())
        .url("https://dns.google/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByName("8.8.8.8"),
            InetAddress.getByName("8.8.4.4"),
            InetAddress.getByName("2001:4860:4860::8888"),
            InetAddress.getByName("2001:4860:4860::8844"),
        )
        .build()
    val alidns = DnsOverHttps.Builder()
        .client(newDohClient())
        .url("https://dns.alidns.com/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByName("223.5.5.5"),
            InetAddress.getByName("223.6.6.6"),
            InetAddress.getByName("2400:3200::1"),
            InetAddress.getByName("2400:3200:baba::1"),
        )
        .build()
    return RacingDns(listOf(Dns.SYSTEM, cloudflare, google, alidns), timeoutMs = RACING_DNS_TIMEOUT_MS)
}

private fun newDohClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(DOH_CLIENT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    .readTimeout(DOH_CLIENT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    .fastFallback(true)
    .build()

internal class RacingDns(
    private val resolvers: List<Dns>,
    private val timeoutMs: Long = RACING_DNS_TIMEOUT_MS,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        if (resolvers.isEmpty()) {
            throw UnknownHostException("No DNS resolvers configured for $hostname")
        }

        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val deferredResult = CompletableDeferred<List<InetAddress>>()

        try {
            return runBlocking(Dispatchers.IO) {
                withTimeout(timeoutMs) {
                    val jobs = resolvers.map { resolver ->
                        launch {
                            try {
                                val addresses = resolver.lookup(hostname)
                                if (addresses.isNotEmpty()) {
                                    deferredResult.complete(addresses)
                                } else {
                                    errors.add(UnknownHostException("Resolver returned empty address list for $hostname"))
                                }
                            } catch (t: Throwable) {
                                errors.add(t)
                            }
                        }
                    }

                    val monitorJob = launch {
                        jobs.joinAll()
                        if (!deferredResult.isCompleted) {
                            val error = UnknownHostException("All ${resolvers.size} DNS resolvers failed for $hostname")
                            errors.forEach { error.addSuppressed(it) }
                            deferredResult.completeExceptionally(error)
                        }
                    }

                    try {
                        deferredResult.await()
                    } finally {
                        jobs.forEach { it.cancel() }
                        monitorJob.cancel()
                    }
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            val error = UnknownHostException("DNS resolution for $hostname timed out after ${timeoutMs}ms")
            errors.forEach { error.addSuppressed(it) }
            error.addSuppressed(timeout)
            throw error
        } catch (e: UnknownHostException) {
            if (e.suppressed.isEmpty() && errors.isNotEmpty()) {
                errors.forEach { e.addSuppressed(it) }
            }
            throw e
        } catch (t: Throwable) {
            var current: Throwable? = t
            while (current != null) {
                if (current is UnknownHostException) {
                    if (current.suppressed.isEmpty() && errors.isNotEmpty()) {
                        errors.forEach { current.addSuppressed(it) }
                    }
                    throw current
                }
                current = current.cause
            }
            val error = UnknownHostException("Failed to resolve $hostname: ${t.message}").apply {
                initCause(t)
            }
            errors.forEach { if (it !== error && it !== t) error.addSuppressed(it) }
            throw error
        }
    }
}

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
