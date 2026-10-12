package com.hesaplarimdijital.kitapbulucu.ui

import androidx.compose.ui.graphics.vector.ImageVector
import desktopkit.components.DeskIcons

/** The app's own icons, drawn on desktop-kit's 16×16 grid in its line style. */
object AppIcons {
    /** An open book. */
    val Book: ImageVector by lazy {
        DeskIcons.build(
            "Book",
            DeskIcons.stroke("M8 4.3C6.8 3.4 5.2 3 3 3v9.2c2.2 0 3.8.4 5 1.3 1.2-.9 2.8-1.3 5-1.3V3c-2.2 0-3.8.4-5 1.3z"),
            DeskIcons.stroke("M8 4.3v9.2"),
        )
    }

    /** Two upright books and a leaning one on a shelf board. */
    val Shelf: ImageVector by lazy {
        DeskIcons.build(
            "Shelf",
            DeskIcons.stroke("M2 13.5h12"),
            DeskIcons.stroke("M3.5 13.5v-9h2.5v9"),
            DeskIcons.stroke("M6 13.5V3h2.5v10.5"),
            DeskIcons.stroke("M9.7 13.5 11.9 5l2 .55"),
        )
    }
}
