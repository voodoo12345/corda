package net.corda.plugins.gui

import javafx.scene.Scene
import javafx.scene.control.Tab
import net.corda.plugins.NodeRunner
import tornadofx.App
import tornadofx.UIComponent
import kotlin.concurrent.thread

class NodeRunnerApp : App(TabbedConsoleView::class, Style::class) {
    val nodeRunner: NodeRunner = NodeRunner()

    override fun createPrimaryScene(view: UIComponent): Scene {
        (view as TabbedConsoleView).registerNodeRunner(nodeRunner)
        thread {
            nodeRunner.run()
        }

        return Scene(view.root, 1024.0, 768.0)
    }
}