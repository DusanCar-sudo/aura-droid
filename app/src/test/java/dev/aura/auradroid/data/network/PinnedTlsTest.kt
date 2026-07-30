package dev.aura.auradroid.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * The pin is the entire trust decision on the LAN, so these check the two
 * outcomes that matter: the paired desktop is accepted, and anything else —
 * including a valid certificate for the same address — is refused.
 *
 * Both certificates below are self-signed throwaways generated for this test.
 */
class PinnedTlsTest {

    private fun cert(pem: String): X509Certificate =
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(pem.toByteArray())) as X509Certificate

    private fun trustManagerOf(builder: okhttp3.OkHttpClient.Builder): X509TrustManager {
        // The builder is configured with exactly one trust manager; reach it
        // through the socket factory the same way OkHttp does.
        val field = builder.javaClass.getDeclaredField("x509TrustManagerOrNull")
        field.isAccessible = true
        return field.get(builder) as X509TrustManager
    }

    @Test
    fun `fingerprint matches the colon-hex form the desktop prints`() {
        val c = cert(CERT_A)
        val fp = PinnedTls.fingerprint(c)

        // Node's X509Certificate.fingerprint256 is uppercase colon-separated
        // hex; a mismatch in format would silently never match.
        assertTrue("unexpected shape: $fp", fp.matches(Regex("([0-9A-F]{2}:){31}[0-9A-F]{2}")))
    }

    @Test
    fun `accepts exactly the pinned certificate`() {
        val c = cert(CERT_A)
        val tm = trustManagerOf(PinnedTls.pinned(PinnedTls.fingerprint(c)))
        tm.checkServerTrusted(arrayOf(c), "EC")   // must not throw
    }

    @Test
    fun `accepts the pin case-insensitively`() {
        val c = cert(CERT_A)
        val tm = trustManagerOf(PinnedTls.pinned(PinnedTls.fingerprint(c).lowercase()))
        tm.checkServerTrusted(arrayOf(c), "EC")
    }

    @Test
    fun `rejects a different certificate for the same address`() {
        val a = cert(CERT_A)
        val b = cert(CERT_B)
        assertNotEquals(PinnedTls.fingerprint(a), PinnedTls.fingerprint(b))

        val tm = trustManagerOf(PinnedTls.pinned(PinnedTls.fingerprint(a)))
        try {
            tm.checkServerTrusted(arrayOf(b), "EC")
            fail("a substituted certificate must not be accepted")
        } catch (e: CertificateException) {
            assertTrue(e.message!!.contains("does not match"))
        }
    }

    @Test
    fun `rejects an empty chain`() {
        val tm = trustManagerOf(PinnedTls.pinned(PinnedTls.fingerprint(cert(CERT_A))))
        try {
            tm.checkServerTrusted(emptyArray(), "EC")
            fail("no certificate must not count as a match")
        } catch (e: CertificateException) {
            assertTrue(e.message!!.contains("No certificate"))
        }
    }

    @Test
    fun `trust-on-first-use reports what it saw`() {
        val c = cert(CERT_A)
        var seen: String? = null
        val tm = trustManagerOf(PinnedTls.trustOnFirstUse { seen = it })

        tm.checkServerTrusted(arrayOf(c), "EC")
        assertEquals(PinnedTls.fingerprint(c), seen)
    }

    @Test
    fun `client certificates are never trusted`() {
        val tm = trustManagerOf(PinnedTls.pinned(PinnedTls.fingerprint(cert(CERT_A))))
        assertTrue(tm.acceptedIssuers.isEmpty())
        try {
            tm.checkClientTrusted(arrayOf(cert(CERT_A)), "EC")
            fail("client auth is not part of this design")
        } catch (_: CertificateException) {
            // expected
        }
    }

    private companion object {
        // openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1
        //   -nodes -days 3650 -subj "/CN=Aura (172.16.10.195)"
        //   -addext "subjectAltName=IP:172.16.10.195"
        const val CERT_A = """-----BEGIN CERTIFICATE-----
MIIBpTCCAUqgAwIBAgIUOH2FSnVvvEYpRTbx9GyXObl/K78wCgYIKoZIzj0EAwIw
HzEdMBsGA1UEAwwUQXVyYSAoMTcyLjE2LjEwLjE5NSkwHhcNMjYwNzI5MDI1MTE2
WhcNMzYwNzI2MDI1MTE2WjAfMR0wGwYDVQQDDBRBdXJhICgxNzIuMTYuMTAuMTk1
KTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABItD1KK9iP4M2VNoGx0ASiZlxdLl
HDOyWN/k+S+YQS9llRthIHfT43/ZVetLP4UYK4tVOSmvOwACVplyNGy+M3KjZDBi
MB0GA1UdDgQWBBT8mZPTqMrb+RHFmuTpRJ6xLKHCnjAfBgNVHSMEGDAWgBT8mZPT
qMrb+RHFmuTpRJ6xLKHCnjAPBgNVHRMBAf8EBTADAQH/MA8GA1UdEQQIMAaHBKwQ
CsMwCgYIKoZIzj0EAwIDSQAwRgIhAJtQZGBDqei1HAgU7ssOV/mc1iozVEMVBsbS
QEV0rnZqAiEAgUwt+NSalMoWE8hdaEXnLALUoWgO9J/dAy1047lk6S4=
-----END CERTIFICATE-----"""

        // A second, independently generated certificate for the same address —
        // this is what an impersonator on the network would present.
        const val CERT_B = """-----BEGIN CERTIFICATE-----
MIIBozCCAUqgAwIBAgIUTqI+f9rhBfHzO38lmFaIyrP6AiswCgYIKoZIzj0EAwIw
HzEdMBsGA1UEAwwUQXVyYSAoMTcyLjE2LjEwLjE5NSkwHhcNMjYwNzI5MDI1MTE2
WhcNMzYwNzI2MDI1MTE2WjAfMR0wGwYDVQQDDBRBdXJhICgxNzIuMTYuMTAuMTk1
KTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABBxBlfFQND2LEJYsOkraqE/AwqKu
SVeDsgvxgwzlc5+YKk0IQo2gxYMsc41iBtNbebzPfhd5iqCIuTvoKrscnYmjZDBi
MB0GA1UdDgQWBBQUhSSIJG8kIVuH+klKxqayJ6shtDAfBgNVHSMEGDAWgBQUhSSI
JG8kIVuH+klKxqayJ6shtDAPBgNVHRMBAf8EBTADAQH/MA8GA1UdEQQIMAaHBKwQ
CsMwCgYIKoZIzj0EAwIDRwAwRAIgeuz71EPcE85y820tPw7MXxPhvwOVsDQrMm4B
V7ROMoECIAwr+gIyttSBE3q3Abm+B8iaidiGo2gKU54szjCVW6HV
-----END CERTIFICATE-----"""
    }
}
