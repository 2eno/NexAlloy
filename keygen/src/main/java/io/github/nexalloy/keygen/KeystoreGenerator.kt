package io.github.nexalloy.keygen

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** A generated keystore plus the values the GitHub workflows read from secrets. */
class GeneratedKeystore(
    val bytes: ByteArray,
    val alias: String,
    /** Used for both the keystore and the key, like keytool does for PKCS12. */
    val password: String,
    val certificate: X509Certificate,
)

/**
 * Generates what `keytool -genkeypair -keyalg RSA -keysize 4096` does: an RSA key with a
 * self-signed certificate, in a PKCS12 keystore. The Android Gradle plugin and keytool
 * both read PKCS12 regardless of the file extension, so it can be saved as `.jks`.
 */
object KeystoreGenerator {
    private val random = SecureRandom()

    /**
     * Letters and digits only: the workflows write the password into signing.properties
     * with an unquoted shell `echo`, which would mangle characters like `$`, `&` or spaces.
     */
    fun randomPassword(length: Int = 32): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        return String(CharArray(length) { chars[random.nextInt(chars.length)] })
    }

    fun generate(
        alias: String,
        password: String,
        commonName: String,
        validityYears: Int,
        keySize: Int = 4096,
    ): GeneratedKeystore {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(keySize, random) }
            .generateKeyPair()

        val now = ZonedDateTime.now(ZoneOffset.UTC).withNano(0)
        val name = Der.name(commonName)
        val sha256WithRsa = Der.seq(Der.oid("1.2.840.113549.1.1.11"), Der.NULL)
        val tbs = Der.seq(
            Der.explicit(0, Der.integer(BigInteger.valueOf(2))), // v3
            Der.integer(BigInteger(63, random).add(BigInteger.ONE)),
            sha256WithRsa,
            name,
            Der.seq(Der.time(now), Der.time(now.plusYears(validityYears.toLong()))),
            name,
            keyPair.public.encoded, // already a DER SubjectPublicKeyInfo
        )
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(keyPair.private)
            update(tbs)
            sign()
        }
        val certDer = Der.seq(tbs, sha256WithRsa, Der.bitString(signature))
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(certDer.inputStream()) as X509Certificate
        certificate.verify(keyPair.public)

        val chars = password.toCharArray()
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry(alias, keyPair.private, chars, arrayOf(certificate))
        }
        val bytes = ByteArrayOutputStream().also { keyStore.store(it, chars) }.toByteArray()
        return GeneratedKeystore(bytes, alias, password, certificate)
    }

    fun sha256Fingerprint(certificate: X509Certificate): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            .joinToString(":") { "%02X".format(it) }
}

/** Just enough DER to build an X.509 certificate. */
internal object Der {
    val NULL = byteArrayOf(0x05, 0x00)

    fun tlv(tag: Int, content: ByteArray): ByteArray {
        val length = content.size
        val header = when {
            length < 0x80 -> byteArrayOf(tag.toByte(), length.toByte())
            length < 0x100 -> byteArrayOf(tag.toByte(), 0x81.toByte(), length.toByte())
            length < 0x10000 -> byteArrayOf(
                tag.toByte(), 0x82.toByte(), (length shr 8).toByte(), length.toByte()
            )
            else -> byteArrayOf(
                tag.toByte(), 0x83.toByte(),
                (length shr 16).toByte(), (length shr 8).toByte(), length.toByte()
            )
        }
        return header + content
    }

    fun seq(vararg parts: ByteArray) = tlv(0x30, concat(parts))
    fun set(vararg parts: ByteArray) = tlv(0x31, concat(parts))
    fun explicit(tag: Int, inner: ByteArray) = tlv(0xA0 or tag, inner)
    fun integer(value: BigInteger) = tlv(0x02, value.toByteArray())
    fun bitString(bytes: ByteArray) = tlv(0x03, byteArrayOf(0) + bytes)
    fun utf8(value: String) = tlv(0x0C, value.toByteArray(Charsets.UTF_8))

    fun oid(dotted: String): ByteArray {
        val arcs = dotted.split('.').map { it.toLong() }
        val out = ByteArrayOutputStream()
        out.write((arcs[0] * 40 + arcs[1]).toInt())
        for (arc in arcs.drop(2)) {
            var septets = listOf((arc and 0x7F).toInt())
            var rest = arc shr 7
            while (rest > 0) {
                septets = listOf(((rest and 0x7F) or 0x80).toInt()) + septets
                rest = rest shr 7
            }
            septets.forEach(out::write)
        }
        return tlv(0x06, out.toByteArray())
    }

    /** X.500 name with a single common name. */
    fun name(commonName: String) = seq(set(seq(oid("2.5.4.3"), utf8(commonName))))

    /** RFC 5280: UTCTime through 2049, GeneralizedTime from 2050. */
    fun time(time: ZonedDateTime): ByteArray =
        if (time.year < 2050) {
            tlv(0x17, time.format(DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'")).toByteArray())
        } else {
            tlv(0x18, time.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'")).toByteArray())
        }

    private fun concat(parts: Array<out ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        parts.forEach(out::write)
        return out.toByteArray()
    }
}
