package net.corda.plugins.gui

import javafx.scene.Scene
import net.corda.plugins.NodeRunner
import tornadofx.App
import tornadofx.UIComponent
import kotlin.concurrent.thread

class NodeRunnerApp : App(TabbedConsoleView::class) {
    val nodeRunner: NodeRunner = NodeRunner()
    val nodeRunnerThread = thread { nodeRunner.run() }

    override fun createPrimaryScene(view: UIComponent): Scene {
        nodeRunner.processes.forEach {
            (view as TabbedConsoleView).attachConsole(it)
        }
        return Scene(view.root)
    }
}