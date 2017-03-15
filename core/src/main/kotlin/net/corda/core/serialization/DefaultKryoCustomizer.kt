package net.corda.core.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer
import com.esotericsoftware.kryo.serializers.FieldSerializer
import com.esotericsoftware.kryo.util.MapReferenceResolver
import de.javakaffee.kryoserializers.ArraysAsListSerializer
import de.javakaffee.kryoserializers.UnmodifiableCollectionsSerializer
import de.javakaffee.kryoserializers.guava.*
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.MetaData
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.NonEmptySetSerializer
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import org.objenesis.strategy.StdInstantiatorStrategy
import java.io.BufferedInputStream
import java.util.*

object DefaultKryoCustomizer {
    private val pluginRegistries: List<CordaPluginRegistry> by lazy {
        // No ClassResolver only constructor.  MapReferenceResolver is the default as used by Kryo in other constructors.
        val unusedKryo = Kryo(makeStandardClassResolver(), MapReferenceResolver())
        val customization = KryoSerializationCustomization(unusedKryo)
        ServiceLoader.load(CordaPluginRegistry::class.java).toList().filter { it.customizeSerialization(customization) }
    }

    fun customize(kryo: Kryo): Kryo {
        return kryo.apply {
            // Store a little schema of field names in the stream the first time a class is used which increases tolerance
            // for change to a class.
            setDefaultSerializer(CompatibleFieldSerializer::class.java)
            // Take the safest route here and allow subclasses to have fields named the same as super classes.
            fieldSerializerConfig.setCachedFieldNameStrategy(FieldSerializer.CachedFieldNameStrategy.EXTENDED)

            // Allow construction of objects using a JVM backdoor that skips invoking the constructors, if there is no
            // no-arg constructor available.
            instantiatorStrategy = Kryo.DefaultInstantiatorStrategy(StdInstantiatorStrategy())

            register(Arrays.asList("").javaClass, ArraysAsListSerializer())
            register(SignedTransaction::class.java, ImmutableClassSerializer(SignedTransaction::class))
            register(WireTransaction::class.java, WireTransactionSerializer)
            register(SerializedBytes::class.java, SerializedBytesSerializer)

            UnmodifiableCollectionsSerializer.registerSerializers(this)
            ImmutableListSerializer.registerSerializers(this)
            ImmutableSetSerializer.registerSerializers(this)
            ImmutableSortedSetSerializer.registerSerializers(this)
            ImmutableMapSerializer.registerSerializers(this)
            ImmutableMultimapSerializer.registerSerializers(this)

            register(BufferedInputStream::class.java, InputStreamSerializer)
            register(Class.forName("sun.net.www.protocol.jar.JarURLConnection\$JarURLInputStream"), InputStreamSerializer)

            noReferencesWithin<WireTransaction>()

            register(EdDSAPublicKey::class.java, Ed25519PublicKeySerializer)
            register(EdDSAPrivateKey::class.java, Ed25519PrivateKeySerializer)

            // Using a custom serializer for compactness
            register(CompositeKey.Node::class.java, CompositeKeyNodeSerializer)
            register(CompositeKey.Leaf::class.java, CompositeKeyLeafSerializer)

            // Exceptions. We don't bother sending the stack traces as the client will fill in its own anyway.
            register(Array<StackTraceElement>::class, read = { kryo, input -> emptyArray() }, write = { kryo, output, obj -> })

            // This ensures a NonEmptySetSerializer is constructed with an initial value.
            register(NonEmptySet::class.java, NonEmptySetSerializer)

            /** This ensures any kotlin objects that implement [DeserializeAsKotlinObjectDef] are read back in as singletons. */
            addDefaultSerializer(DeserializeAsKotlinObjectDef::class.java, KotlinObjectSerializer)

            addDefaultSerializer(SerializeAsToken::class.java, SerializeAsTokenSerializer<SerializeAsToken>())

            register(MetaData::class.java, MetaDataSerializer)
            register(BitSet::class.java, ReferencesAwareJavaSerializer)

            val customization = KryoSerializationCustomization(this)
            pluginRegistries.forEach { it.customizeSerialization(customization) }
        }
    }
}