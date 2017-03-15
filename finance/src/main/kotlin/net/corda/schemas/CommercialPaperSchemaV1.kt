package net.corda.schemas

import io.requery.Convert
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.requery.converters.InstantConverter
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * An object used to fully qualify the [CommercialPaperSchema] family name (i.e. independent of version).
 */
object CommercialPaperSchema

/**
 * First version of a commercial paper contract ORM schema that maps all fields of the [CommercialPaper] contract state
 * as it stood at the time of writing.
 */
object CommercialPaperSchemaV1 : MappedSchema(schemaFamily = CommercialPaperSchema.javaClass, version = 1, mappedTypes = listOf(PersistentCommericalPaperState::class.java)) {
    @Entity
    @Table(name = "cp_states")
    class PersistentCommericalPaperState(
            @Column(name = "issuance_key")
            var issuanceParty: String,

            @Column(name = "issuance_ref")
            var issuanceRef: ByteArray,

            @Column(name = "owner_key")
            var owner: String,

            @Column(name = "maturity_instant")
            var maturity: Instant,

            @Column(name = "face_value")
            var faceValue: Long,

            @Column(name = "ccy_code", length = 3)
            var currency: String,

            @Column(name = "face_value_issuer_key")
            var faceValueIssuerParty: String,

            @Column(name = "face_value_issuer_ref")
            var faceValueIssuerRef: ByteArray
    ) : PersistentState()
}
