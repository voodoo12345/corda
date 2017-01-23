package net.corda.plugins.gui

import javafx.scene.control.Label
import tornadofx.View

class ConsoleView : View() {
    // TODO: Pipe text into this view from the process
    override val root = Label("TEST")
}