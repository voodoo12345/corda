package net.corda.core.serialization

import com.google.common.primitives.Ints
import net.corda.core.crypto.*
import net.corda.core.messaging.Ack
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider
import org.junit.Test
import java.io.InputStream
import java.security.Security
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals

class KryoTests {

    private val kryo = createKryo()

    @Test
    fun ok() {
        val birthday = Instant.parse("1984-04-17T00:30:00.00Z")
        val mike = Person("mike", birthday)
        val bits = mike.serialize(kryo)
        assertThat(bits.deserialize(kryo)).isEqualTo(Person("mike", birthday))
    }

    @Test
    fun nullables() {
        val bob = Person("bob", null)
        val bits = bob.serialize(kryo)
        assertThat(bits.deserialize(kryo)).isEqualTo(Person("bob", null))
    }

    @Test
    fun `serialised form is stable when the same object instance is added to the deserialised object graph`() {
        kryo.noReferencesWithin<ArrayList<*>>()
        val obj = Ints.toByteArray(0x01234567).opaque()
        val originalList = arrayListOf(obj)
        val deserialisedList = originalList.serialize(kryo).deserialize(kryo)
        originalList += obj
        deserialisedList += obj
        assertThat(deserialisedList.serialize(kryo)).isEqualTo(originalList.serialize(kryo))
    }

    @Test
    fun `serialised form is stable when the same object instance occurs more than once, and using java serialisation`() {
        kryo.noReferencesWithin<ArrayList<*>>()
        val instant = Instant.ofEpochMilli(123)
        val instantCopy = Instant.ofEpochMilli(123)
        assertThat(instant).isNotSameAs(instantCopy)
        val listWithCopies = arrayListOf(instant, instantCopy)
        val listWithSameInstances = arrayListOf(instant, instant)
        assertThat(listWithSameInstances.serialize(kryo)).isEqualTo(listWithCopies.serialize(kryo))
    }

    @Test
    fun `cyclic object graph`() {
        val cyclic = Cyclic(3)
        val bits = cyclic.serialize(kryo)
        assertThat(bits.deserialize(kryo)).isEqualTo(cyclic)
    }

    @Test
    fun `deserialised key pair functions the same as serialised one`() {
        val keyPair = generateKeyPair()
        val bitsToSign: ByteArray = Ints.toByteArray(0x01234567)
        val wrongBits: ByteArray = Ints.toByteArray(0x76543210)
        val signature = keyPair.signWithECDSA(bitsToSign)
        signature.verifyWithECDSA(bitsToSign)
        assertThatThrownBy { signature.verifyWithECDSA(wrongBits) }

        val deserialisedKeyPair = keyPair.serialize(kryo).deserialize(kryo)
        val deserialisedSignature = deserialisedKeyPair.signWithECDSA(bitsToSign)
        assertThat(deserialisedSignature).isEqualTo(signature)
        deserialisedSignature.verifyWithECDSA(bitsToSign)
        assertThatThrownBy { deserialisedSignature.verifyWithECDSA(wrongBits) }
    }

    @Test
    fun `write and read Ack`() {
        val tokenizableBefore = Ack
        val serializedBytes = tokenizableBefore.serialize(kryo)
        val tokenizableAfter = serializedBytes.deserialize(kryo)
        assertThat(tokenizableAfter).isSameAs(tokenizableBefore)
    }

    @Test
    fun `InputStream serialisation`() {
        val rubbish = ByteArray(12345, { (it * it * 0.12345).toByte() })
        val readRubbishStream: InputStream = rubbish.inputStream().serialize(kryo).deserialize(kryo)
        for (i in 0 .. 12344) {
            assertEquals(rubbish[i], readRubbishStream.read().toByte())
        }
        assertEquals(-1, readRubbishStream.read())
    }

    @Test
    fun `serialize - deserialize MetaData`() {
        Security.addProvider(BouncyCastleProvider())
        Security.addProvider(BouncyCastlePQCProvider())
        val testString = "Hello World"
        val testBytes = testString.toByteArray()
        val keyPair1 = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")
        val bitSet = java.util.BitSet(10)
        bitSet.set(3)

        val meta = MetaData("ECDSA_SECP256K1_SHA256", "M9", SignatureType.FULL, Instant.now(), bitSet, bitSet, testBytes, keyPair1.public)
        val serializedMetaData = meta.bytes()
        val meta2 = serializedMetaData.deserialize<MetaData>()
        assertEquals(meta2, meta)
    }

    @CordaSerializable
    private data class Person(val name: String, val birthday: Instant?)

    @Suppress("unused")
    @CordaSerializable
    private class Cyclic(val value: Int) {
        val thisInstance = this
        override fun equals(other: Any?): Boolean = (this === other) || (other is Cyclic && this.value == other.value)
        override fun hashCode(): Int = value.hashCode()
        override fun toString(): String = "Cyclic($value)"
    }

}
