package io.github.passradar.net

import android.os.Build
import io.github.passradar.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Something answered, but not with the data we asked for. */
open class BadResponseException(message: String, cause: Throwable? = null) : IOException(message, cause)

class HttpStatusException(val code: Int) : BadResponseException("HTTP $code")

object Http {
    private const val TIMEOUT_MS = 20_000

    /** Tells the server honestly who is asking. */
    val userAgent = "Passradar/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; open source)"

    /** Blocking GET that returns a JSON body. Call from a background thread. */
    fun getJson(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", userAgent)
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                connection.errorStream?.close()
                throw HttpStatusException(code)
            }
            val type = connection.contentType.orEmpty()
            val body = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            // The firewall in front of the booking system can answer with an HTML page.
            if (!type.contains("json", ignoreCase = true)) {
                throw BadResponseException("Unexpected content type '$type'")
            }
            return body
        } catch (e: IOException) {
            connection.disconnect()
            throw e
        }
    }
}
