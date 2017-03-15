package net.corda.traderdemo

import com.google.common.util.concurrent.Futures
import net.corda.contracts.testing.calculateRandomlySizedAmounts
import net.corda.core.contracts.Amount
import net.corda.core.contracts.DOLLARS
import net.corda.core.getOrThrow
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.utilities.Emoji
import net.corda.core.utilities.loggerFor
import net.corda.flows.IssuerFlow.IssuanceRequester
import net.corda.testing.BOC
import net.corda.traderdemo.flow.SellerFlow
import java.util.*
import kotlin.test.assertEquals

/**
 * Interface for communicating with nodes running the trader demo.
 */
class TraderDemoClientApi(val rpc: CordaRPCOps) {
    private companion object {
        val logger = loggerFor<TraderDemoClientApi>()
    }

    fun runBuyer(amount: Amount<Currency> = 30000.0.DOLLARS) {
        val bankOfCordaParty = rpc.partyFromName(BOC.name)
                ?: throw Exception("Unable to locate ${BOC.name} in Network Map Service")
        val me = rpc.nodeIdentity()
        // TODO: revert back to multiple issue request amounts (3,10) when soft locking implemented
        val amounts = calculateRandomlySizedAmounts(amount, 1, 1, Random())
        val resultFutures = amounts.map {
            rpc.startFlow(::IssuanceRequester, amount, me.legalIdentity, OpaqueBytes.of(1), bankOfCordaParty).returnValue
        }

        Futures.allAsList(resultFutures).getOrThrow()
    }

    fun runSeller(amount: Amount<Currency> = 1000.0.DOLLARS, counterparty: String) {
        val otherParty = rpc.partyFromName(counterparty) ?: throw IllegalStateException("Don't know $counterparty")
        // The seller will sell some commercial paper to the buyer, who will pay with (self issued) cash.
        //
        // The CP sale transaction comes with a prospectus PDF, which will tag along for the ride in an
        // attachment. Make sure we have the transaction prospectus attachment loaded into our store.
        //
        // This can also be done via an HTTP upload, but here we short-circuit and do it from code.
        if (!rpc.attachmentExists(SellerFlow.PROSPECTUS_HASH)) {
            javaClass.classLoader.getResourceAsStream("bank-of-london-cp.jar").use {
                val id = rpc.uploadAttachment(it)
                assertEquals(SellerFlow.PROSPECTUS_HASH, id)
            }
        }

        // The line below blocks and waits for the future to resolve.
        val stx = rpc.startFlow(::SellerFlow, otherParty, amount).returnValue.getOrThrow()
        logger.info("Sale completed - we have a happy customer!\n\nFinal transaction is:\n\n${Emoji.renderIfSupported(stx.tx)}")
    }
}
