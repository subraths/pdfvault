package com.pdfvault.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Human-friendly byte count, e.g. "1.4 MB". */
fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${bytes} B" else String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
}

private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

/** Formats an epoch-seconds timestamp, or "" when unknown. */
fun formatEpochSeconds(seconds: Long): String =
    if (seconds <= 0) "" else dateFormat.format(Date(seconds * 1000))

/** Coarse "time ago" label for a past epoch-millis timestamp, e.g. "3h ago". */
fun formatRelativeMillis(millis: Long): String {
    if (millis <= 0) return ""
    val delta = (System.currentTimeMillis() - millis).coerceAtLeast(0)
    val minutes = delta / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> dateFormat.format(Date(millis))
    }
}

/** Maps any throwable to a short, human message suitable for a snackbar or error banner. */
fun Throwable.userMessage(): String {
    val name = this::class.simpleName.orEmpty()
    val msg = message.orEmpty()
    return when {
        this is java.net.UnknownHostException || this is java.net.ConnectException ->
            "Can't reach S3 — check your internet connection."
        this is java.net.SocketTimeoutException || name.contains("Timeout", true) ->
            "The connection timed out. Please try again."
        msg.contains("SignatureDoesNotMatch", true) || msg.contains("InvalidAccessKeyId", true) ->
            "Invalid credentials. Re-check your access key and secret."
        msg.contains("AccessDenied", true) || msg.contains("403") || msg.contains("Forbidden", true) ->
            "Access denied — your credentials may lack permission for this action."
        msg.contains("NoSuchBucket", true) ->
            "That bucket no longer exists."
        msg.contains("NoSuchKey", true) || msg.contains("404", true) || msg.contains("NotFound", true) ->
            "That file no longer exists on S3."
        msg.isNotBlank() -> msg
        else -> name.ifBlank { "Something went wrong." }
    }
}
