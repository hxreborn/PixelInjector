package eu.hxreborn.pixelinjector.support

import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object Hpke {
    private const val KEM_ID = 0x0020
    private const val KDF_ID = 0x0001
    private const val AEAD_ID = 0x0002
    private const val KEY_LENGTH = 32
    private const val NONCE_LENGTH = 12
    private const val TAG_BITS = 128
    private const val RAW_KEY_LENGTH = 32
    const val SEALED_OVERHEAD = RAW_KEY_LENGTH + TAG_BITS / Byte.SIZE_BITS
    private val spkiPrefix = decodeHex("302a300506032b656e032100")
    private val version = "HPKE-v1".toByteArray()
    private val kemSuite = "KEM".toByteArray() + u16(KEM_ID)
    private val hpkeSuite = "HPKE".toByteArray() + u16(KEM_ID) + u16(KDF_ID) + u16(AEAD_ID)

    class Sealed(
        val enc: ByteArray,
        val ciphertext: ByteArray,
    )

    class NoteKey(
        val privatePkcs8: ByteArray,
        val publicRaw: ByteArray,
    )

    fun newNoteKey(): NoteKey {
        val pair = KeyPairGenerator.getInstance("X25519").generateKeyPair()
        return NoteKey(
            pair.private.encoded,
            pair.public.encoded
                .takeLast(RAW_KEY_LENGTH)
                .toByteArray(),
        )
    }

    fun seal(
        recipientPublic: ByteArray,
        info: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
    ): Sealed {
        val ephemeral =
            KeyPairGenerator.getInstance("X25519").generateKeyPair()
        return sealWith(ephemeral, recipientPublic, info, aad, plaintext)
    }

    fun sealWith(
        ephemeral: KeyPair,
        recipientPublic: ByteArray,
        info: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
    ): Sealed {
        val enc =
            ephemeral.public.encoded
                .takeLast(RAW_KEY_LENGTH)
                .toByteArray()
        val cipher =
            cipher(
                Cipher.ENCRYPT_MODE,
                dh(ephemeral.private, recipientPublic),
                enc,
                recipientPublic,
                info,
            )
        cipher.updateAAD(aad)
        return Sealed(enc, cipher.doFinal(plaintext))
    }

    fun open(
        key: NoteKey,
        info: ByteArray,
        aad: ByteArray,
        sealed: ByteArray,
    ): ByteArray {
        val enc = sealed.copyOf(RAW_KEY_LENGTH)
        val private =
            KeyFactory
                .getInstance(
                    "XDH",
                ).generatePrivate(PKCS8EncodedKeySpec(key.privatePkcs8))
        val cipher = cipher(Cipher.DECRYPT_MODE, dh(private, enc), enc, key.publicRaw, info)
        cipher.updateAAD(aad)
        return cipher.doFinal(sealed, RAW_KEY_LENGTH, sealed.size - RAW_KEY_LENGTH)
    }

    private fun cipher(
        mode: Int,
        dh: ByteArray,
        enc: ByteArray,
        recipientPublic: ByteArray,
        info: ByteArray,
    ): Cipher {
        val prk = labeledExtract(kemSuite, ByteArray(0), "eae_prk", dh)
        val shared =
            labeledExpand(kemSuite, prk, "shared_secret", enc + recipientPublic, KEY_LENGTH)
        val empty = ByteArray(0)
        val context =
            byteArrayOf(0) +
                labeledExtract(hpkeSuite, empty, "psk_id_hash", empty) +
                labeledExtract(hpkeSuite, empty, "info_hash", info)
        val secret = labeledExtract(hpkeSuite, shared, "secret", empty)
        val key = labeledExpand(hpkeSuite, secret, "key", context, KEY_LENGTH)
        val nonce = labeledExpand(hpkeSuite, secret, "base_nonce", context, NONCE_LENGTH)
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        }
    }

    private fun dh(
        private: PrivateKey,
        publicRaw: ByteArray,
    ): ByteArray {
        val public =
            KeyFactory.getInstance("XDH").generatePublic(
                X509EncodedKeySpec(spkiPrefix + publicRaw),
            )
        return KeyAgreement.getInstance("XDH").run {
            init(private)
            doPhase(public, true)
            generateSecret()
        }
    }

    private fun hmac(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(if (key.isEmpty()) ByteArray(KEY_LENGTH) else key, "HmacSHA256"))
            doFinal(data)
        }

    private fun labeledExtract(
        suite: ByteArray,
        salt: ByteArray,
        label: String,
        ikm: ByteArray,
    ): ByteArray = hmac(salt, version + suite + label.toByteArray() + ikm)

    private fun labeledExpand(
        suite: ByteArray,
        prk: ByteArray,
        label: String,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        val labeled = u16(length) + version + suite + label.toByteArray() + info
        val out = ByteArrayOutputStream()
        var block = ByteArray(0)
        var counter = 1
        while (out.size() < length) {
            block = hmac(prk, block + labeled + byteArrayOf(counter++.toByte()))
            out.write(block, 0, block.size)
        }
        return out.toByteArray().copyOf(length)
    }

    private fun u16(value: Int): ByteArray = byteArrayOf((value shr 8).toByte(), value.toByte())

    fun decodeHex(value: String): ByteArray =
        value
            .chunked(2)
            .map {
                it.toInt(16).toByte()
            }.toByteArray()
}
