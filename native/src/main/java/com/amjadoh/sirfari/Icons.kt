package com.amjadoh.sirfari

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// نفس أيقونات التبويبات في سيرفري (ops/console/public/index.html)، خطوط بدون تعبئة
private fun lineIcon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        for (d in paths) addPath(
            pathData = addPathNodes(d), fill = null, stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

object SirfariIcons {
    val Home = lineIcon("home", "M4 11l8-7 8 7v8a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z")
    val Tasks = lineIcon(
        "tasks", "M9 6h11M9 12h11M9 18h11",
        "M3.1 6a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0-2.8 0z",
        "M3.1 12a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0-2.8 0z",
        "M3.1 18a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0-2.8 0z",
    )
    val Server = lineIcon(
        "server",
        "M5.5 4h13a2 2 0 0 1 2 2v2.5a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
        "M5.5 13.5h13a2 2 0 0 1 2 2V18a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2v-2.5a2 2 0 0 1 2-2z",
        "M7 7.25h.01M7 16.75h.01M11 7.25h6M11 16.75h6",
    )
    val Space = lineIcon("space", "M12 3.5a8.5 8.5 0 1 0 8.5 8.5H12z", "M15 3.8A8.5 8.5 0 0 1 20.2 9H15z")
    val Files = lineIcon("files", "M3.5 7.5a2 2 0 0 1 2-2h4l2 2h7a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z")
    val Key = lineIcon("key", "M14.5 4a5 5 0 1 1-4.6 7L4 17v3h3v-2h2v-2h2l1.6-1.6A5 5 0 0 1 14.5 4z", "M16 8h.01")
}
