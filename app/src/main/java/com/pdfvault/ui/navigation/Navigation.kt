package com.pdfvault.ui.navigation

import android.util.Base64

/** Builds the route strings used by the [NavHost] and encodes path arguments. */
object Routes {
    const val SETUP = "setup"
    const val MAIN = "main"
    const val VIEWER = "viewer/{keyB64}"

    fun viewer(objectKey: String): String = "viewer/${NavArgs.encode(objectKey)}"
}

/**
 * S3 keys contain "/" and other characters that break path-based nav routes, so we
 * URL-safe Base64 the value when putting it in a route and decode it on the way out.
 */
object NavArgs {
    private const val FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

    fun encode(value: String): String =
        Base64.encodeToString(value.toByteArray(Charsets.UTF_8), FLAGS)

    fun decode(value: String): String =
        String(Base64.decode(value, FLAGS), Charsets.UTF_8)
}
