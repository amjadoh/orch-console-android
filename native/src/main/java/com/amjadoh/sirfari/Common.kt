package com.amjadoh.sirfari

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// ألوان الحالة (ثابتة، تقرا على الكحلي والأبيض): خلصت / ينتظرك / مشكلة / شغال / عادي
object Tone {
    val ok = Color(0xFF3FCB8E)
    val warn = Color(0xFFF2B45A)
    val bad = Color(0xFFF07A7A)
    val run = Color(0xFFA78BFA)
    val idle = Color(0xFF7C8AA6)
    val action = Color(0xFF5AB0FF)
    fun level(l: String) = when (l) { "ok" -> ok; "watch", "warn" -> warn; "act", "bad" -> bad; else -> idle }
    fun state(s: String) = when (s) { "active", "running", "success" -> ok; "changing" -> run; "failed", "missing", "inactive", "exited", "dead" -> bad; else -> idle }
}

/** بطاقة سيرفري: حواف 22 وحد رفيع. العنوان اختياري (والأفضل عنوان القسم برا البطاقة بـ Section) */
@Composable
fun Panel(title: String? = null, modifier: Modifier = Modifier, padding: Int = 18, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface).border(1.dp, LocalSirfari.current.line, shape).padding(padding.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

/** قسم: عنوان عريض برا البطاقات، ورابط اختياري على الطرف الثاني */
@Composable
fun Section(title: String, action: String? = null, onAction: () -> Unit = {}, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (action != null) Text(action, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 6.dp, vertical = 4.dp))
        }
        content()
    }
}

@Composable
fun Dot(color: Color, size: Int = 8, halo: Boolean = false) = Box(
    Modifier.size((if (halo) size + 8 else size).dp), contentAlignment = Alignment.Center,
) {
    if (halo) Box(Modifier.size((size + 8).dp).background(color.copy(alpha = 0.2f), CircleShape))
    Box(Modifier.size(size.dp).background(color, CircleShape))
}

/** شارة صغيرة ملونة: «تنتظرك»، «الخطة جاهزة»… */
@Composable
fun Chip(text: String, color: Color, filled: Boolean = true) {
    Text(
        text, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = color, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).then(
            if (filled) Modifier.background(color.copy(alpha = 0.14f)) else Modifier.border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(50))
        ).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** معرفات ومسارات (MSR-0002، masroof، app/x.kt): يسار لليمين دايم، بخط ثابت */
val MonoStyle @Composable get() = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr, fontWeight = FontWeight.Normal)

@Composable
fun Mono(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, style: TextStyle = MonoStyle) =
    Text(text, modifier, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
fun StatusLine(color: Color, text: String, trailing: String? = null, trailingColor: Color? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Dot(color)
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (trailing != null) Text(trailing, Modifier.widthIn(max = 150.dp), style = MaterialTheme.typography.labelMedium,
            color = trailingColor ?: MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** صف قائمة داخل بطاقة، بفاصل رفيع */
@Composable
fun ListRow(modifier: Modifier = Modifier, divider: Boolean = true, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 13.dp)) { content() }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(LocalSirfari.current.line))
    }
}

/** شريط نسبة رفيع */
@Composable
fun Meter(fraction: Double, color: Color, modifier: Modifier = Modifier, height: Int = 4) {
    Box(modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(height.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))) {
        Box(Modifier.fillMaxWidth(fraction.toFloat().coerceIn(0f, 1f)).height(height.dp).clip(RoundedCornerShape(height.dp)).background(color))
    }
}

/** زر أيقونة مربع (44dp) */
@Composable
fun SquareIcon(icon: ImageVector, label: String, tint: Color = MaterialTheme.colorScheme.onSurface, bg: Color = MaterialTheme.colorScheme.surface, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.size(44.dp).clip(shape).background(bg).border(1.dp, LocalSirfari.current.line, shape).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp)) }
}

/** شريط «بدون اتصال»: يطلع بس لما فيه مشكلة أو البيانات قديمة (أكثر من دقيقتين) */
@Composable
fun FreshnessBar(r: Repo.Result<*>?, now: Long, onRetry: (() -> Unit)? = null) {
    if (r == null) return
    val stale = r.at != null && now - r.at > 120_000
    if (!r.offline && !stale) return
    val (color, text) = when {
        r.offline && r.data != null -> Tone.warn to "بدون اتصال · هذي آخر لقطة ${ago(r.at, now)}"
        r.offline -> Tone.bad to "ما وصلت للسيرفر. تأكد إن Tailscale شغال"
        else -> Tone.warn to "آخر تحديث ${ago(r.at, now)}"
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.12f)).padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Dot(color)
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(text, style = MaterialTheme.typography.bodySmall)
            if (r.offline && r.error != null && r.data == null) Text(r.error.substringAfter("(", "").removeSuffix(")"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        if (onRetry != null) TextButton(onClick = onRetry) { Text("حاول مرة ثانية") }
    }
}

/** حالة التحميل أو الخطأ بدل الشاشة الفاضية */
@Composable
fun LoadState(error: String?, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 36.dp), contentAlignment = Alignment.Center) {
        if (error == null) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ما قدرت أجيب البيانات", style = MaterialTheme.typography.titleSmall)
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
            TextButton(onClick = onRetry) { Text("حاول مرة ثانية") }
        }
    }
}

fun ago(at: Long?, now: Long): String {
    if (at == null) return "—"
    val s = ((now - at) / 1000).coerceAtLeast(0)
    return when {
        s < 10 -> "الحين"
        s < 60 -> "قبل $s ثانية"
        s < 3600 -> "قبل ${s / 60} د"
        s < 86400 -> "قبل ${s / 3600} س"
        else -> "قبل ${s / 86400} يوم"
    }
}

fun pct(x: Double?) = if (x == null || x.isNaN()) "—" else "${Math.round(x)}%"

/** حدود التنبيه لكل مقياس (نفسها في الحلقات والأشرطة): swap يحذر من 25% مثل تنبيه السيرفر */
object Limits {
    fun warn(metric: String) = when (metric) { "swap" -> 25.0; "cpu" -> 80.0; else -> 80.0 }
    fun bad(metric: String) = when (metric) { "swap" -> 60.0; else -> 92.0 }
    fun tone(metric: String, v: Double?) = when { v == null -> Tone.idle; v >= bad(metric) -> Tone.bad; v >= warn(metric) -> Tone.warn; else -> Tone.action }
    fun level(metric: String, v: Double?) = when { v == null -> 0; v >= bad(metric) -> 2; v >= warn(metric) -> 1; else -> 0 }
}
