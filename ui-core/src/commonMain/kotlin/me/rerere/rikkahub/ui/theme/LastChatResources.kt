package me.rerere.rikkahub.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import me.rerere.rikkahub.ui.core.generated.resources.Res
import me.rerere.rikkahub.ui.core.generated.resources.default_generical_pfp
import org.jetbrains.compose.resources.painterResource

@Composable
fun rememberDefaultGenericalPainter(): Painter {
    return painterResource(Res.drawable.default_generical_pfp)
}
