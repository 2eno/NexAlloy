package io.github.nexalloy.keygen

import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Builds a password-protected Bitwarden JSON export holding the keystore as a secure note.
 * Import it at vault.bitwarden.com → Tools → Import data, format "Bitwarden (json)".
 *
 * The encryption follows Bitwarden's BitwardenPasswordProtectedImporter: PBKDF2-SHA256 over
 * the file password with the base64 salt string as salt, the result stretched with
 * HKDF-Expand into an AES-256-CBC key ("enc") and an HMAC-SHA256 key ("mac"), and each
 * value stored as an EncString of type 2: "2.<iv>|<ciphertext>|<mac>".
 */
object BitwardenExport {
    const val KDF_ITERATIONS = 600_000

    private const val FIELD_TEXT = 0
    private const val FIELD_HIDDEN = 1
    private const val CIPHER_SECURE_NOTE = 2

    private val random = SecureRandom()
    private val b64 = Base64.getEncoder()

    /** The unencrypted export: one secure note whose notes are the base64 keystore. */
    fun plainJson(keystore: GeneratedKeystore, itemName: String): String {
        val fields = listOf(
            Triple("KEYSTORE_PASSWORD", keystore.password, FIELD_HIDDEN),
            Triple("KEYSTORE_ALIAS", keystore.alias, FIELD_TEXT),
            Triple("KEYSTORE_ALIAS_PASSWORD", keystore.password, FIELD_HIDDEN),
            Triple(
                "SHA-256 fingerprint",
                KeystoreGenerator.sha256Fingerprint(keystore.certificate),
                FIELD_TEXT,
            ),
        ).joinToString(",") { (name, value, type) ->
            """{"name":${str(name)},"value":${str(value)},"type":$type,"linkedId":null}"""
        }
        // The notes hold exactly the value of the KEYSTORE secret so it can be copied as is.
        return """{"encrypted":false,"folders":[],"items":[{""" +
            """"id":null,"organizationId":null,"folderId":null,"collectionIds":null,""" +
            """"type":$CIPHER_SECURE_NOTE,"reprompt":0,"name":${str(itemName)},""" +
            """"notes":${str(b64.encodeToString(keystore.bytes))},"favorite":false,""" +
            """"secureNote":{"type":0},"fields":[$fields]}]}"""
    }

    fun encryptedJson(plainJson: String, filePassword: String): String {
        val salt = b64.encodeToString(ByteArray(16).also(random::nextBytes))
        val key = deriveKey(filePassword, salt, KDF_ITERATIONS)
        return """{"encrypted":true,"passwordProtected":true,"salt":${str(salt)},""" +
            """"kdfType":0,"kdfIterations":$KDF_ITERATIONS,""" +
            """"encKeyValidation_DO_NOT_EDIT":${str(encrypt(UUID.randomUUID().toString(), key))},""" +
            """"data":${str(encrypt(plainJson, key))}}"""
    }

    class Key(val enc: ByteArray, val mac: ByteArray)

    fun deriveKey(password: String, salt: String, iterations: Int): Key {
        // Bitwarden derives from the UTF-8 bytes of both, including the salt string itself
        // rather than the bytes it decodes to.
        val master = pbkdf2(password.toByteArray(), salt.toByteArray(), iterations)
        return Key(hkdfExpand(master, "enc"), hkdfExpand(master, "mac"))
    }

    fun encrypt(plain: String, key: Key): String {
        val iv = ByteArray(16).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.enc, "AES"), IvParameterSpec(iv))
        }
        val ct = cipher.doFinal(plain.toByteArray())
        val mac = hmac(key.mac, iv + ct)
        return "2.${b64.encodeToString(iv)}|${b64.encodeToString(ct)}|${b64.encodeToString(mac)}"
    }

    fun decrypt(encString: String, key: Key): String {
        require(encString.startsWith("2.")) { "Unsupported EncString type" }
        val (iv, ct, mac) = encString.removePrefix("2.").split('|').map(Base64.getDecoder()::decode)
        require(java.security.MessageDigest.isEqual(hmac(key.mac, iv + ct), mac)) { "Bad MAC" }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key.enc, "AES"), IvParameterSpec(iv))
        }
        return String(cipher.doFinal(ct))
    }

    private fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
        // Implemented on HMAC directly rather than through PBEKeySpec, whose handling of
        // non-ASCII passwords differs between Android versions.
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(password, "HmacSHA256")) }
        var u = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1))
        val out = u.copyOf()
        repeat(iterations - 1) {
            u = mac.doFinal(u)
            for (i in out.indices) out[i] = (out[i].toInt() xor u[i].toInt()).toByte()
        }
        return out
    }

    /** HKDF-Expand (RFC 5869) for a single 32-byte block. */
    private fun hkdfExpand(prk: ByteArray, info: String) =
        hmac(prk, info.toByteArray() + byteArrayOf(1))

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)

    private fun str(value: String): String {
        val sb = StringBuilder("\"")
        for (c in value) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c < ' ' -> sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }
}
