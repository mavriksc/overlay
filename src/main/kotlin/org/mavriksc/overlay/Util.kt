package org.mavriksc.overlay

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.awt.Point
import java.nio.file.Files
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.math.roundToInt
import kotlin.io.path.Path

fun String.toRequest(): Request = Request.Builder().url(this).build()

fun String.writeToFile(text: String) {
    val file = File(this)
    file.parentFile?.mkdirs()
    file.writeText(text)
}

fun String.getTextFromFile(): String? {
    return try {
        Files.readString(Path(this))
    } catch (_: Exception) {
        null
    }
}

fun getOkHttpClientForGameClient(timeout: Long = 1, unit: TimeUnit = TimeUnit.SECONDS): OkHttpClient {
    val certificateFactory = CertificateFactory.getInstance("X.509")
    val riotCert = object {}.javaClass.getResourceAsStream("/riotgames.pem")?.use {
        certificateFactory.generateCertificate(it) as X509Certificate
    } ?: error("Missing riotgames.pem in resources")

    val riotKeyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
        load(null, null)
        setCertificateEntry("riotgames-root", riotCert)
    }
    val riotTrustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
        init(riotKeyStore)
    }.trustManagers.filterIsInstance<X509TrustManager>().first()

    val defaultTrustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
        init(null as KeyStore?)
    }.trustManagers.filterIsInstance<X509TrustManager>().first()

    val compositeTrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            try {
                defaultTrustManager.checkClientTrusted(chain, authType)
            } catch (e: CertificateException) {
                riotTrustManager.checkClientTrusted(chain, authType)
            }
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            try {
                defaultTrustManager.checkServerTrusted(chain, authType)
            } catch (e: CertificateException) {
                riotTrustManager.checkServerTrusted(chain, authType)
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> {
            return defaultTrustManager.acceptedIssuers + riotTrustManager.acceptedIssuers
        }
    }

    val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(compositeTrustManager), null)
    }

    return OkHttpClient.Builder()
        .callTimeout(timeout, unit)
        .sslSocketFactory(sslContext.socketFactory, compositeTrustManager)
        .build()
}


fun <A, B> Pair<A,B>.x() = first
fun <A, B> Pair<A,B>.y() = second

fun pointBetween(a: Point, b: Point, percent: Int): Point {
    val t = percent.coerceIn(0, 100) / 100.0
    val x = (a.x + (b.x - a.x) * t).roundToInt()
    val y = (a.y + (b.y - a.y) * t).roundToInt()
    return Point(x, y)
}

fun intBetween(a: Int, b: Int, percent: Int): Int {
    val t = percent.coerceIn(0, 100) / 100.0
    return (a + (b - a) * t).roundToInt()
}

