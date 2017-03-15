package net.corda.core.serialization

import com.esotericsoftware.kryo.*
import com.esotericsoftware.kryo.util.DefaultClassResolver
import com.esotericsoftware.kryo.util.Util
import net.corda.core.node.AttachmentsClassLoader
import net.corda.core.utilities.loggerFor
import java.io.PrintWriter
import java.lang.reflect.Modifier
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.*

fun Kryo.addToWhitelist(type: Class<*>) {
    ((classResolver as? CordaClassResolver)?.whitelist as? MutableClassWhitelist)?.add(type)
}

fun makeStandardClassResolver(): ClassResolver {
    return CordaClassResolver(GlobalTransientClassWhiteList(BuiltInExceptionsWhitelist()))
}

fun makeNoWhitelistClassResolver(): ClassResolver {
    return CordaClassResolver(AllWhitelist)
}

class CordaClassResolver(val whitelist: ClassWhitelist) : DefaultClassResolver() {
    companion object {
        private val logger = loggerFor<CordaClassResolver>()
    }

    /** Returns the registration for the specified class, or null if the class is not registered.  */
    override fun getRegistration(type: Class<*>): Registration? {
        return super.getRegistration(type) ?: checkClass(type)
    }

    private var whitelistEnabled = true

    fun disableWhitelist() {
        whitelistEnabled = false
    }

    fun enableWhitelist() {
        whitelistEnabled = true
    }

    private fun checkClass(type: Class<*>): Registration? {
        /** If call path has disabled whitelisting (see [CordaKryo.register]), just return without checking. */
        if(!whitelistEnabled) return null
         // Allow primitives, abstracts and interfaces
        if (type.isPrimitive || type == Any::class.java || Modifier.isAbstract(type.modifiers) || type==String::class.java) return null
        // If array, recurse on element type
        if (type.isArray) {
            return checkClass(type.componentType)
        }
        if (!type.isEnum && Enum::class.java.isAssignableFrom(type)) {
            // Specialised enum entry, so just resolve the parent Enum type since cannot annotate the specialised entry.
            return checkClass(type.superclass)
        }
        // It's safe to have the Class already, since Kryo loads it with initialisation off.
        val hasAnnotation = checkForAnnotation(type)
        if (!hasAnnotation && !whitelist.hasListed(type)) {
            throw KryoException("Class ${Util.className(type)} is not annotated or on the whitelist, so cannot be used in serialization")
        }
        return null
    }

    override fun registerImplicit(type: Class<*>): Registration {
        // We have to set reference to true, since the flag influences how String fields are treated and we want it to be consistent.
        val references = kryo.references
        try {
            kryo.references = true
            return register(Registration(type, kryo.getDefaultSerializer(type), NAME.toInt()))
        } finally {
            kryo.references = references
        }
    }

    // We don't allow the annotation for classes in attachments for now.  The class will be on the main classpath if we have the CorDapp installed.
    // We also do not allow extension of KryoSerializable for annotated classes, or combination with @DefaultSerializer for custom serialisation.
    // TODO: Later we can support annotations on attachment classes and spin up a proxy via bytecode that we know is harmless.
    private fun checkForAnnotation(type: Class<*>): Boolean {
        return (type.classLoader !is AttachmentsClassLoader)
                && !KryoSerializable::class.java.isAssignableFrom(type)
                && !type.isAnnotationPresent(DefaultSerializer::class.java)
                && (type.isAnnotationPresent(CordaSerializable::class.java) || hasAnnotationOnInterface(type))
    }

    // Recursively check interfaces for our annotation.
    private fun hasAnnotationOnInterface(type: Class<*>): Boolean {
        return type.interfaces.any { it.isAnnotationPresent(CordaSerializable::class.java) || hasAnnotationOnInterface(it) }
                || (type.superclass != null && hasAnnotationOnInterface(type.superclass))
    }
}

interface ClassWhitelist {
    fun hasListed(type: Class<*>): Boolean
}

interface MutableClassWhitelist : ClassWhitelist {
    fun add(entry: Class<*>)
}

object EmptyWhitelist : ClassWhitelist {
    override fun hasListed(type: Class<*>): Boolean = false
}

class BuiltInExceptionsWhitelist : ClassWhitelist {
    override fun hasListed(type: Class<*>): Boolean = Throwable::class.java.isAssignableFrom(type) && type.`package`.name.startsWith("java.")
}

object AllWhitelist : ClassWhitelist {
    override fun hasListed(type: Class<*>): Boolean = true
}

// TODO: Need some concept of from which class loader
class GlobalTransientClassWhiteList(val delegate: ClassWhitelist) : MutableClassWhitelist, ClassWhitelist by delegate {
    companion object {
        val whitelist: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    }

    override fun hasListed(type: Class<*>): Boolean {
        return (type.name in whitelist) || delegate.hasListed(type)
    }

    override fun add(entry: Class<*>) {
        whitelist += entry.name
    }
}

/**
 * This class is not currently used, but can be installed to log a large number of missing entries from the whitelist
 * and was used to track down the initial set.
 */
class LoggingWhitelist(val delegate: ClassWhitelist, val global: Boolean = true) : MutableClassWhitelist {
    companion object {
        val log = loggerFor<LoggingWhitelist>()
        val globallySeen: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
        val journalWriter: PrintWriter? = openOptionalDynamicWhitelistJournal()

        private fun openOptionalDynamicWhitelistJournal(): PrintWriter? {
            val fileName = System.getenv("WHITELIST_FILE")
            if (fileName != null && fileName.isNotEmpty()) {
                try {
                    return PrintWriter(Files.newBufferedWriter(Paths.get(fileName), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE), true)
                } catch(ioEx: Exception) {
                    log.error("Could not open/create whitelist journal file for append: $fileName", ioEx)
                }
            }
            return null
        }
    }

    private val locallySeen: MutableSet<String> = mutableSetOf()
    private val alreadySeen: MutableSet<String> get() = if (global) globallySeen else locallySeen

    override fun hasListed(type: Class<*>): Boolean {
        if (type.name !in alreadySeen && !delegate.hasListed(type)) {
            alreadySeen += type.name
            val className = Util.className(type)
            log.warn("Dynamically whitelisted class $className")
            if (journalWriter != null) {
                journalWriter.println(className)
            }
        }
        return true
    }

    override fun add(entry: Class<*>) {
        if (delegate is MutableClassWhitelist) {
            delegate.add(entry)
        } else {
            throw UnsupportedOperationException("Cannot add to whitelist since delegate whitelist is not mutable.")
        }
    }
}

