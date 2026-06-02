package montafra.beam.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

val LocalOutlineOnlyCards = staticCompositionLocalOf { false }

@Composable
fun BeamCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable ColumnScope.() -> Unit,
) {
    val outlineOnly = LocalOutlineOnlyCards.current
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (outlineOnly) Color.Transparent else containerColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (outlineOnly) BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline) else null,
        content = content,
    )
}
