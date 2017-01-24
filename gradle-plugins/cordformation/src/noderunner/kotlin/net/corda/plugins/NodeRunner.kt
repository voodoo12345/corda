package net.corda.plugins

import javafx.application.Application
import net.corda.plugins.gui.NodeRunnerApp
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

// TODO: Handle headless mode again
fun main(args: Array<String>) {
    Application.launch(NodeRunnerApp::class.java)
    //NodeRunner().run()
}

class NodeRunner {
    private companion object {
        val jarName = "corda.jar"
        val nodeConfName = "node.conf"
    }

    data class CordaProcess(val process: Process, val name: String)

    private var processes = mutableListOf<CordaProcess>()
    var onProcess: (CordaProcess) -> Unit = {}

    fun run() {
        val workingDir = Paths.get(System.getProperty("user.dir")).toFile()
        println("Starting node runner in $workingDir")
        Runtime.getRuntime().addShutdownHook(Thread({ shutdown() }))

        workingDir.list().map { File(workingDir, it) }.forEach {
            if (isNode(it)) {
                startNode(it)
            }

            if (isWebserver(it)) {
                startWebServer(it)
            }
        }

        println("Started ${processes.size} processes")
        while (true) {
            Thread.sleep(500)
        }
    }

    private fun isNode(maybeNodeDir: File) = maybeNodeDir.isDirectory
            && File(maybeNodeDir, jarName).exists()
            && File(maybeNodeDir, nodeConfName).exists()

    private fun isWebserver(maybeWebserverDir: File) = isNode(maybeWebserverDir) && hasWebserverPort(maybeWebserverDir)

    // TODO: Add a webserver.conf, or use TypeSafe config instead of this hack
    private fun hasWebserverPort(nodeConfDir: File) = Files.readAllLines(File(nodeConfDir, nodeConfName).toPath()).joinToString { it }.contains("webAddress")

    private fun startNode(nodeDir: File) {
        println("Starting node in $nodeDir")
        registerProcess(CordaProcess(execCordaJar(nodeDir), "${nodeDir.toPath().fileName}"))
    }

    private fun startWebServer(webserverDir: File) {
        println("Starting webserver in $webserverDir")
        registerProcess(CordaProcess(execCordaJar(webserverDir, listOf("--webserver")), "${webserverDir.toPath().fileName}-web"))
    }

    private fun execCordaJar(dir: File, args: List<String> = listOf()): Process {
        val separator = System.getProperty("file.separator")
        val path = System.getProperty("java.home") + separator + "bin" + separator + "java"
        val builder = ProcessBuilder(listOf(path, "-jar", jarName) + args)
        // TODO: Switch on if headless
        //builder.redirectError(Paths.get("error.${dir.toPath().fileName}.log").toFile())
        //builder.inheritIO()
        builder.directory(dir)
        return builder.start()
    }

    private fun registerProcess(process: CordaProcess) {
        processes.add(process)
        onProcess(process)
    }

    private fun shutdown() {
        processes.forEach { it.process.destroy() }
        Thread.sleep(5000)
        processes.forEach { it.process.destroyForcibly() }
    }
}