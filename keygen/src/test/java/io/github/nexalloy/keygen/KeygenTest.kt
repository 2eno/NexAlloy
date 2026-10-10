package io.github.nexalloy.keygen

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class KeygenTest {
    private val keystore by lazy {
        KeystoreGenerator.generate("nexalloynext", KeystoreGenerator.randomPassword(), "NexAlloyNext", 100)
    }

    @Test
    fun keystoreLoadsAndSigns() {
        val ks = KeyStore.getInstance("PKCS12").apply {
            load(keystore.bytes.inputStream(), keystore.password.toCharArray())
        }
        val key = ks.getKey("nexalloynext", keystore.password.toCharArray()) as PrivateKey
        val cert = ks.getCertificate("nexalloynext")
        cert.verify(cert.publicKey)
        val data = "apk".toByteArray()
        val sig = Signature.getInstance("SHA256withRSA").run { initSign(key); update(data); sign() }
        assertTrue(Signature.getInstance("SHA256withRSA").run { initVerify(cert); update(data); verify(sig) })
        assertEquals("CN=NexAlloyNext", keystore.certificate.subjectX500Principal.name)
        assertEquals(3, keystore.certificate.version)
        assertTrue(keystore.certificate.notAfter.toInstant().toString() > "2125")
    }

    @Test
    fun pbkdf2MatchesJdk() {
        val salt = "c2FsdHNhbHRzYWx0"
        val ours = BitwardenExport.deriveKey("correct horse", salt, 1000)
        val master = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec("correct horse".toCharArray(), salt.toByteArray(), 1000, 256))
            .encoded
        val enc = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(master, "HmacSHA256")) }
            .doFinal("enc".toByteArray() + byteArrayOf(1))
        assertArrayEquals(enc, ours.enc)
    }

    @Test
    fun bitwardenExportDecrypts() {
        val plain = BitwardenExport.plainJson(keystore, "NexAlloy signing key")
        val file = BitwardenExport.encryptedJson(plain, "file password")
        val salt = Regex("\"salt\":\"([^\"]+)\"").find(file)!!.groupValues[1]
        val data = Regex("\"data\":\"([^\"]+)\"").find(file)!!.groupValues[1]
        val validation = Regex("\"encKeyValidation_DO_NOT_EDIT\":\"([^\"]+)\"").find(file)!!.groupValues[1]
        val key = BitwardenExport.deriveKey("file password", salt, BitwardenExport.KDF_ITERATIONS)
        BitwardenExport.decrypt(validation, key)
        assertEquals(plain, BitwardenExport.decrypt(data, key))
        assertTrue(plain.contains(Base64.getEncoder().encodeToString(keystore.bytes)))

        // Bitwarden limits encrypted notes to 10,000 characters.
        val notes = Base64.getEncoder().encodeToString(keystore.bytes)
        val encryptedNotes = BitwardenExport.encrypt(notes, key)
        println("keystore ${keystore.bytes.size} B, notes ${notes.length}, encrypted ${encryptedNotes.length}")
        assertTrue(encryptedNotes.length < 10_000)
    }
}
