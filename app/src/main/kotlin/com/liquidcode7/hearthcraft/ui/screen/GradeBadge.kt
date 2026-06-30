package com.liquidcode7.hearthcraft.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.liquidcode7.hearthcraft.data.model.Grade

/** Compact inline badge showing a Grade name. Color-coded Crude→Pristine. */
@Composable
fun GradeBadge(grade: Int, modifier: Modifier = Modifier) {
    val g = Grade.fromOrdinal(grade)
    val bg = when (g) {
        Grade.CRUDE    -> Color(0xFF78909C)   // blue-grey
        Grade.COMMON   -> Color(0xFF66BB6A)   // green
        Grade.FINE     -> Color(0xFF42A5F5)   // blue
        Grade.SUPERB   -> Color(0xFFAB47BC)   // purple
        Grade.PRISTINE -> Color(0xFFFFCA28)   // amber
    }
    Text(
        text = g.displayName,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    )
}
