package com.app.drivedata.auth

import java.security.MessageDigest
import java.security.SecureRandom

/** Salts and hashes passwords so the database never stores them in clear text. */
object PasswordHasher {

    private const val DIGEST_ALGORITHM = "SHA-256"
    private const val SALT_SIZE_BYTES = 16

    fun newSalt(): String {
        val salt = ByteArray(SALT_SIZE_BYTES)
        SecureRandom().nextBytes(salt)
        return salt.toHex()
    }

    fun hash(password: String, salt: String): String {
        val digest = MessageDigest.getInstance(DIGEST_ALGORITHM)
        digest.update(salt.hexToBytes())
        return digest.digest(password.toByteArray(Charsets.UTF_8)).toHex()
    }

    fun verify(password: String, salt: String, expectedHash: String): Boolean =
        MessageDigest.isEqual(
            hash(password, salt).toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8),
        )

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { pair -> pair.toInt(16).toByte() }.toByteArray()
}
