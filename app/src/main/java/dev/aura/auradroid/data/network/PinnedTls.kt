package dev.aura.auradroid.data.network

import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * TLS for a desktop that signs its own certificate.
 *
 * No public CA will issue for 192.168.x.x, so the platform trust store has
 * nothing useful to say here and the default trust manager rejects the
 * connection outright — meaning OkHttp's CertificatePinner never gets a turn,
 * since it runs *after* chain validation. The trust decision therefore has to
 * be replaced rather than supplemented.
 *
 * What replaces it is a single question: is this the exact certificate that was
 * present when the user paired? That is a stronger guarantee than the CA system
 * gives on a home network, and it is the same model SSH has used for decades.
 */
object PinnedTls {

    /** Colon-separated uppercase hex, matching Node's X509Certificate.fingerprint256. */
    fun fingerprint(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256")
            .digest(cert.encoded)
            .joinToString(":") { "%02X".format(it) }

    private fun clientWith(trust: X509TrustManager): OkHttpClient.Builder {
        val ssl = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trust), java.security.SecureRandom())
        }
        return OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trust)
            // The certificate carries an IP SAN, and some Android versions are
            // inconsistent about verifying those. The pin already establishes
            // identity far more tightly than a name match would.
            .hostnameVerifier { _, _ -> true }
    }

    /**
     * Accepts only the certificate whose SHA-256 matches [expected].
     *
     * Any mismatch is a hard failure — a changed key on the desktop, or someone
     * else answering on that address. The user has to pair again, which is the
     * point: it makes the substitution visible rather than silent.
     */
    fun pinned(expected: String): OkHttpClient.Builder {
        val want = expected.uppercase()
        return clientWith(object : X509TrustManager {
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val leaf = chain?.firstOrNull()
                    ?: throw CertificateException("No certificate presented.")
                val got = fingerprint(leaf)
                if (got != want) {
                    throw CertificateException(
                        "Certificate does not match the paired desktop. " +
                            "Expected $want but got $got.",
                    )
                }
            }

            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                throw CertificateException("Client authentication is not used.")
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
    }

    /**
     * Accepts any certificate and reports what it saw. **Pairing only.**
     *
     * There is no prior knowledge of the desktop's key at this point, so this
     * one exchange is trust-on-first-use: whatever answers gets pinned, and
     * everything afterwards is checked against it. The exposure is a single
     * request on the user's own network, in a window they opened deliberately,
     * against a code that dies in ten minutes — and the desktop prints the
     * fingerprint so it can be compared if they want certainty.
     */
    fun trustOnFirstUse(record: (String) -> Unit): OkHttpClient.Builder =
        clientWith(object : X509TrustManager {
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val leaf = chain?.firstOrNull()
                    ?: throw CertificateException("No certificate presented.")
                record(fingerprint(leaf))
            }

            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                throw CertificateException("Client authentication is not used.")
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
}
