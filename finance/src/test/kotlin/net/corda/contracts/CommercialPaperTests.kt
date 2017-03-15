package net.corda.contracts

import net.corda.contracts.asset.*
import net.corda.contracts.testing.fillWithSomeTestCash
import net.corda.core.contracts.*
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.composite
import net.corda.core.days
import net.corda.core.node.recordTransactions
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.seconds
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.DUMMY_NOTARY_KEY
import net.corda.core.utilities.DUMMY_PUBKEY_1
import net.corda.core.utilities.TEST_TX_TIME
import net.corda.node.services.vault.NodeVaultService
import net.corda.node.utilities.configureDatabase
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.*
import net.corda.testing.node.MockServices
import net.corda.testing.node.makeTestDataSourceProperties
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Instant
import java.util.*
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// TODO: The generate functions aren't tested by these tests: add them.

interface ICommercialPaperTestTemplate {
    fun getPaper(): ICommercialPaperState
    fun getIssueCommand(notary: Party): CommandData
    fun getRedeemCommand(notary: Party): CommandData
    fun getMoveCommand(): CommandData
}

class JavaCommercialPaperTest() : ICommercialPaperTestTemplate {
    override fun getPaper(): ICommercialPaperState = JavaCommercialPaper.State(
            MEGA_CORP.ref(123),
            MEGA_CORP_PUBKEY,
            1000.DOLLARS `issued by` MEGA_CORP.ref(123),
            TEST_TX_TIME + 7.days
    )

    override fun getIssueCommand(notary: Party): CommandData = JavaCommercialPaper.Commands.Issue()
    override fun getRedeemCommand(notary: Party): CommandData = JavaCommercialPaper.Commands.Redeem()
    override fun getMoveCommand(): CommandData = JavaCommercialPaper.Commands.Move()
}

class KotlinCommercialPaperTest() : ICommercialPaperTestTemplate {
    override fun getPaper(): ICommercialPaperState = CommercialPaper.State(
            issuance = MEGA_CORP.ref(123),
            owner = MEGA_CORP_PUBKEY,
            faceValue = 1000.DOLLARS `issued by` MEGA_CORP.ref(123),
            maturityDate = TEST_TX_TIME + 7.days
    )

    override fun getIssueCommand(notary: Party): CommandData = CommercialPaper.Commands.Issue()
    override fun getRedeemCommand(notary: Party): CommandData = CommercialPaper.Commands.Redeem()
    override fun getMoveCommand(): CommandData = CommercialPaper.Commands.Move()
}

class KotlinCommercialPaperLegacyTest() : ICommercialPaperTestTemplate {
    override fun getPaper(): ICommercialPaperState = CommercialPaperLegacy.State(
            issuance = MEGA_CORP.ref(123),
            owner = MEGA_CORP_PUBKEY,
            faceValue = 1000.DOLLARS `issued by` MEGA_CORP.ref(123),
            maturityDate = TEST_TX_TIME + 7.days
    )

    override fun getIssueCommand(notary: Party): CommandData = CommercialPaperLegacy.Commands.Issue()
    override fun getRedeemCommand(notary: Party): CommandData = CommercialPaperLegacy.Commands.Redeem()
    override fun getMoveCommand(): CommandData = CommercialPaperLegacy.Commands.Move()
}

@RunWith(Parameterized::class)
class CommercialPaperTestsGeneric {
    companion object {
        @Parameterized.Parameters @JvmStatic
        fun data() = listOf(JavaCommercialPaperTest(), KotlinCommercialPaperTest(), KotlinCommercialPaperLegacyTest())
    }

    @Parameterized.Parameter
    lateinit var thisTest: ICommercialPaperTestTemplate

    val issuer = MEGA_CORP.ref(123)

    @Test
    fun `trade lifecycle test`() {
        val someProfits = 1200.DOLLARS `issued by` issuer
        ledger {
            unverifiedTransaction {
                output("alice's $900", 900.DOLLARS.CASH `issued by` issuer `owned by` ALICE_PUBKEY)
                output("some profits", someProfits.STATE `owned by` MEGA_CORP_PUBKEY)
            }

            // Some CP is issued onto the ledger by MegaCorp.
            transaction("Issuance") {
                output("paper") { thisTest.getPaper() }
                command(MEGA_CORP_PUBKEY) { thisTest.getIssueCommand(DUMMY_NOTARY) }
                timestamp(TEST_TX_TIME)
                this.verifies()
            }

            // The CP is sold to alice for her $900, $100 less than the face value. At 10% interest after only 7 days,
            // that sounds a bit too good to be true!
            transaction("Trade") {
                input("paper")
                input("alice's $900")
                output("borrowed $900") { 900.DOLLARS.CASH `issued by` issuer `owned by` MEGA_CORP_PUBKEY }
                output("alice's paper") { "paper".output<ICommercialPaperState>() `owned by` ALICE_PUBKEY }
                command(ALICE_PUBKEY) { Cash.Commands.Move() }
                command(MEGA_CORP_PUBKEY) { thisTest.getMoveCommand() }
                this.verifies()
            }

            // Time passes, and Alice redeem's her CP for $1000, netting a $100 profit. MegaCorp has received $1200
            // as a single payment from somewhere and uses it to pay Alice off, keeping the remaining $200 as change.
            transaction("Redemption") {
                input("alice's paper")
                input("some profits")

                fun TransactionDSL<TransactionDSLInterpreter>.outputs(aliceGetsBack: Amount<Issued<Currency>>) {
                    output("Alice's profit") { aliceGetsBack.STATE `owned by` ALICE_PUBKEY }
                    output("Change") { (someProfits - aliceGetsBack).STATE `owned by` MEGA_CORP_PUBKEY }
                }

                command(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
                command(ALICE_PUBKEY) { thisTest.getRedeemCommand(DUMMY_NOTARY) }

                tweak {
                    outputs(700.DOLLARS `issued by` issuer)
                    timestamp(TEST_TX_TIME + 8.days)
                    this `fails with` "received amount equals the face value"
                }
                outputs(1000.DOLLARS `issued by` issuer)


                tweak {
                    timestamp(TEST_TX_TIME + 2.days)
                    this `fails with` "must have matured"
                }
                timestamp(TEST_TX_TIME + 8.days)

                tweak {
                    output { "paper".output<ICommercialPaperState>() }
                    this `fails with` "must be destroyed"
                }

                this.verifies()
            }
        }
    }

    @Test
    fun `key mismatch at issue`() {
        transaction {
            output { thisTest.getPaper() }
            command(DUMMY_PUBKEY_1) { thisTest.getIssueCommand(DUMMY_NOTARY) }
            timestamp(TEST_TX_TIME)
            this `fails with` "output states are issued by a command signer"
        }
    }

    @Test
    fun `face value is not zero`() {
        transaction {
            output { thisTest.getPaper().withFaceValue(0.DOLLARS `issued by` issuer) }
            command(MEGA_CORP_PUBKEY) { thisTest.getIssueCommand(DUMMY_NOTARY) }
            timestamp(TEST_TX_TIME)
            this `fails with` "output values sum to more than the inputs"
        }
    }

    @Test
    fun `maturity date not in the past`() {
        transaction {
            output { thisTest.getPaper().withMaturityDate(TEST_TX_TIME - 10.days) }
            command(MEGA_CORP_PUBKEY) { thisTest.getIssueCommand(DUMMY_NOTARY) }
            timestamp(TEST_TX_TIME)
            this `fails with` "maturity date is not in the past"
        }
    }

    @Test
    fun `issue cannot replace an existing state`() {
        transaction {
            input(thisTest.getPaper())
            output { thisTest.getPaper() }
            command(MEGA_CORP_PUBKEY) { thisTest.getIssueCommand(DUMMY_NOTARY) }
            timestamp(TEST_TX_TIME)
            this `fails with` "output values sum to more than the inputs"
        }
    }

    fun cashOutputsToVault(vararg outputs: TransactionState<Cash.State>): Pair<LedgerTransaction, List<StateAndRef<Cash.State>>> {
        val ltx = LedgerTransaction(emptyList(), listOf(*outputs), emptyList(), emptyList(), SecureHash.randomSHA256(), null, emptyList(), null, TransactionType.General())
        return Pair(ltx, outputs.mapIndexed { index, state -> StateAndRef(state, StateRef(ltx.id, index)) })
    }

    /**
     *  Unit test requires two separate Database instances to represent each of the two
     *  transaction participants (enforces uniqueness of vault content in lieu of partipant identity)
     */

    private lateinit var bigCorpServices: MockServices
    private lateinit var bigCorpVault: Vault<ContractState>
    private lateinit var bigCorpVaultService: VaultService

    private lateinit var aliceServices: MockServices
    private lateinit var aliceVaultService: VaultService
    private lateinit var alicesVault: Vault<ContractState>

    private lateinit var moveTX: SignedTransaction

    @Test
    fun `issue move and then redeem`() {

        val dataSourcePropsAlice = makeTestDataSourceProperties()
        val dataSourceAndDatabaseAlice = configureDatabase(dataSourcePropsAlice)
        val databaseAlice = dataSourceAndDatabaseAlice.second
        databaseTransaction(databaseAlice) {

            aliceServices = object : MockServices() {
                override val vaultService: VaultService = NodeVaultService(this, dataSourcePropsAlice)

                override fun recordTransactions(txs: Iterable<SignedTransaction>) {
                    for (stx in txs) {
                        storageService.validatedTransactions.addTransaction(stx)
                    }
                    // Refactored to use notifyAll() as we have no other unit test for that method with multiple transactions.
                    vaultService.notifyAll(txs.map { it.tx })
                }
            }
            alicesVault = aliceServices.fillWithSomeTestCash(9000.DOLLARS, atLeastThisManyStates = 1, atMostThisManyStates = 1)
            aliceVaultService = aliceServices.vaultService
        }

        val dataSourcePropsBigCorp = makeTestDataSourceProperties()
        val dataSourceAndDatabaseBigCorp = configureDatabase(dataSourcePropsBigCorp)
        val databaseBigCorp = dataSourceAndDatabaseBigCorp.second
        databaseTransaction(databaseBigCorp) {

            bigCorpServices = object : MockServices() {
                override val vaultService: VaultService = NodeVaultService(this, dataSourcePropsBigCorp)

                override fun recordTransactions(txs: Iterable<SignedTransaction>) {
                    for (stx in txs) {
                        storageService.validatedTransactions.addTransaction(stx)
                    }
                    // Refactored to use notifyAll() as we have no other unit test for that method with multiple transactions.
                    vaultService.notifyAll(txs.map { it.tx })
                }
            }
            bigCorpVault = bigCorpServices.fillWithSomeTestCash(13000.DOLLARS, atLeastThisManyStates = 1, atMostThisManyStates = 1)
            bigCorpVaultService = bigCorpServices.vaultService
        }

        // Propagate the cash transactions to each side.
        aliceServices.recordTransactions(bigCorpVault.states.map { bigCorpServices.storageService.validatedTransactions.getTransaction(it.ref.txhash)!! })
        bigCorpServices.recordTransactions(alicesVault.states.map { aliceServices.storageService.validatedTransactions.getTransaction(it.ref.txhash)!! })

        // BigCorp™ issues $10,000 of commercial paper, to mature in 30 days, owned initially by itself.
        val faceValue = 10000.DOLLARS `issued by` DUMMY_CASH_ISSUER
        val issuance = bigCorpServices.myInfo.legalIdentity.ref(1)
        val issueTX: SignedTransaction =
                CommercialPaper().generateIssue(issuance, faceValue, TEST_TX_TIME + 30.days, DUMMY_NOTARY).apply {
                    setTime(TEST_TX_TIME, 30.seconds)
                    signWith(bigCorpServices.key)
                    signWith(DUMMY_NOTARY_KEY)
                }.toSignedTransaction()

        databaseTransaction(databaseAlice) {
            // Alice pays $9000 to BigCorp to own some of their debt.
            moveTX = run {
                val ptx = TransactionType.General.Builder(DUMMY_NOTARY)
                aliceVaultService.generateSpend(ptx, 9000.DOLLARS, bigCorpServices.key.public.composite)
                CommercialPaper().generateMove(ptx, issueTX.tx.outRef(0), aliceServices.key.public.composite)
                ptx.signWith(bigCorpServices.key)
                ptx.signWith(aliceServices.key)
                ptx.signWith(DUMMY_NOTARY_KEY)
                ptx.toSignedTransaction()
            }
        }

        databaseTransaction(databaseBigCorp) {
            fun makeRedeemTX(time: Instant): SignedTransaction {
                val ptx = TransactionType.General.Builder(DUMMY_NOTARY)
                ptx.setTime(time, 30.seconds)
                CommercialPaper().generateRedeem(ptx, moveTX.tx.outRef(1), bigCorpVaultService)
                ptx.signWith(aliceServices.key)
                ptx.signWith(bigCorpServices.key)
                ptx.signWith(DUMMY_NOTARY_KEY)
                return ptx.toSignedTransaction()
            }

            val tooEarlyRedemption = makeRedeemTX(TEST_TX_TIME + 10.days)
            val validRedemption = makeRedeemTX(TEST_TX_TIME + 31.days)

            // Verify the txns are valid and insert into both sides.
            listOf(issueTX, moveTX).forEach {
                it.toLedgerTransaction(aliceServices).verify()
                aliceServices.recordTransactions(it)
                bigCorpServices.recordTransactions(it)
            }

            val e = assertFailsWith(TransactionVerificationException::class) {
                tooEarlyRedemption.toLedgerTransaction(aliceServices).verify()
            }
            assertTrue(e.cause!!.message!!.contains("paper must have matured"))

            validRedemption.toLedgerTransaction(aliceServices).verify()
        }
    }
}
