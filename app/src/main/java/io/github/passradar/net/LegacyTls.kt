package io.github.passradar.net

import android.content.Context
import android.os.Build
import android.util.Log
import io.github.passradar.R
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * Lets old Android versions verify the servers the app talks to.
 *
 * pass-og-id.politiet.no uses a certificate from DigiCert Global Root G2, which
 * Android only trusts from 7.0. Kartverket's map tiles use Let's Encrypt (ISRG
 * Root X1), trusted from 7.1.1. On Android 7.1 and older we add both roots to
 * the system's own list for every HttpsURLConnection in the app (our API calls
 * and osmdroid's tile downloads).
 */
object LegacyTls {
    private val bundledRoots = listOf(R.raw.digicert_global_root_g2, R.raw.isrg_root_x1)

    fun install(context: Context) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) return
        try {
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
            val system = KeyStore.getInstance("AndroidCAStore").apply { load(null, null) }
            for (alias in system.aliases()) {
                keyStore.setCertificateEntry(alias, system.getCertificate(alias))
            }
            val certificates = CertificateFactory.getInstance("X.509")
            for (res in bundledRoots) {
                context.resources.openRawResource(res).use {
                    keyStore.setCertificateEntry("passradar-$res", certificates.generateCertificate(it))
                }
            }
            val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trust.init(keyStore)
            val tls = SSLContext.getInstance("TLS")
            tls.init(null, trust.trustManagers, null)
            HttpsURLConnection.setDefaultSSLSocketFactory(tls.socketFactory)
        } catch (e: Exception) {
            Log.w("Passradar", "Could not add bundled root certificates", e)
        }
    }
}
