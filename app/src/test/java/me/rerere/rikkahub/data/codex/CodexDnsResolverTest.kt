package me.rerere.rikkahub.data.codex

import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class CodexDnsResolverTest {

    @Test
    fun `fallback dns returns primary addresses when primary succeeds`() {
        val primaryAddress = InetAddress.getByName("1.1.1.1")
        val primary = Dns { listOf(primaryAddress) }
        val fallback = Dns { error("Should not be called") }

        val resolver = FallbackDns(primary, fallback)
        val result = resolver.lookup("example.com")

        assertEquals(listOf(primaryAddress), result)
    }

    @Test
    fun `fallback dns uses fallback when primary throws UnknownHostException`() {
        val fallbackAddress = InetAddress.getByName("8.8.8.8")
        val primary = Dns { throw UnknownHostException("Primary failed") }
        val fallback = Dns { listOf(fallbackAddress) }

        val resolver = FallbackDns(primary, fallback)
        val result = resolver.lookup("example.com")

        assertEquals(listOf(fallbackAddress), result)
    }

    @Test
    fun `fallback dns uses fallback when primary throws SocketTimeoutException`() {
        val fallbackAddress = InetAddress.getByName("8.8.8.8")
        val primary = Dns { throw SocketTimeoutException("DoH timeout") }
        val fallback = Dns { listOf(fallbackAddress) }

        val resolver = FallbackDns(primary, fallback)
        val result = resolver.lookup("example.com")

        assertEquals(listOf(fallbackAddress), result)
    }

    @Test
    fun `fallback dns uses fallback when primary returns empty list`() {
        val fallbackAddress = InetAddress.getByName("8.8.8.8")
        val primary = Dns { emptyList() }
        val fallback = Dns { listOf(fallbackAddress) }

        val resolver = FallbackDns(primary, fallback)
        val result = resolver.lookup("example.com")

        assertEquals(listOf(fallbackAddress), result)
    }

    @Test
    fun `fallback dns throws UnknownHostException when all resolvers fail`() {
        val primary = Dns { throw SocketTimeoutException("Primary timeout") }
        val fallback = Dns { throw UnknownHostException("Fallback failed") }

        val resolver = FallbackDns(primary, fallback)
        try {
            resolver.lookup("example.com")
            fail("Expected UnknownHostException")
        } catch (e: UnknownHostException) {
            assertEquals("Fallback failed", e.message)
            assertEquals(1, e.suppressed.size)
        }
    }

    @Test
    fun `racing dns returns address when first resolver succeeds`() {
        val addr1 = InetAddress.getByName("1.1.1.1")
        val r1 = Dns { listOf(addr1) }
        val r2 = Dns {
            Thread.sleep(500)
            listOf(InetAddress.getByName("8.8.8.8"))
        }

        val resolver = RacingDns(listOf(r1, r2), timeoutMs = 2_000L)
        val result = resolver.lookup("example.com")

        assertEquals(listOf(addr1), result)
    }

    @Test
    fun `racing dns returns address from surviving resolver when one throws`() {
        val addr2 = InetAddress.getByName("8.8.8.8")
        val r1 = Dns { throw UnknownHostException("R1 failed") }
        val r2 = Dns { listOf(addr2) }

        val resolver = RacingDns(listOf(r1, r2), timeoutMs = 2_000L)
        val result = resolver.lookup("example.com")

        assertEquals(listOf(addr2), result)
    }

    @Test
    fun `racing dns returns address when fast resolver finishes before slow failing resolver`() {
        val addr2 = InetAddress.getByName("8.8.8.8")
        val r1 = Dns {
            Thread.sleep(800)
            throw SocketTimeoutException("R1 timeout")
        }
        val r2 = Dns { listOf(addr2) }

        val resolver = RacingDns(listOf(r1, r2), timeoutMs = 2_000L)
        val result = resolver.lookup("example.com")

        assertEquals(listOf(addr2), result)
    }

    @Test
    fun `racing dns throws UnknownHostException when all resolvers fail`() {
        val r1 = Dns { throw UnknownHostException("R1 error") }
        val r2 = Dns { throw SocketTimeoutException("R2 timeout") }

        val resolver = RacingDns(listOf(r1, r2), timeoutMs = 2_000L)
        try {
            resolver.lookup("example.com")
            fail("Expected UnknownHostException")
        } catch (e: UnknownHostException) {
            org.junit.Assert.assertTrue(e.suppressed.isNotEmpty())
        }
    }

    @Test
    fun `racing dns throws UnknownHostException on overall timeout`() {
        val r1 = Dns {
            Thread.sleep(500)
            listOf(InetAddress.getByName("1.1.1.1"))
        }

        val resolver = RacingDns(listOf(r1), timeoutMs = 50L)
        try {
            resolver.lookup("example.com")
            fail("Expected UnknownHostException on timeout")
        } catch (e: UnknownHostException) {
            // expected timeout exception
        }
    }
}
