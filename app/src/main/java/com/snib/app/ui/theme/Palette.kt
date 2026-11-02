package com.snib.app.ui.theme

import androidx.compose.ui.graphics.Color

/** Drawn from Scottish door ironmongery — brass fittings, slate, harl render. */
object Snib {
    val slate = Color(0xFF2A2E33)      // primary ground; cool dark, not black
    val slateLift = Color(0xFF383D44)  // cards, raised surfaces
    val brass = Color(0xFFB08A4A)      // the mark, snibbed state, primary action
    val harl = Color(0xFFDCDAD3)       // body text on slate
    val verdigris = Color(0xFF3E7C74)  // open state, confirmations
    val rust = Color(0xFFA63D2F)       // emergency breaks and recovery only

    val harlDim = harl.copy(alpha = 0.62f)
    val harlFaint = harl.copy(alpha = 0.38f)
}
