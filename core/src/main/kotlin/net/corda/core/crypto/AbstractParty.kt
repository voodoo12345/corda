package net.corda.core.crypto

import net.corda.core.contracts.PartyAndReference
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.OpaqueBytes
import java.security.PublicKey

/**
 * An [AbstractParty] contains the common elements of [Party] and [AnonymousParty], specifically the owning key of
 * the party. In most cases [Party] or [AnonymousParty] should be used, depending on use-case.
 */
@CordaSerializable
abstract class AbstractParty(val owningKey: CompositeKey) {
    /** A helper constructor that converts the given [PublicKey] in to a [CompositeKey] with a single node */
    constructor(owningKey: PublicKey) : this(owningKey.composite)

    /** Anonymised parties do not include any detail apart from owning key, so equality is dependent solely on the key */
    override fun equals(other: Any?): Boolean = other is AbstractParty && this.owningKey == other.owningKey
    override fun hashCode(): Int = owningKey.hashCode()
    abstract fun toAnonymous() : AnonymousParty
    abstract fun nameOrNull() : String?

    abstract fun ref(bytes: OpaqueBytes): PartyAndReference
    fun ref(vararg bytes: Byte) = ref(OpaqueBytes.of(*bytes))
}