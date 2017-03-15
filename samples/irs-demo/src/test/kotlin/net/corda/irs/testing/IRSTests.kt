package net.corda.irs.testing

import net.corda.core.contracts.*
import net.corda.core.node.recordTransactions
import net.corda.core.seconds
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.DUMMY_NOTARY_KEY
import net.corda.core.utilities.TEST_TX_TIME
import net.corda.irs.contract.*
import net.corda.testing.*
import net.corda.testing.node.MockServices
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

fun createDummyIRS(irsSelect: Int): InterestRateSwap.State {
    return when (irsSelect) {
        1 -> {

            val fixedLeg = InterestRateSwap.FixedLeg(
                    fixedRatePayer = MEGA_CORP.toAnonymous(),
                    notional = 15900000.DOLLARS,
                    paymentFrequency = Frequency.SemiAnnual,
                    effectiveDate = LocalDate.of(2016, 3, 10),
                    effectiveDateAdjustment = null,
                    terminationDate = LocalDate.of(2026, 3, 10),
                    terminationDateAdjustment = null,
                    fixedRate = FixedRate(PercentageRatioUnit("1.677")),
                    dayCountBasisDay = DayCountBasisDay.D30,
                    dayCountBasisYear = DayCountBasisYear.Y360,
                    rollConvention = DateRollConvention.ModifiedFollowing,
                    dayInMonth = 10,
                    paymentRule = PaymentRule.InArrears,
                    paymentDelay = 3,
                    paymentCalendar = BusinessCalendar.getInstance("London", "NewYork"),
                    interestPeriodAdjustment = AccrualAdjustment.Adjusted
            )

            val floatingLeg = InterestRateSwap.FloatingLeg(
                    floatingRatePayer = MINI_CORP.toAnonymous(),
                    notional = 15900000.DOLLARS,
                    paymentFrequency = Frequency.Quarterly,
                    effectiveDate = LocalDate.of(2016, 3, 10),
                    effectiveDateAdjustment = null,
                    terminationDate = LocalDate.of(2026, 3, 10),
                    terminationDateAdjustment = null,
                    dayCountBasisDay = DayCountBasisDay.D30,
                    dayCountBasisYear = DayCountBasisYear.Y360,
                    rollConvention = DateRollConvention.ModifiedFollowing,
                    fixingRollConvention = DateRollConvention.ModifiedFollowing,
                    dayInMonth = 10,
                    resetDayInMonth = 10,
                    paymentRule = PaymentRule.InArrears,
                    paymentDelay = 3,
                    paymentCalendar = BusinessCalendar.getInstance("London", "NewYork"),
                    interestPeriodAdjustment = AccrualAdjustment.Adjusted,
                    fixingPeriodOffset = 2,
                    resetRule = PaymentRule.InAdvance,
                    fixingsPerPayment = Frequency.Quarterly,
                    fixingCalendar = BusinessCalendar.getInstance("London"),
                    index = "LIBOR",
                    indexSource = "TEL3750",
                    indexTenor = Tenor("3M")
            )

            val calculation = InterestRateSwap.Calculation(

                    // TODO: this seems to fail quite dramatically
                    //expression = "fixedLeg.notional * fixedLeg.fixedRate",

                    // TODO: How I want it to look
                    //expression = "( fixedLeg.notional * (fixedLeg.fixedRate)) - (floatingLeg.notional * (rateSchedule.get(context.getDate('currentDate'))))",

                    // How it's ended up looking, which I think is now broken but it's a WIP.
                    expression = Expression("( fixedLeg.notional.pennies * (fixedLeg.fixedRate.ratioUnit.value)) -" +
                            "(floatingLeg.notional.pennies * (calculation.fixingSchedule.get(context.getDate('currentDate')).rate.ratioUnit.value))"),

                    floatingLegPaymentSchedule = HashMap(),
                    fixedLegPaymentSchedule = HashMap()
            )

            val EUR = currency("EUR")

            val common = InterestRateSwap.Common(
                    baseCurrency = EUR,
                    eligibleCurrency = EUR,
                    eligibleCreditSupport = "Cash in an Eligible Currency",
                    independentAmounts = Amount(0, EUR),
                    threshold = Amount(0, EUR),
                    minimumTransferAmount = Amount(250000 * 100, EUR),
                    rounding = Amount(10000 * 100, EUR),
                    valuationDateDescription = "Every Local Business Day",
                    notificationTime = "2:00pm London",
                    resolutionTime = "2:00pm London time on the first LocalBusiness Day following the date on which the notice is given ",
                    interestRate = ReferenceRate("T3270", Tenor("6M"), "EONIA"),
                    addressForTransfers = "",
                    exposure = UnknownType(),
                    localBusinessDay = BusinessCalendar.getInstance("London"),
                    tradeID = "trade1",
                    hashLegalDocs = "put hash here",
                    dailyInterestAmount = Expression("(CashAmount * InterestRate ) / (fixedLeg.notional.currency.currencyCode.equals('GBP')) ? 365 : 360")
            )

            InterestRateSwap.State(fixedLeg = fixedLeg, floatingLeg = floatingLeg, calculation = calculation, common = common)
        }
        2 -> {
            // 10y swap, we pay 1.3% fixed 30/360 semi, rec 3m usd libor act/360 Q on 25m notional (mod foll/adj on both sides)
            // I did a mock up start date 10/03/2015 – 10/03/2025 so you have 5 cashflows on float side that have been preset the rest are unknown

            val fixedLeg = InterestRateSwap.FixedLeg(
                    fixedRatePayer = MEGA_CORP.toAnonymous(),
                    notional = 25000000.DOLLARS,
                    paymentFrequency = Frequency.SemiAnnual,
                    effectiveDate = LocalDate.of(2015, 3, 10),
                    effectiveDateAdjustment = null,
                    terminationDate = LocalDate.of(2025, 3, 10),
                    terminationDateAdjustment = null,
                    fixedRate = FixedRate(PercentageRatioUnit("1.3")),
                    dayCountBasisDay = DayCountBasisDay.D30,
                    dayCountBasisYear = DayCountBasisYear.Y360,
                    rollConvention = DateRollConvention.ModifiedFollowing,
                    dayInMonth = 10,
                    paymentRule = PaymentRule.InArrears,
                    paymentDelay = 0,
                    paymentCalendar = BusinessCalendar.getInstance(),
                    interestPeriodAdjustment = AccrualAdjustment.Adjusted
            )

            val floatingLeg = InterestRateSwap.FloatingLeg(
                    floatingRatePayer = MINI_CORP.toAnonymous(),
                    notional = 25000000.DOLLARS,
                    paymentFrequency = Frequency.Quarterly,
                    effectiveDate = LocalDate.of(2015, 3, 10),
                    effectiveDateAdjustment = null,
                    terminationDate = LocalDate.of(2025, 3, 10),
                    terminationDateAdjustment = null,
                    dayCountBasisDay = DayCountBasisDay.DActual,
                    dayCountBasisYear = DayCountBasisYear.Y360,
                    rollConvention = DateRollConvention.ModifiedFollowing,
                    fixingRollConvention = DateRollConvention.ModifiedFollowing,
                    dayInMonth = 10,
                    resetDayInMonth = 10,
                    paymentRule = PaymentRule.InArrears,
                    paymentDelay = 0,
                    paymentCalendar = BusinessCalendar.getInstance(),
                    interestPeriodAdjustment = AccrualAdjustment.Adjusted,
                    fixingPeriodOffset = 2,
                    resetRule = PaymentRule.InAdvance,
                    fixingsPerPayment = Frequency.Quarterly,
                    fixingCalendar = BusinessCalendar.getInstance(),
                    index = "USD LIBOR",
                    indexSource = "TEL3750",
                    indexTenor = Tenor("3M")
            )

            val calculation = InterestRateSwap.Calculation(

                    // TODO: this seems to fail quite dramatically
                    //expression = "fixedLeg.notional * fixedLeg.fixedRate",

                    // TODO: How I want it to look
                    //expression = "( fixedLeg.notional * (fixedLeg.fixedRate)) - (floatingLeg.notional * (rateSchedule.get(context.getDate('currentDate'))))",

                    // How it's ended up looking, which I think is now broken but it's a WIP.
                    expression = Expression("( fixedLeg.notional.pennies * (fixedLeg.fixedRate.ratioUnit.value)) -" +
                            "(floatingLeg.notional.pennies * (calculation.fixingSchedule.get(context.getDate('currentDate')).rate.ratioUnit.value))"),

                    floatingLegPaymentSchedule = HashMap(),
                    fixedLegPaymentSchedule = HashMap()
            )

            val EUR = currency("EUR")

            val common = InterestRateSwap.Common(
                    baseCurrency = EUR,
                    eligibleCurrency = EUR,
                    eligibleCreditSupport = "Cash in an Eligible Currency",
                    independentAmounts = Amount(0, EUR),
                    threshold = Amount(0, EUR),
                    minimumTransferAmount = Amount(250000 * 100, EUR),
                    rounding = Amount(10000 * 100, EUR),
                    valuationDateDescription = "Every Local Business Day",
                    notificationTime = "2:00pm London",
                    resolutionTime = "2:00pm London time on the first LocalBusiness Day following the date on which the notice is given ",
                    interestRate = ReferenceRate("T3270", Tenor("6M"), "EONIA"),
                    addressForTransfers = "",
                    exposure = UnknownType(),
                    localBusinessDay = BusinessCalendar.getInstance("London"),
                    tradeID = "trade2",
                    hashLegalDocs = "put hash here",
                    dailyInterestAmount = Expression("(CashAmount * InterestRate ) / (fixedLeg.notional.currency.currencyCode.equals('GBP')) ? 365 : 360")
            )

            return InterestRateSwap.State(fixedLeg = fixedLeg, floatingLeg = floatingLeg, calculation = calculation, common = common)

        }
        else -> TODO("IRS number $irsSelect not defined")
    }
}

class IRSTests {
    @Test
    fun ok() {
        trade().verifies()
    }

    @Test
    fun `ok with groups`() {
        tradegroups().verifies()
    }

    /**
     * Generate an IRS txn - we'll need it for a few things.
     */
    fun generateIRSTxn(irsSelect: Int): SignedTransaction {
        val dummyIRS = createDummyIRS(irsSelect)
        val genTX: SignedTransaction = run {
            val gtx = InterestRateSwap().generateAgreement(
                    fixedLeg = dummyIRS.fixedLeg,
                    floatingLeg = dummyIRS.floatingLeg,
                    calculation = dummyIRS.calculation,
                    common = dummyIRS.common,
                    notary = DUMMY_NOTARY).apply {
                setTime(TEST_TX_TIME, 30.seconds)
                signWith(MEGA_CORP_KEY)
                signWith(MINI_CORP_KEY)
                signWith(DUMMY_NOTARY_KEY)
            }
            gtx.toSignedTransaction()
        }
        return genTX
    }

    /**
     * Just make sure it's sane.
     */
    @Test
    fun pprintIRS() {
        val irs = singleIRS()
        println(irs.prettyPrint())
    }

    /**
     * Utility so I don't have to keep typing this.
     */
    fun singleIRS(irsSelector: Int = 1): InterestRateSwap.State {
        return generateIRSTxn(irsSelector).tx.outputs.map { it.data }.filterIsInstance<InterestRateSwap.State>().single()
    }

    /**
     * Test the generate. No explicit exception as if something goes wrong, we'll find out anyway.
     */
    @Test
    fun generateIRS() {
        // Tests aren't allowed to return things
        generateIRSTxn(1)
    }

    /**
     * Testing a simple IRS, add a few fixings and then display as CSV.
     */
    @Test
    fun `IRS Export test`() {
        // No transactions etc required - we're just checking simple maths and export functionallity
        val irs = singleIRS(2)

        var newCalculation = irs.calculation

        val fixings = mapOf(LocalDate.of(2015, 3, 6) to "0.6",
                LocalDate.of(2015, 6, 8) to "0.75",
                LocalDate.of(2015, 9, 8) to "0.8",
                LocalDate.of(2015, 12, 8) to "0.55",
                LocalDate.of(2016, 3, 8) to "0.644")

        for ((key, value) in fixings) {
            newCalculation = newCalculation.applyFixing(key, FixedRate(PercentageRatioUnit(value)))
        }

        val newIRS = InterestRateSwap.State(irs.fixedLeg, irs.floatingLeg, newCalculation, irs.common)
        println(newIRS.exportIRSToCSV())
    }

    /**
     * Make sure it has a schedule and the schedule has some unfixed rates.
     */
    @Test
    fun `next fixing date`() {
        val irs = singleIRS(1)
        println(irs.calculation.nextFixingDate())
    }

    /**
     * Iterate through all the fix dates and add something.
     */
    @Test
    fun generateIRSandFixSome() {
        val services = MockServices()
        var previousTXN = generateIRSTxn(1)
        previousTXN.toLedgerTransaction(services).verify()
        services.recordTransactions(previousTXN)
        fun currentIRS() = previousTXN.tx.outputs.map { it.data }.filterIsInstance<InterestRateSwap.State>().single()

        while (true) {
            val nextFix: FixOf = currentIRS().nextFixingOf() ?: break
            val fixTX: SignedTransaction = run {
                val tx = TransactionType.General.Builder(DUMMY_NOTARY)
                val fixing = Fix(nextFix, "0.052".percent.value)
                InterestRateSwap().generateFix(tx, previousTXN.tx.outRef(0), fixing)
                with(tx) {
                    setTime(TEST_TX_TIME, 30.seconds)
                    signWith(MEGA_CORP_KEY)
                    signWith(MINI_CORP_KEY)
                    signWith(DUMMY_NOTARY_KEY)
                }
                tx.toSignedTransaction()
            }
            fixTX.toLedgerTransaction(services).verify()
            services.recordTransactions(fixTX)
            previousTXN = fixTX
        }
    }

    // Move these later as they aren't IRS specific.
    @Test
    fun `test some rate objects 100 * FixedRate(5%)`() {
        val r1 = FixedRate(PercentageRatioUnit("5"))
        assert(100 * r1 == 5)
    }

    @Test
    fun `expression calculation testing`() {
        val dummyIRS = singleIRS()
        val stuffToPrint: ArrayList<String> = arrayListOf(
                "fixedLeg.notional.quantity",
                "fixedLeg.fixedRate.ratioUnit",
                "fixedLeg.fixedRate.ratioUnit.value",
                "floatingLeg.notional.quantity",
                "fixedLeg.fixedRate",
                "currentBusinessDate",
                "calculation.floatingLegPaymentSchedule.get(currentBusinessDate)",
                "fixedLeg.notional.token.currencyCode",
                "fixedLeg.notional.quantity * 10",
                "fixedLeg.notional.quantity * fixedLeg.fixedRate.ratioUnit.value",
                "(fixedLeg.notional.token.currencyCode.equals('GBP')) ? 365 : 360 ",
                "(fixedLeg.notional.quantity * (fixedLeg.fixedRate.ratioUnit.value))"
                // "calculation.floatingLegPaymentSchedule.get(context.getDate('currentDate')).rate"
                // "calculation.floatingLegPaymentSchedule.get(context.getDate('currentDate')).rate.ratioUnit.value",
                //"( fixedLeg.notional.pennies * (fixedLeg.fixedRate.ratioUnit.value)) - (floatingLeg.notional.pennies * (calculation.fixingSchedule.get(context.getDate('currentDate')).rate.ratioUnit.value))",
                // "( fixedLeg.notional * fixedLeg.fixedRate )"
        )

        for (i in stuffToPrint) {
            println(i)
            val z = dummyIRS.evaluateCalculation(LocalDate.of(2016, 9, 15), Expression(i))
            println(z.javaClass)
            println(z)
            println("-----------")
        }
        // This does not throw an exception in the test itself; it evaluates the above and they will throw if they do not pass.
    }


    /**
     * Generates a typical transactional history for an IRS.
     */
    fun trade(): LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter> {

        val ld = LocalDate.of(2016, 3, 8)
        val bd = BigDecimal("0.0063518")

        return ledger {
            transaction("Agreement") {
                output("irs post agreement") { singleIRS() }
                command(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
                timestamp(TEST_TX_TIME)
                this.verifies()
            }

            transaction("Fix") {
                input("irs post agreement")
                val postAgreement = "irs post agreement".output<InterestRateSwap.State>()
                output("irs post first fixing") {
                    postAgreement.copy(
                            postAgreement.fixedLeg,
                            postAgreement.floatingLeg,
                            postAgreement.calculation.applyFixing(ld, FixedRate(RatioUnit(bd))),
                            postAgreement.common
                    )
                }
                command(ORACLE_PUBKEY) {
                    InterestRateSwap.Commands.Refix(Fix(FixOf("ICE LIBOR", ld, Tenor("3M")), bd))
                }
                timestamp(TEST_TX_TIME)
                this.verifies()
            }
        }
    }

    @Test
    fun `ensure failure occurs when there are inbound states for an agreement command`() {
        val irs = singleIRS()
        transaction {
            input() { irs }
            output("irs post agreement") { irs }
            command(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
            timestamp(TEST_TX_TIME)
            this `fails with` "There are no in states for an agreement"
        }
    }

    @Test
    fun `ensure failure occurs when no events in fix schedule`() {
        val irs = singleIRS()
        val emptySchedule = HashMap<LocalDate, FixedRatePaymentEvent>()
        transaction {
            output() {
                irs.copy(calculation = irs.calculation.copy(fixedLegPaymentSchedule = emptySchedule))
            }
            command(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
            timestamp(TEST_TX_TIME)
            this `fails with` "There are events in the fix schedule"
        }
    }

    @Test
    fun `ensure failure occurs when no events in floating schedule`() {
        val irs = singleIRS()
        val emptySchedule = HashMap<LocalDate, FloatingRatePaymentEvent>()
        transaction {
            output() {
                irs.copy(calculation = irs.calculation.copy(floatingLegPaymentSchedule = emptySchedule))
            }
            command(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
            timestamp(TEST_TX_TIME)
            this `fails with` "There are events in the float schedule"
        }
    }

    @Test
    fun `ensure notionals are non zero`() {
        val irs = singleIRS()
        transaction {
            output() {
                irs.copy(irs.fixedLeg.copy(notional = irs.fixedLeg.notional.copy(quantity = 0)))
            }
            command(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
            timestamp(TEST_TX_TIME)
            this `fails with` "All notionals must be non zero"
        }

        transaction {
            output() {
                irs.copy(irs.fixedLeg.copy(notional = irs.floatingLeg.notional.copy(quantity = 0)))
            }
            command(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
            timestamp(TEST_TX_TIME)
            this `fails with` "All notionals must be non zero"
        }
    }

    @Test
    fun `ensure positive rate on fixed leg`() {
        val irs = singleIRS()
        val modifiedIRS = irs.copy(fixedLeg = irs.fixedLeg.copy(fixedRate = FixedRate(PercentageRatioUnit("-0.1"))))
        transaction {
            output() {
                modifiedIRS
            }
            command(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
            timestamp(TEST_TX_TIME)
            this `fails with` "The fixed leg rate must be positive"
        }
    }

    /**
     * This will be modified once we adapt the IRS to be cross currency.
     */
    @Test
    fun `ensure same currency notionals`() {
        val irs = singleIRS()
        val modifiedIRS = irs.copy(fixedLeg = irs.fixedLeg.copy(notional = Amount(irs.fixedLeg.notional.quantity, Currency.getInstance("JPY"))))
        transaction {
            output() {
                modifiedIRS
            }
            command(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
            timestamp(TEST_TX_TIME)
            this `fails with` "The currency of the notionals must be the same"
        }
    }

    @Test
    fun `ensure notional amounts are equal`() {
        val irs = singleIRS()
        val modifiedIRS = irs.copy(fixedLeg = irs.fixedLeg.copy(notional = Amount(irs.floatingLeg.notional.quantity + 1, irs.floatingLeg.notional.token)))
        transaction {
            output() {
                modifiedIRS
            }
            command(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
            timestamp(TEST_TX_TIME)
            this `fails with` "All leg notionals must be the same"
        }
    }

    @Test
    fun `ensure trade date and termination date checks are done pt1`() {
        val irs = singleIRS()
        val modifiedIRS1 = irs.copy(fixedLeg = irs.fixedLeg.copy(terminationDate = irs.fixedLeg.effectiveDate.minusDays(1)))
        transaction {
            output() {
                modifiedIRS1
            }
            command(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
            timestamp(TEST_TX_TIME)
            this `fails with` "The effective date is before the termination date for the fixed leg"
        }

        val modifiedIRS2 = irs.copy(floatingLeg = irs.floatingLeg.copy(terminationDate = irs.floatingLeg.effectiveDate.minusDays(1)))
        transaction {
            output() {
                modifiedIRS2
            }
            command(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
            timestamp(TEST_TX_TIME)
            this `fails with` "The effective date is before the termination date for the floating leg"
        }
    }

    @Test
    fun `ensure trade date and termination date checks are done pt2`() {
        val irs = singleIRS()

        val modifiedIRS3 = irs.copy(floatingLeg = irs.floatingLeg.copy(terminationDate = irs.fixedLeg.terminationDate.minusDays(1)))
        transaction {
            output() {
                modifiedIRS3
            }
            command(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
            timestamp(TEST_TX_TIME)
            this `fails with` "The termination dates are aligned"
        }


        val modifiedIRS4 = irs.copy(floatingLeg = irs.floatingLeg.copy(effectiveDate = irs.fixedLeg.effectiveDate.minusDays(1)))
        transaction {
            output() {
                modifiedIRS4
            }
            command(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
            timestamp(TEST_TX_TIME)
            this `fails with` "The effective dates are aligned"
        }
    }


    @Test
    fun `various fixing tests`() {
        val ld = LocalDate.of(2016, 3, 8)
        val bd = BigDecimal("0.0063518")

        transaction {
            output("irs post agreement") { singleIRS() }
            command(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
            timestamp(TEST_TX_TIME)
            this.verifies()
        }

        val oldIRS = singleIRS(1)
        val newIRS = oldIRS.copy(oldIRS.fixedLeg,
                oldIRS.floatingLeg,
                oldIRS.calculation.applyFixing(ld, FixedRate(RatioUnit(bd))),
                oldIRS.common)

        transaction {
            input() {
                oldIRS

            }

            // Templated tweak for reference. A corrent fixing applied should be ok
            tweak {
                command(ORACLE_PUBKEY) {
                    InterestRateSwap.Commands.Refix(Fix(FixOf("ICE LIBOR", ld, Tenor("3M")), bd))
                }
                timestamp(TEST_TX_TIME)
                output() { newIRS }
                this.verifies()
            }

            // This test makes sure that verify confirms the fixing was applied and there is a difference in the old and new
            tweak {
                command(ORACLE_PUBKEY) { InterestRateSwap.Commands.Refix(Fix(FixOf("ICE LIBOR", ld, Tenor("3M")), bd)) }
                timestamp(TEST_TX_TIME)
                output() { oldIRS }
                this `fails with` "There is at least one difference in the IRS floating leg payment schedules"
            }

            // This tests tries to sneak in a change to another fixing (which may or may not be the latest one)
            tweak {
                command(ORACLE_PUBKEY) { InterestRateSwap.Commands.Refix(Fix(FixOf("ICE LIBOR", ld, Tenor("3M")), bd)) }
                timestamp(TEST_TX_TIME)

                val firstResetKey = newIRS.calculation.floatingLegPaymentSchedule.keys.toList()[1]
                val firstResetValue = newIRS.calculation.floatingLegPaymentSchedule[firstResetKey]
                val modifiedFirstResetValue = firstResetValue!!.copy(notional = Amount(firstResetValue.notional.quantity, Currency.getInstance("JPY")))

                output() {
                    newIRS.copy(
                            newIRS.fixedLeg,
                            newIRS.floatingLeg,
                            newIRS.calculation.copy(floatingLegPaymentSchedule = newIRS.calculation.floatingLegPaymentSchedule.plus(
                                    Pair(firstResetKey, modifiedFirstResetValue))),
                            newIRS.common
                    )
                }
                this `fails with` "There is only one change in the IRS floating leg payment schedule"
            }

            // This tests modifies the payment currency for the fixing
            tweak {
                command(ORACLE_PUBKEY) { InterestRateSwap.Commands.Refix(Fix(FixOf("ICE LIBOR", ld, Tenor("3M")), bd)) }
                timestamp(TEST_TX_TIME)

                val latestReset = newIRS.calculation.floatingLegPaymentSchedule.filter { it.value.rate is FixedRate }.maxBy { it.key }
                val modifiedLatestResetValue = latestReset!!.value.copy(notional = Amount(latestReset.value.notional.quantity, Currency.getInstance("JPY")))

                output() {
                    newIRS.copy(
                            newIRS.fixedLeg,
                            newIRS.floatingLeg,
                            newIRS.calculation.copy(floatingLegPaymentSchedule = newIRS.calculation.floatingLegPaymentSchedule.plus(
                                    Pair(latestReset.key, modifiedLatestResetValue))),
                            newIRS.common
                    )
                }
                this `fails with` "The fix payment has the same currency as the notional"
            }
        }
    }


    /**
     * This returns an example of transactions that are grouped by TradeId and then a fixing applied.
     * It's important to make the tradeID different for two reasons, the hashes will be the same and all sorts of confusion will
     * result and the grouping won't work either.
     * In reality, the only fields that should be in common will be the next fixing date and the reference rate.
     */
    fun tradegroups(): LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter> {
        val ld1 = LocalDate.of(2016, 3, 8)
        val bd1 = BigDecimal("0.0063518")

        val irs = singleIRS()

        return ledger {
            transaction("Agreement") {
                output("irs post agreement1") {
                    irs.copy(
                            irs.fixedLeg,
                            irs.floatingLeg,
                            irs.calculation,
                            irs.common.copy(tradeID = "t1")
                    )
                }
                command(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
                timestamp(TEST_TX_TIME)
                this.verifies()
            }

            transaction("Agreement") {
                output("irs post agreement2") {
                    irs.copy(
                            linearId = UniqueIdentifier("t2"),
                            fixedLeg = irs.fixedLeg,
                            floatingLeg = irs.floatingLeg,
                            calculation = irs.calculation,
                            common = irs.common.copy(tradeID = "t2")
                    )
                }
                command(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
                timestamp(TEST_TX_TIME)
                this.verifies()
            }

            transaction("Fix") {
                input("irs post agreement1")
                input("irs post agreement2")
                val postAgreement1 = "irs post agreement1".output<InterestRateSwap.State>()
                output("irs post first fixing1") {
                    postAgreement1.copy(
                            postAgreement1.fixedLeg,
                            postAgreement1.floatingLeg,
                            postAgreement1.calculation.applyFixing(ld1, FixedRate(RatioUnit(bd1))),
                            postAgreement1.common.copy(tradeID = "t1")
                    )
                }
                val postAgreement2 = "irs post agreement2".output<InterestRateSwap.State>()
                output("irs post first fixing2") {
                    postAgreement2.copy(
                            postAgreement2.fixedLeg,
                            postAgreement2.floatingLeg,
                            postAgreement2.calculation.applyFixing(ld1, FixedRate(RatioUnit(bd1))),
                            postAgreement2.common.copy(tradeID = "t2")
                    )
                }

                command(ORACLE_PUBKEY) {
                    InterestRateSwap.Commands.Refix(Fix(FixOf("ICE LIBOR", ld1, Tenor("3M")), bd1))
                }
                timestamp(TEST_TX_TIME)
                this.verifies()
            }
        }
    }
}


