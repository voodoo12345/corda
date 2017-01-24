package net.corda.plugins.gui

import javafx.application.Platform
import net.corda.plugins.NodeRunner
import tornadofx.View
import tornadofx.textarea
import java.io.BufferedReader
import java.io.InputStream
import kotlin.concurrent.thread

class ConsoleView(val process: NodeRunner.CordaProcess) : View() {
    val lineSep = System.getProperty("line.separator")
    override val root = textarea {
        text = "Showing output from node: ${process.name}$lineSep"
    }

    init {
        root.isEditable = false
        thread {
            while (process.process.isAlive) {
                readLineFrom(process.process.inputStream)
                readLineFrom(process.process.errorStream)

                Thread.sleep(10)
            }

            Platform.runLater {
                root.text += "Node exited with exit code ${process.process.exitValue()}"
            }
        }
    }

    private fun readLineFrom(stream: InputStream) {
        if (stream.available() > 0) {
            val reader = BufferedReader(stream.reader())
            val line = reader.readLine()
            Platform.runLater {
                root.text += line + lineSep
            }
        }
    }
}