package net.corda.schemas

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import javax.persistence.*

/**
 * An object used to fully qualify the [CashSchema] family name (i.e. independent of version).
 */
object CashSchema

/**
 * First version of a cash contract ORM schema that maps all fields of the [Cash] contract state as it stood
 * at the time of writing.
 */
object CashSchemaV1 : MappedSchema(schemaFamily = CashSchema.javaClass, version = 1, mappedTypes = listOf(PersistentCashState::class.java)) {
    @Entity
    @Table(name = "cash_states")
    class PersistentCashState(
            @Column(name = "owner_key")
            var owner: String,

            @Column(name = "pennies")
            var pennies: Long,

            @Column(name = "ccy_code", length = 3)
            var currency: String,

            @Column(name = "issuer_key")
            var issuerParty: String,

            @Column(name = "issuer_ref")
            var issuerRef: ByteArray
    ) : PersistentState()
}
