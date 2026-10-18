package com.unciv.ui.popups.options

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.civilopediascreen.MarkupRenderer

internal class AboutTab(
    optionsPopup: OptionsPopup
): OptionsPopupTab(optionsPopup) {
    init {
        renderTo(this)
    }

    companion object {
        /** Get "About" content in a separate Table without the [OptionsPopupTab] contract */
        // MainMenuscreen adds this to a Popup - one Table wrapper could be saved by using renderTo(Popup.innerTable), but that would be longer code
        fun asTable() = Table().apply { renderTo(this) }

        private fun renderTo(table: Table) {
            table.pad(20f)
            // The changelog has no patches, and anchors per release tag omit the dots
            val versionAnchor = Regex("""\.|-patch\d+$""").replace(UncivGame.VERSION.text, "")
            val lines = sequence {
                yield(FormattedLine("Wigo Civilizações", header = 2))
                yield(FormattedLine("Independent adaptation by Wigo Tecnologia."))
                yield(FormattedLine("Based on Unciv. Original credits and MPL-2.0 license retained."))
                yield(FormattedLine("Wigo source code (MPL-2.0)", link = "https://github.com/lopesclayton/Unciv"))
                yield(FormattedLine())
                yield(FormattedLine(extraImage = "banner", imageSize = 240f, centered = true))
                yield(FormattedLine())
                yield(FormattedLine("{Version}: ${UncivGame.VERSION.toNiceString()}", link = "${Constants.uncivRepoURL}blob/master/changelog.md#$versionAnchor"))
                yield(FormattedLine("See online Readme", link = "${Constants.uncivRepoURL}blob/master/README.md#unciv---foss-civ-v-for-androiddesktop"))
                yield(FormattedLine("Visit repository", link = Constants.uncivRepoURL))
                yield(FormattedLine("Visit the wiki", link = Constants.wikiURL))
            }
            val textWidth = ((UncivGame.Current.screen?.stage?.width ?: 600f) - 100f).coerceIn(160f, 520f)
            MarkupRenderer.renderTo(table, lines.asIterable(), labelWidth = textWidth)
        }
    }
}
