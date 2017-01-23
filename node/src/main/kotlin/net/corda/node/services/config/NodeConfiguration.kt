package net.corda.node.services.config

import com.google.common.net.HostAndPort
import com.typesafe.config.Config
import net.corda.core.div
import net.corda.core.node.services.ServiceInfo
import net.corda.node.internal.NetworkMapInfo
import net.corda.node.internal.Node
import net.corda.node.serialization.NodeClock
import net.corda.node.services.User
import net.corda.node.services.network.NetworkMapService
import net.corda.node.utilities.TestClock
import java.nio.file.Path
import java.util.*

// TODO Rename this to SSLConfiguration as it's also used by non-node components
interface NodeSSLConfiguration {
    val keyStorePassword: String
    val trustStorePassword: String
    val certificatesDirectory: Path
    // TODO Rename to keyStoreFile
    val keyStorePath: Path get() = certificatesDirectory / "sslkeystore.jks"
    // TODO Rename to trustStoreFile
    val trustStorePath: Path get() = certificatesDirectory / "truststore.jks"
}

interface NodeConfiguration : NodeSSLConfiguration {
    val baseDirectory: Path
    override val certificatesDirectory: Path get() = baseDirectory / "certificates"
    val myLegalName: String
    val networkMapService: NetworkMapInfo?
    val nearestCity: String
    val emailAddress: String
    val exportJMXto: String
    val dataSourceProperties: Properties
    val rpcUsers: List<User>
    val devMode: Boolean
}

//class FullNodeConfiguration(override val keyStorePassword: String,
//                            override val trustStorePassword: String,
//                            override val basedir: Path,
//                            override val myLegalName: String,
//                            override val networkMapService: NetworkMapInfo?,
//                            override val nearestCity: String,
//                            override val emailAddress: String,
//                            override val dataSourceProperties: Properties,
//                            override val rpcUsers: List<User> = emptyList(),
//                            override val devMode: Boolean = false,
//                            val useHTTPS: Boolean,
//                            val artemisAddress: HostAndPort,
//                            val webAddress: HostAndPort,
//                            val messagingServerAddress: HostAndPort? = null,
//                            // TODO Make this Set<ServiceInfo>
//                            val extraAdvertisedServiceIds: List<String>,
//                            val useTestClock: Boolean = false,
//                            val notaryNodeAddress: HostAndPort? = null,
//                            val notaryClusterAddresses: List<HostAndPort> = emptyList()) : NodeConfiguration {
/**
 * [baseDirectory] is not retrieved from the config file but rather from a command line argument.
 */
    class FullNodeConfiguration(override val baseDirectory: Path, val config: Config) : NodeConfiguration {
        override val myLegalName: String by config
        override val nearestCity: String by config
        override val emailAddress: String by config
        override val exportJMXto: String get() = "http"
        override val keyStorePassword: String by config
        override val trustStorePassword: String by config
        override val dataSourceProperties: Properties by config
        override val devMode: Boolean by config.orElse { false }
        override val networkMapService: NetworkMapInfo? by config.orElse { null }
        override val rpcUsers: List<User> by config.orElse { emptyList<User>() }
        val useHTTPS: Boolean by config
        val artemisAddress: HostAndPort by config
        val webAddress: HostAndPort by config
    // TODO This field is slightly redundant as artemisAddress is sufficient to hold the address of the node's MQ broker.
    // Instead this should be a Boolean indicating whether that broker is an internal one started by the node or an external one
        val messagingServerAddress: HostAndPort? by config.orElse { null }
        // TODO Make this Set<ServiceInfo>
        val extraAdvertisedServiceIds: List<String> by config
        val useTestClock: Boolean by config.orElse { false }
        val notaryNodeAddress: HostAndPort? by config.orElse { null }
        val notaryClusterAddresses: List<HostAndPort> by config.orElse { emptyList<HostAndPort>() }
//    override val exportJMXto: String get() = "http"
    init {
        // TODO Move this to AretmisMessagingServer
        rpcUsers.forEach {
            require(it.username.matches("\\w+".toRegex())) { "Username ${it.username} contains invalid characters" }
        }
    }

    fun createNode(): Node {
        // This is a sanity feature do not remove.
        require(!useTestClock || devMode) { "Cannot use test clock outside of dev mode" }

        val advertisedServices = extraAdvertisedServiceIds
                .filter(String::isNotBlank)
                .map { ServiceInfo.parse(it) }
                .toMutableSet()
        if (networkMapService == null) advertisedServices.add(ServiceInfo(NetworkMapService.type))

        return Node(this, advertisedServices, if (useTestClock) TestClock() else NodeClock())
    }
}
