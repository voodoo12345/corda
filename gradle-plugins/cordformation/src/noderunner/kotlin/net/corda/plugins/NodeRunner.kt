package net.corda.plugins

import javafx.application.Application
import net.corda.plugins.gui.NodeRunnerApp
import java.awt.GraphicsEnvironment
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths


fun main(args: Array<String>) {
    NodeRunner().run()
}

class NodeRunner() {
    private companion object {
        val jarName = "corda.jar"
        val nodeConfName = "node.conf"
    }

    fun run() {
        val workingDir = Paths.get(System.getProperty("user.dir")).toFile()
        println("Starting node runner in $workingDir")

        workingDir.list().map { File(workingDir, it) }.forEach {
            if (isNode(it)) {
                startNode(it)
            }

            if (isWebserver(it)) {
                startWebServer(it)
            }
        }

        println("Started ${processes.size} processes")
        println("Node runner finished")
    }

    private fun isNode(maybeNodeDir: File) = maybeNodeDir.isDirectory
            && File(maybeNodeDir, jarName).exists()
            && File(maybeNodeDir, nodeConfName).exists()

    private fun isWebserver(maybeWebserverDir: File) = isNode(maybeWebserverDir) && hasWebserverPort(maybeWebserverDir)

    // TODO: Add a webserver.conf, or use TypeSafe config instead of this hack
    private fun hasWebserverPort(nodeConfDir: File) = Files.readAllLines(File(nodeConfDir, nodeConfName).toPath()).joinToString { it }.contains("webAddress")

    private fun startNode(nodeDir: File) {
        println("Starting node in $nodeDir")
        execCordaJar(nodeDir)
    }

    private fun startWebServer(webserverDir: File) {
        println("Starting webserver in $webserverDir")
        execCordaJar(webserverDir, listOf("--webserver"))
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
}