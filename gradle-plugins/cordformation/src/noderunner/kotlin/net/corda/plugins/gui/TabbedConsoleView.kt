package net.corda.plugins.gui

import javafx.scene.control.TabPane
import tornadofx.View
import tornadofx.plusAssign
import tornadofx.tab
import tornadofx.tabpane

// TODO: The node runner should be invoked from inside the application, not passed into it, depending on
// whether we are headless or not.
class TabbedConsoleView : View() {
    override val root = tabpane {
        tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE
        //for (i in 0..numTabs) {
        //    tab("$i") { this += ConsoleView() }
        //}
    }
}