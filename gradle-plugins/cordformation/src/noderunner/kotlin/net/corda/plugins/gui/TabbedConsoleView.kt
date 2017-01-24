package net.corda.plugins.gui

import net.corda.plugins.NodeRunner
import tornadofx.View
import tornadofx.plusAssign
import tornadofx.tab
import tornadofx.tabpane

class TabbedConsoleView : View() {
    override val root = tabpane {}

    init {
        title = "Corda Node Viewer"
    }

    fun registerNodeRunner(nodeRunner: NodeRunner) {
        nodeRunner.onProcess = { attachConsole(it) }
    }

    private fun attachConsole(cordaProc: NodeRunner.CordaProcess) {
        root.tab(cordaProc.name) {
            val view = ConsoleView(cordaProc)
            this += view

            setOnClosed { cordaProc.process.destroy() }
        }
    }
}