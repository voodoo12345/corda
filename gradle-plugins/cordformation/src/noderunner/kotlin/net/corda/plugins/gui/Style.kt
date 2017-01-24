package net.corda.plugins.gui

import javafx.scene.layout.BackgroundSize
import javafx.scene.paint.Paint
import tornadofx.*

class Style : Stylesheet() {
    companion object {
        // The -fx-background property doesn't exist in Tornado, this is a hack
        // to get scroll panes to style properly (they ignore -fx-background-color)
        // http://stackoverflow.com/questions/22952531/scrollpanes-in-javafx-8-always-have-gray-background
        val `-fxBackground` by cssproperty<String> { it }
        val softWhiteStr = "#FAFAFA"
        val softWhite = c(softWhiteStr)
        val transparent = c("white", 0.0)
    }

    init {
        scrollPane {
            fontFamily = "Monospaced"
            backgroundColor += softWhite
            `-fxBackground`.value = softWhiteStr
        }

        text {
            backgroundColor += softWhite
        }

        tab {
            backgroundColor += softWhite
        }

        tabPane {
            backgroundColor += softWhite
        }

        textArea {
            padding = box(10.px, 10.px)
            fitToHeight = true
            backgroundColor += transparent
            backgroundInsets += box(0.px, 0.px)
            borderWidth += box(0.px, 0.px)
        }

        textArea {
            content {
                backgroundColor += transparent
            }
        }
    }
}