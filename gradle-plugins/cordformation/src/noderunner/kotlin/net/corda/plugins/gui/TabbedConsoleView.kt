package net.corda.plugins.gui

import javafx.scene.control.TabPane
import net.corda.plugins.NodeRunner
import tornadofx.View
import tornadofx.plusAssign
import tornadofx.tab
import tornadofx.tabpane

// TODO: The node runner should be invoked from inside the application, not passed into it, depending on
// whether we are headless or not.
class TabbedConsoleView : View() {
    override val root = tabpane {
        // TODO: Allow and kill process on tab close
        tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE
    }

    fun attachConsole(cordaProc: NodeRunner.CordaProcess) {
        root.tab(cordaProc.name) { this += ConsoleView() }
    }
}