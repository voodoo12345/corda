package net.corda.core.schemas

import io.requery.Persistable
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.serialization.toHexString
import java.io.Serializable
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.EmbeddedId
import javax.persistence.MappedSuperclass

//DOCSTART QueryableState
/**
 * A contract state that may be mapped to database schemas configured for this node to support querying for,
 * or filtering of, states.
 */
interface QueryableState : ContractState {
    /**
     * Enumerate the schemas this state can export representations of itself as.
     */
    fun supportedSchemas(): Iterable<MappedSchema>

    /**
     * Export a representation for the given schema.
     */
    fun generateMappedObject(schema: MappedSchema): PersistentState
}
//DOCEND QueryableState

//DOCSTART MappedSchema
/**
 * A database schema that might be configured for this node.  As well as a name and version for identifying the schema,
 * also list the classes that may be used in the generated object graph in order to configure the ORM tool.
 *
 * @param schemaFamily A class to fully qualify the name of a schema family (i.e. excludes version)
 * @param version The version number of this instance within the family.
 * @param mappedTypes The JPA entity classes that the ORM layer needs to be configure with for this schema.
 */
abstract class MappedSchema(schemaFamily: Class<*>,
                            val version: Int,
                            val mappedTypes: Iterable<Class<*>>) {
    val name: String = schemaFamily.name
    override fun toString(): String = "${this.javaClass.simpleName}(name=$name, version=$version)"
}
//DOCEND MappedSchema

/**
 * A super class for all mapped states exported to a schema that ensures the [StateRef] appears on the database row.  The
 * [StateRef] will be set to the correct value by the framework (there's no need to set during mapping generation by the state itself).
 */
@MappedSuperclass open class PersistentState(@EmbeddedId var stateRef: PersistentStateRef? = null) : Persistable

/**
 * Embedded [StateRef] representation used in state mapping.
 */
@Embeddable
data class PersistentStateRef(
        @Column(name = "transaction_id", length = 64)
        var txId: String?,

        @Column(name = "output_index")
        var index: Int?
) : Serializable {
    constructor(stateRef: StateRef) : this(stateRef.txhash.bytes.toHexString(), stateRef.index)
    /*
     JPA Query requirement:
     @Entity classes should have a default (non-arg) constructor to instantiate the objects when retrieving them from the database.
    */
    constructor() : this(null, null)
}
