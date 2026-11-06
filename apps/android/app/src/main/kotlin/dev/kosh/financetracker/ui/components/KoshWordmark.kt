package dev.kosh.financetracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kosh.financetracker.ui.theme.InstrumentSerifFamily
import dev.kosh.financetracker.ui.theme.Tinward

// Approved Tinward mark, same geometry as apps/web Logo.tsx (viewBox 340x276):
// an ink "T" with an ochre square.
private const val T_PATH = "M24 15H212Q218 15 218 21V228Q218 268 178 268H132Q125 268 125 261V98H18Q10 98 10 90V29Q10 15 24 15Z"

@Composable
fun TinwardMark(modifier: Modifier = Modifier, height: Dp = 22.dp, tColor: Color = Tinward.colors.textPrimary) {
    val path = remember { PathParser().parsePathString(T_PATH).toPath() }
    val square = Tinward.colors.accent
    androidx.compose.foundation.layout.Box(modifier.height(height).width(height * (340f / 276f))) {
        Canvas(modifier = Modifier.height(height).width(height * (340f / 276f))) {
            val s = size.height / 276f
            scale(s, s, pivot = Offset.Zero) {
                drawPath(path, tColor)
                drawRoundRect(square, topLeft = Offset(240f, 8f), size = Size(90f, 90f), cornerRadius = CornerRadius(10f, 10f))
            }
        }
    }
}

@Composable
fun KoshWordmark(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        TinwardMark(height = 20.dp)
        Spacer(Modifier.width(8.dp))
        Text("Tinward", style = MaterialTheme.typography.titleLarge.copy(fontFamily = InstrumentSerifFamily, fontSize = 24.sp), color = Tinward.colors.textPrimary)
    }
}
