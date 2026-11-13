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
    val Home = lineIcon("home", "M3.5 10.5L12 3.5l8.5 7V19.5a1 1 0 0 1-1 1h-4.5v-6h-6v6H4.5a1 1 0 0 1-1-1z")
    val Tasks = lineIcon("tasks", "M10 6h10M10 12h10M10 18h10", "M4 6l1 1 2-2M4 12l1 1 2-2M4 18l1 1 2-2")
    val Server = lineIcon(
        "server",
        "M5 4h14a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
        "M5 13h14a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2z",
        "M7 7.5h.01M7 16.5h.01",
    )
    val Space = lineIcon("space", "M21 12A9 9 0 1 1 12 3v9z", "M15 3.5A9 9 0 0 1 20.5 9H15z")
    val Files = lineIcon("files", "M12 3v12M7 10l5 5 5-5", "M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2")
    val Key = lineIcon("key", "M14.5 4a5 5 0 1 1-4.6 7L4 17v3h3v-2h2v-2h2l1.6-1.6A5 5 0 0 1 14.5 4z", "M16 8h.01")
    val Settings = lineIcon(
        "settings", "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z",
        "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z",
    )
    val Fingerprint = lineIcon(
        "fingerprint", "M12 11v3a8 8 0 0 1-1.5 4.7", "M8.5 9.5A4 4 0 0 1 16 11v1.5", "M5 13.5V11a7 7 0 0 1 12.6-4.2",
        "M15.5 16.5c-.3 1.4-.9 2.7-1.6 3.8", "M19 11v.5c0 1.2-.1 2.4-.4 3.5", "M8 13a4 4 0 0 0-.1 1 11 11 0 0 1-1.4 5",
    )
    val Shield = lineIcon("shield", "M12 3l8 3v6c0 4.5-3.4 8.3-8 9-4.6-.7-8-4.5-8-9V6z", "M9 12l2 2 4-4")
    val Back = lineIcon("back", "M9 6l6 6-6 6") // في RTL «رجوع» يأشر يمين
    val ChevronDown = lineIcon("down", "M6 9l6 6 6-6")
    val Search = lineIcon("search", "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z", "M20 20l-3.5-3.5")
    val Copy = lineIcon("copy", "M9 9h10a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V10a1 1 0 0 1 1-1z", "M16 9V5a1 1 0 0 0-1-1H5a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h3")
    val More = lineIcon("more", "M5 12h.01M12 12h.01M19 12h.01")
    val Plus = lineIcon("plus", "M12 5v14M5 12h14")
    val Warn = lineIcon("warn", "M12 3l10 18H2z", "M12 10v4M12 17.5h.01")
    val Sun = lineIcon("sun", "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8z", "M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4")
    val Moon = lineIcon("moon", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z")
    val Wifi = lineIcon("link", "M5 12.5a10 10 0 0 1 14 0M8 15.5a6 6 0 0 1 8 0", "M12 19h.01")
}
