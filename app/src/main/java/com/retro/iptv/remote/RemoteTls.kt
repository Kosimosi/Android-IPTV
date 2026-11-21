package com.retro.iptv.remote

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** Private-LAN TLS identity plus trust-on-first-successful-pair certificate pinning. */
object RemoteTls {
    private const val IDENTITY_FILE = "retrotv_remote_identity.p12"
    private const val STORE_PASSWORD = "RetroTV-Local-Identity-2026"
    private const val PREFS = "retro_remote_tls"
    private const val KEY_CERT_SHA256 = "tv_certificate_sha256"

    fun serverContext(context: Context): SSLContext {
        val storeFile = File(context.filesDir, IDENTITY_FILE)
        val keyStore = KeyStore.getInstance("PKCS12")
        if (storeFile.exists()) {
            storeFile.inputStream().use { keyStore.load(it, STORE_PASSWORD.toCharArray()) }
        } else {
            keyStore.load(null, STORE_PASSWORD.toCharArray())
            val generator = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, SecureRandom()) }
            val pair = generator.generateKeyPair()
            val now = System.currentTimeMillis()
            val subject = X500Name("CN=RETROTV Local Remote")
            val certificateBuilder = JcaX509v3CertificateBuilder(
                subject,
                BigInteger(160, SecureRandom()),
                Date(now - 60_000L),
                Date(now + 10L * 365 * 24 * 60 * 60 * 1000),
                subject,
                pair.public
            )
            val signer = JcaContentSignerBuilder("SHA256withRSA").build(pair.private)
            val certificate = JcaX509CertificateConverter().getCertificate(certificateBuilder.build(signer))
            certificate.verify(pair.public)
            keyStore.setKeyEntry("retrotv", pair.private, STORE_PASSWORD.toCharArray(), arrayOf(certificate))
            storeFile.outputStream().use { keyStore.store(it, STORE_PASSWORD.toCharArray()) }
        }

        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, STORE_PASSWORD.toCharArray())
        }
        return SSLContext.getInstance("TLS").apply { init(keyManagers.keyManagers, null, SecureRandom()) }
    }

    data class ClientTls(val context: SSLContext, private val observedFingerprint: AtomicReference<String?>) {
        /** Called only after the TV has accepted the six-digit pairing PIN. */
        fun persistFirstPair(context: Context) {
            val fingerprint = observedFingerprint.get() ?: error("TV certificate was not presented")
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_CERT_SHA256, fingerprint).commit()
        }
    }

    fun clientContext(context: Context): ClientTls {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pinned = prefs.getString(KEY_CERT_SHA256, null)
        val observed = AtomicReference<String?>(null)
        val trustManager = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                val certificate = chain.firstOrNull() ?: throw java.security.cert.CertificateException("Missing TV certificate")
                try {
                    certificate.checkValidity()
                    certificate.verify(certificate.publicKey)
                } catch (error: Exception) {
                    throw java.security.cert.CertificateException("Invalid RETROTV self-signed certificate", error)
                }
                val actual = fingerprint(certificate)
                if (pinned != null && !MessageDigest.isEqual(
                        pinned.toByteArray(Charsets.US_ASCII), actual.toByteArray(Charsets.US_ASCII)
                    )
                ) throw java.security.cert.CertificateException("RETROTV certificate changed")
                observed.set(actual)
            }
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), SecureRandom())
        }
        return ClientTls(sslContext, observed)
    }

    private fun fingerprint(certificate: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            .joinToString("") { "%02x".format(it) }
}
