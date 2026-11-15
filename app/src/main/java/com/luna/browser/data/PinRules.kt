package com.luna.browser.data

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The PIN of a profile. It keeps family members out of each other's profiles (a child can't open a parent's history or
 * sign-ins from the picker); it is not encryption: the data stays readable to anyone who can read the TV's app storage.
 *
 * Only a salted SHA-256 of the PIN is stored, never the PIN. Wrong tries are counted in memory by the app and answered with
 * a growing wait ([lockSeconds]).
 */
object PinRules {
    const val LENGTH = 4
    private const val FREE_TRIES = 5

    fun valid(pin: String) = pin.length == LENGTH && pin.all { it in '0'..'9' }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    private fun digest(salt: String, pin: String) = hex(MessageDigest.getInstance("SHA-256").digest((salt + ":" + pin).toByteArray()))

    /** "salt:hash" for [pin]. */
    fun encode(pin: String, salt: String = hex(ByteArray(8).also { SecureRandom().nextBytes(it) })): String = salt + ":" + digest(salt, pin)

    /** True when [pin] matches what [encode] made; false for anything malformed. */
    fun verify(pin: String, stored: String?): Boolean {
        if (stored == null || !valid(pin)) return false
        val i = stored.indexOf(':')
        if (i <= 0 || i == stored.lastIndex) return false
        val salt = stored.substring(0, i)
        return MessageDigest.isEqual(digest(salt, pin).toByteArray(), stored.substring(i + 1).toByteArray())
    }

    /** Seconds to wait after [failures] wrong tries in a row: none for the first four, then 30 s, doubling up to 5 minutes. */
    fun lockSeconds(failures: Int): Int {
        if (failures < FREE_TRIES) return 0
        var s = 30
        repeat(failures - FREE_TRIES) { s = minOf(300, s * 2) }
        return s
    }
}
