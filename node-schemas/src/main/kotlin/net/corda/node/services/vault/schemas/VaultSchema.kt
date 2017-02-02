package net.corda.node.services.vault.schemas

import io.requery.*
import net.corda.core.node.services.Vault
import net.corda.core.schemas.requery.Requery
import net.corda.core.schemas.requery.converters.InstantConverter
import java.time.Instant
import java.util.*

object VaultSchema {

    @Table(name = "vault_transaction_notes")
    @Entity(model = "vault")
    interface VaultTxnNote : Persistable {
        @get:Key
        @get:Generated
        @get:Column(name = "seq_no", index = true)
        var seqNo: Int

        @get:Column(name = "transaction_id", length = 64, index = true)
        var txId: String

        @get:Column(name = "note")
        var note: String
    }

    @Table(name = "vault_cash_balances")
    @Entity(model = "vault")
    interface VaultCashBalances : Persistable {
        @get:Key
        @get:Column(name = "currency_code", length = 3)
        var currency: String

        @get:Column(name = "amount", value = "0")
        var amount: Long
    }

    @Table(name = "vault_states")
    @Entity(model = "vault")
    interface VaultStates : Requery.PersistentState {

        // refers to the notary a state is attached to
        @get:Column(name = "notary_name")
        var notaryName: String

        @get:Column(name = "notary_key")
        var notaryKey: String

        // references a concrete ContractState that is [QueryableState] and has a [MappedSchema]
        @get:Column(name = "contract_state_class_name")
        var contractStateClassName: String

        // reference to serialized transaction Contract State
        @get:Column(name = "contract_state", length = 10000 )
        var contractState: ByteArray

        // state lifecycle: unconsumed, consumed
        @get:Column(name = "state_status")
        var stateStatus: Vault.StateStatus

        // refers to timestamp recorded upon entering UNCONSUMED state
        @get:Column(name = "recorded_timestamp")
        @get:Convert(InstantConverter::class)
        var recordedTime: Instant

        // refers to timestamp recorded upon entering CONSUMED state
        @get:Column(name = "consumed_timestamp")
        @get:Convert(InstantConverter::class)
        var consumedTime: Instant

        // used by denote a state has been soft locked (to prevent double spend)
        @get:Column(name = "lock_id", nullable = true)
        var lockId: String

        // refers to the last time a lock was taken (reserved) or updated (released, re-reserved)
        @get:Column(name = "lock_timestamp")
        @get:Convert(InstantConverter::class)
        var lockUpdateTime: Instant
    }

    @Table(name = "vault_consumed_fungible_states")
    @Entity(model = "vault")
    interface VaultFungibleState : Requery.PersistentState {

        // TODO: 1:1 and 1:m uni-directional relationship mapping code generation not working in Requery
//        @get:OneToMany(mappedBy = "key")
//        var participants: List<VaultKey>

//        @get:OneToOne(mappedBy = "key")
//        var ownerKey: VaultKey
        @get:Column(name = "owner_key")
        var ownerKey: String

        @get:Column(name = "quantity")
        var quantity: Long
        @get:Column(name = "currency", length = 3 )
        var ccyCode: String

//        @get:OneToOne(mappedBy = "key")
//        var issuerKey: VaultKey
        @get:Column(name = "issuer_key")
        var issuerKey: String
        @get:Column(name = "issuer_reference", length = 3 )
        var issuerRef: ByteArray

//        @get:OneToMany(mappedBy = "key")
//        var exitKeys: List<VaultKey>
    }

    @Table(name = "vault_consumed_linear_states")
    @Entity(model = "vault")
    interface VaultLinearState : Requery.PersistentState {

        // TODO: 1:1 and 1:m uni-directional relationship mapping code generation not working in Requery
//        @get:OneToMany(mappedBy = "key")
//        var participants: List<VaultKey>

//        @get:OneToOne(mappedBy = "key")
//        var ownerKey: VaultKey
        @get:Column(name = "owner_key")
        var ownerKey: String

        @get:Index("externalId_index")
        var externalId: String
        @get:Column(length = 36, unique = true, nullable = false)
        var uuid: UUID

        var dealRef: String

//        @get:OneToMany(mappedBy = "name")
//        var dealParties: List<VaultParty>
    }

    @Table(name = "vault_keys")
    // TODO: unused until the 1:1 and 1:m uni-directional relationship mapping code generation is fixed in Requery
    //       see https://github.com/requery/requery/issues/328
    //    @Entity(model = "vault")
    interface VaultKey : Persistable {
        @get:Key
        @get:Generated
        var id: Int

        @get:Key
        @get:Column(length = 255)
        @get:ForeignKey
        var key: String
    }

    @Table(name = "vault_parties")
    // TODO: unused until the 1:1 and 1:m uni-directional relationship mapping code generation is fixed in Requery
    //       see https://github.com/requery/requery/issues/328
//    @Entity(model = "vault")
    interface VaultParty : Persistable {
        @get:Key
        @get:Generated
        var id: Int

        @get:ForeignKey
        @get:Key
        var name: String
        @get:ForeignKey
        @get:Key
        @get:Column(length = 255)
        var key: String
    }
}
