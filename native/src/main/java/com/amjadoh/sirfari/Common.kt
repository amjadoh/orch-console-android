package com.amjadoh.sirfari

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ألوان الحالة نفسها في الويب (ok / warn / bad / run)، بدرجة تقرا في الداكن والفاتح
object Tone {
    val ok = Color(0xFF3E9B57)
    val warn = Color(0xFFD08A1E)
    val bad = Color(0xFFD25050)
    val run = Color(0xFF4A8FD6)
    val idle = Color(0xFF9A988F)
    fun level(l: String) = when (l) { "ok" -> ok; "watch", "warn" -> warn; "act", "bad" -> bad; else -> idle }
    fun state(s: String) = when (s) { "active", "running", "success" -> ok; "changing" -> run; "failed", "missing", "inactive", "exited", "dead" -> bad; else -> idle }
}

@Composable
fun Panel(title: String? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun Dot(color: Color, size: Int = 9) = Box(Modifier.size(size.dp).background(color, CircleShape))

@Composable
fun StatusLine(color: Color, text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Dot(color)
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** شريط «آخر تحديث» أو «بدون اتصال، هذي آخر لقطة» */
@Composable
fun FreshnessBar(r: Repo.Result<*>?, now: Long) {
    if (r == null) return
    val (color, text) = when {
        r.offline && r.data != null -> Tone.warn to "بدون اتصال · آخر لقطة ${ago(r.at, now)}. ${r.error ?: ""}"
        r.offline -> Tone.bad to (r.error ?: "ما وصلت للسيرفر")
        else -> Tone.ok to "محدّث ${ago(r.at, now)}"
    }
    Row(
        Modifier.fillMaxWidth().background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Dot(color, 8)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

fun ago(at: Long?, now: Long): String {
    if (at == null) return "—"
    val s = ((now - at) / 1000).coerceAtLeast(0)
    return when {
        s < 10 -> "الحين"
        s < 60 -> "قبل $s ثانية"
        s < 3600 -> "قبل ${s / 60} دقيقة"
        s < 86400 -> "قبل ${s / 3600} ساعة"
        else -> "قبل ${s / 86400} يوم"
    }
}

fun pct(x: Double?) = if (x == null || x.isNaN()) "—" else "${Math.round(x)}٪"
