package net.corda.loadtest

import kotlinx.support.jdk8.collections.parallelStream
import net.corda.client.mock.Generator
import net.corda.core.div
import net.corda.node.driver.PortAllocation
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.transactions.ValidatingNotaryService
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

private val log = LoggerFactory.getLogger(LoadTest::class.java)

/**
 * @param T The type of generated object in the load test. This should describe the basic unit of execution, for example
 *     a single transaction to execute.
 * @param S The type of state that describes the state of the load test, for example a hashmap of vaults. Note that this
 *     most probably won't be the actual vault, because that includes [StateRef]s which we cannot predict in advance
 *     in [interpret] due to usage of nonces.
 * @param generate Generator function for [T]s. (e.g. generate payment transactions of random quantity). It takes as
 *     input a number indicating around how many objects it should generate. This need not be the case, but the generator
 *     must generate the objects so that they run consistently when executed in parallel. (e.g. if Alice has 100 USD we
 *     cannot generate two Spend(80 USD) txs, even though individually they are consistent).
 * @param interpret A pure function that applies the generated object to the abstract state. (e.g. subtract/add payment
 *     quantity to relevant vaults)
 * @param execute A function that executes the generated object by executing IO (e.g. make RPC call to execute tx).
 * @param gatherRemoteState A function that assembles the abstract state from the real world (e.g. by getting snapshots
 *     from nodes) and the current simulated state. When run the simulated state will be replaced by the returned value.
 *     It should throw an exception if a divergence from the expected state is detected.
 * @param isConsistent Should be specified if the abstract state tracks non-determinism, in which case it should return
 *     false if the state is not yet consistent, true otherwise. The final convergence check will poll this value on
 *     gathered states.
 *
 * TODO Perhaps an interface would be more idiomatic here
 */
// DOCS START 1
data class LoadTest<T, S>(
        val testName: String,
        val generate: Nodes.(S, Int) -> Generator<List<T>>,
        val interpret: (S, T) -> S,
        val execute: Nodes.(T) -> Unit,
        val gatherRemoteState: Nodes.(S?) -> S,
        val isConsistent: (S) -> Boolean = { true }
) {
// DOCS END 1

    // DOCS START 2
    /**
     * @param parallelism Number of concurrent threads to use to run commands. Note that the actual parallelism may be
     *     further limited by the batches that [generate] returns.
     * @param generateCount Number of total commands to generate. Note that the actual number of generated commands may
     *     exceed this, it is used just for cutoff.
     * @param clearDatabaseBeforeRun Indicates whether the node databases should be cleared before running the test. May
     *     significantly slow down testing as this requires bringing the nodes down and up again.
     * @param gatherFrequency Indicates after how many commands we should gather the remote states.
     * @param disruptionPatterns A list of disruption-lists. The test will be run for each such list, and the test will
     *     be interleaved with the specified disruptions.
     */
    data class RunParameters(
            val parallelism: Int,
            val generateCount: Int,
            val clearDatabaseBeforeRun: Boolean,
            val gatherFrequency: Int,
            val disruptionPatterns: List<List<DisruptionSpec>>
    )
    // DOCS END 2

    fun run(nodes: Nodes, parameters: RunParameters, random: SplittableRandom) {
        log.info("Running '$testName' with parameters $parameters")
        if (parameters.clearDatabaseBeforeRun) {
            log.info("Clearing databases as clearDatabaseBeforeRun=true")
            // We need to clear the network map first so that other nodes register fine
            nodes.networkMap.clearDb()
            (nodes.simpleNodes + listOf(nodes.notary)).parallelStream().forEach {
                it.clearDb()
            }
        }

        parameters.disruptionPatterns.forEach { disruptions ->
            log.info("Running test '$testName' with disruptions ${disruptions.map { it.disruption.name }}")
            nodes.withDisruptions(disruptions, random) {
                var state = nodes.gatherRemoteState(null)
                var count = parameters.generateCount
                var countSinceLastCheck = 0
                while (count > 0) {
                    log.info("$count remaining commands, state:\n$state")
                    // Generate commands
                    val commands = nodes.generate(state, parameters.parallelism).generate(random).getOrThrow()
                    require(commands.size > 0)
                    log.info("Generated command batch of size ${commands.size}: $commands")
                    // Interpret commands
                    val newState = commands.fold(state, interpret)
                    // Execute commands
                    val queue = ConcurrentLinkedQueue(commands)
                    (1..parameters.parallelism).toList().parallelStream().forEach {
                        var next = queue.poll()
                        while (next != null) {
                            log.info("Executing $next")
                            try {
                                nodes.execute(next)
                                next = queue.poll()
                            } catch (exception: Throwable) {
                                val diagnostic = executeDiagnostic(state, newState, next, exception)
                                log.error(diagnostic)
                                throw Exception(diagnostic)
                            }
                        }
                    }
                    countSinceLastCheck += commands.size
                    if (countSinceLastCheck >= parameters.gatherFrequency) {
                        log.info("Checking consistency...")
                        countSinceLastCheck %= parameters.gatherFrequency
                        state = nodes.gatherRemoteState(newState)
                    } else {
                        state = newState
                    }
                    count -= commands.size
                }
                log.info("Checking final consistency...")
                poll {
                    state = nodes.gatherRemoteState(state)
                    isConsistent(state).apply {
                        if (!this) {
                            log.warn("State is not yet consistent: $state")
                        }
                    }
                }
                log.info("'$testName' done!")
            }
        }

    }

    companion object {
        fun <T, S> executeDiagnostic(oldState: S, newState: S, failedCommand: T, exception: Throwable): String {
            return "There was a problem executing command $failedCommand." +
                    "\nOld simulated state: $oldState" +
                    "\nNew simulated state(after batch): $newState" +
                    "\nException: $exception"
        }
    }
}

data class Nodes(
        val notary: NodeHandle,
        val networkMap: NodeHandle,
        val simpleNodes: List<NodeHandle>
) {
    val allNodes by lazy { (listOf(notary, networkMap) + simpleNodes).associateBy { it.info }.values }
}

/**
 * Runs the given [LoadTest]s using the given configuration.
 */
fun runLoadTests(configuration: LoadTestConfiguration, tests: List<Pair<LoadTest<*, *>, LoadTest.RunParameters>>) {
    val seed = configuration.seed ?: Random().nextLong()
    log.info("Using seed $seed")
    val random = SplittableRandom(seed)
    connectToNodes(
            configuration.sshUser,
            configuration.nodeHosts.map { it to configuration.remoteNodeDirectory / "certificates" },
            configuration.remoteMessagingPort,
            PortAllocation.Incremental(configuration.localTunnelStartingPort),
            configuration.localCertificatesBaseDirectory,
            configuration.rpcUsername,
            configuration.rpcPassword
    ) { connections ->
        log.info("Connected to all nodes!")
        val hostNodeHandleMap = ConcurrentHashMap<String, NodeHandle>()
        connections.parallelStream().forEach { connection ->
            log.info("Getting node info of ${connection.hostName}")
            val nodeInfo = connection.proxy.nodeIdentity()
            log.info("Got node info of ${connection.hostName}: $nodeInfo!")
            val otherNodeInfos = connection.proxy.networkMapUpdates().first
            val pubkeysString = otherNodeInfos.map {
                "    ${it.legalIdentity.name}: ${it.legalIdentity.owningKey.toBase58String()}"
            }.joinToString("\n")
            log.info("${connection.hostName} waiting for network map")
            connection.proxy.waitUntilRegisteredWithNetworkMap().get()
            log.info("${connection.hostName} sees\n$pubkeysString")
            val nodeHandle = NodeHandle(configuration, connection, nodeInfo)
            nodeHandle.waitUntilUp()
            hostNodeHandleMap.put(connection.hostName, nodeHandle)
        }

        val networkMapNode = hostNodeHandleMap.toList().single {
            it.second.info.advertisedServices.any { it.info.type == NetworkMapService.type }
        }

        val notaryNode = hostNodeHandleMap.toList().single {
            it.second.info.advertisedServices.any { it.info.type.isNotary() }
        }

        val nodes = Nodes(
                notary = notaryNode.second,
                networkMap = networkMapNode.second,
                simpleNodes = hostNodeHandleMap.values.filter {
                    it.info.advertisedServices.filter {
                        it.info.type in setOf(NetworkMapService.type, ValidatingNotaryService.type)
                    }.isEmpty()
                }
        )

        tests.forEach {
            val (test, parameters) = it
            test.run(nodes, parameters, random)
        }
    }
}
