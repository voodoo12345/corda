package net.corda.node.serialization

import com.esotericsoftware.kryo.KryoException
import com.google.common.net.HostAndPort
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.serialization.SerializationCustomization
import org.apache.activemq.artemis.api.core.SimpleString
import rx.Notification
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Period
import java.util.*

class DefaultWhitelist : CordaPluginRegistry() {
    override fun customizeSerialization(custom: SerializationCustomization): Boolean {
        custom.apply {
            addToWhitelist(Array<Any>(0, {}).javaClass)
            addToWhitelist(Notification::class.java)
            addToWhitelist(Notification.Kind::class.java)
            addToWhitelist(ArrayList::class.java)
            addToWhitelist(listOf<Any>().javaClass) // EmptyList
            addToWhitelist(Pair::class.java)
            addToWhitelist(ByteArray::class.java)
            addToWhitelist(UUID::class.java)
            addToWhitelist(LinkedHashSet::class.java)
            addToWhitelist(setOf<Unit>().javaClass) // EmptySet
            addToWhitelist(Currency::class.java)
            addToWhitelist(listOf(Unit).javaClass) // SingletonList
            addToWhitelist(setOf(Unit).javaClass) // SingletonSet
            addToWhitelist(mapOf(Unit to Unit).javaClass) // SingletonSet
            addToWhitelist(HostAndPort::class.java)
            addToWhitelist(SimpleString::class.java)
            addToWhitelist(KryoException::class.java)
            addToWhitelist(StringBuffer::class.java)
            addToWhitelist(Unit::class.java)
            addToWhitelist(java.io.ByteArrayInputStream::class.java)
            addToWhitelist(java.lang.Class::class.java)
            addToWhitelist(java.math.BigDecimal::class.java)
            addToWhitelist(java.security.KeyPair::class.java)
            addToWhitelist(java.time.Duration::class.java)
            addToWhitelist(java.time.Instant::class.java)
            addToWhitelist(java.time.LocalDate::class.java)
            addToWhitelist(java.util.Collections.singletonMap("A", "B").javaClass)
            addToWhitelist(java.util.HashMap::class.java)
            addToWhitelist(java.util.LinkedHashMap::class.java)
            addToWhitelist(BigDecimal::class.java)
            addToWhitelist(LocalDate::class.java)
            addToWhitelist(Period::class.java)
            addToWhitelist(BitSet::class.java)
        }
        return true
    }
}