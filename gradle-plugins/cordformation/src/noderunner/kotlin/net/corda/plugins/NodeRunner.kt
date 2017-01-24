package net.corda.plugins

import javafx.application.Application
import net.corda.plugins.gui.NodeRunnerApp
import java.awt.GraphicsEnvironment
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths


fun main(args: Array<String>) {
    // TODO: Use an args parser with help options.
    if(GraphicsEnvironment.isHeadless() || (!args.isEmpty() && (args[0] == "--headless"))) {
        NodeRunner(true).run()
    } else {
        Application.launch(NodeRunnerApp::class.java)
    }
}

class NodeRunner(val isHeadless: Boolean = false) {
    private companion object {
        val jarName = "corda.jar"
        val nodeConfName = "node.conf"
    }

    data class CordaProcess(val process: Process, val name: String)

    private var processes = mutableListOf<CordaProcess>()
    var onProcess: (CordaProcess) -> Unit = {}
    private var alive = true

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
        while (alive) {
            Thread.sleep(500)
        }
        println("Node runner exited")
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
        val nodeName = dir.toPath().fileName
        val separator = System.getProperty("file.separator")
        val path = System.getProperty("java.home") + separator + "bin" + separator + "java"
        val builder = ProcessBuilder(listOf(path, "-Dname=$nodeName", "-jar", jarName) + args)
        if(isHeadless) {
            builder.redirectError(Paths.get("error.${dir.toPath().fileName}.log").toFile())
            builder.inheritIO()
        }
        builder.directory(dir)
        return builder.start()
    }

    private fun registerProcess(process: CordaProcess) {
        processes.add(process)
        onProcess(process)
    }

    fun shutdown() {
        println("Shutting down")
        if(alive) {
            println("Stopping all processes")
            processes.forEach { it.process.destroy() }
            try {
                println("Awaiting all processes to exit")
                processes.forEach { it.process.waitFor() }
            } catch (e: InterruptedException) {
                println("Forcefully killing all processes")
                processes.forEach { it.process.destroyForcibly() }
            }
            alive = false
        }
    }
}