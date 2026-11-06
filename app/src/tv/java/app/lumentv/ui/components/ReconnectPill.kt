package app.lumentv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.lumentv.ui.theme.Lumen

/** Aviso sobre un vídeo pequeño mientras reconecta (la lógica, `StreamReconnect`, está en src/main). */
@Composable
fun ReconnectPill(attempt: Int, modifier: Modifier = Modifier) {
    LText(
        "Reconectando ($attempt/$STREAM_MAX_RETRIES)",
        style = Lumen.type.caption,
        color = Color.White,
        maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}
