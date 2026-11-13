package com.amjadoh.sirfari

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private data class Metric(val key: String, val label: String, val value: Double?) {
    val level get() = Limits.level(key, value)
    val tone get() = Limits.tone(key, value)
}

/**
 * السيرفر بنظرة وحدة: المقياس اللي يحتاج انتباه بدائرة كبيرة وجملة تشرحه، والباقي مربعات صغيرة،
 * والخدمات السليمة مطوية في سطر. كل التفاصيل والتشخيص في ورقة تطلع من تحت.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(r: Repo.Result<ServerView>?, now: Long, onRetry: () -> Unit = {}, smart: @Composable () -> Unit = {}, extra: @Composable () -> Unit = {}) {
    FreshnessBar(r, now, onRetry)
    val s = r?.data ?: run { if (r?.offline != true) LoadState(null, onRetry); return }
    if (s.at == null) { Panel { Text("ما فيه بيانات من جامع السيرفر للحين") }; return }
    var details by rememberSaveable { mutableStateOf(false) }
    var allServices by rememberSaveable { mutableStateOf(false) }

    val metrics = listOf(
        Metric("cpu", "المعالج", s.cpuPct),
        Metric("mem", "الذاكرة", s.mem?.usedPct),
        Metric("disk", "القرص", s.disks.firstOrNull { it.mount == "/" }?.pct ?: s.disks.firstOrNull()?.pct),
        Metric("swap", "Swap", s.mem?.swapPct),
    )
    val hero = metrics.maxWith(compareBy<Metric>({ it.level }, { if (it.key == "mem") 1 else 0 }))
    val worst = metrics.maxOf { it.level }.coerceAtLeast(s.alerts.maxOfOrNull { if (it.level == "bad" || it.level == "act") 2 else 1 } ?: 0)

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Mono(s.hostName ?: "السيرفر", Modifier.weight(1f))
        when {
            worst == 0 -> Chip("سليم", Tone.ok)
            hero.level > 0 -> Chip("${hero.label} ${if (hero.level == 2) "عالية جدا" else "مرتفعة"}", hero.tone)
            else -> Chip("فيه تنبيه", Tone.warn)
        }
    }

    // الدائرة الكبيرة + الجملة اللي تشرح
    Panel(padding = 20, modifier = Modifier.clickable { details = true }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            BigRing(hero)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(heroLine(hero, s), style = MaterialTheme.typography.titleSmall)
                s.consumers.firstOrNull()?.let { c ->
                    Text("أكبر مستهلك للذاكرة: ${c.label} (${Math.round(c.rssMb)} ميجا)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("التشخيص والتفاصيل ←", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        metrics.filter { it != hero }.forEach { m ->
            Panel(padding = 14, modifier = Modifier.weight(1f)) {
                Text(m.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(pct(m.value), style = MaterialTheme.typography.titleLarge, color = if (m.level > 0) m.tone else MaterialTheme.colorScheme.onSurface)
                val series = when (m.key) { "cpu" -> s.history.mapNotNull { it.cpu }; "mem" -> s.history.mapNotNull { it.mem }; else -> emptyList() }
                if (series.size >= 3) Spark(series, m.tone) else Meter((m.value ?: 0.0) / 100, m.tone)
            }
        }
    }

    smart()

    if (s.alerts.isNotEmpty()) Section("التنبيهات") {
        Panel(padding = 0) { s.alerts.forEachIndexed { i, a -> ListRow(divider = i < s.alerts.lastIndex) { StatusLine(Tone.level(a.level), a.text) } } }
    }

    val bad = s.services.filter { it.state != "active" }
    val okCount = s.services.size - bad.size
    val backup = s.timers.firstOrNull { it.kind == "backup" } ?: s.timers.firstOrNull()
    Section("الخدمات", "$okCount من ${s.services.size} شغالة", { allServices = !allServices }) {
        Panel(padding = 0) {
            val shown = if (allServices) s.services else bad
            shown.forEachIndexed { _, v -> ListRow { StatusLine(Tone.state(v.state), v.label + if (v.protected) " · محمية" else "", stateText(v.state), if (v.state != "active") Tone.bad else null) } }
            if (allServices) {
                s.containers.forEach { c ->
                    val idle = c.onDemand && c.state != "running"
                    ListRow { StatusLine(if (idle) Tone.idle else Tone.state(c.state), c.label, if (idle) "عند الطلب" else c.status.ifBlank { c.state }) }
                }
                s.timers.forEach { t -> ListRow { StatusLine(Tone.state(t.result), t.label, "آخر تشغيل ${ago(t.lastAt, now)}") } }
            }
            ListRow(Modifier.clickable { allServices = !allServices }, divider = false) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Dot(Tone.idle)
                    Text(
                        if (allServices) "اطو القائمة" else "+ $okCount خدمة سليمة" + (backup?.let { " · ${it.label} ${ago(it.lastAt, now)}" } ?: ""),
                        Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Icon(SirfariIcons.ChevronDown, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (details) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { details = false }, sheetState = sheet, containerColor = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("تفاصيل السيرفر", style = MaterialTheme.typography.titleLarge)
                val bits = buildList {
                    if (s.load.isNotEmpty()) add("التحميل ${s.load.joinToString(" · ") { "%.2f".format(it) }}")
                    s.mem?.let { add("متاح ${Math.round(it.availMb)} من ${Math.round(it.totalMb)} ميجا") }
                    s.uptimeSec?.let { add("شغال من ${it / 86400} يوم") }
                    s.tailscaleOnline?.let { add(if (it) "Tailscale متصل" else "Tailscale مو متصل") }
                }
                Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HistoryChart(s.history)
                if (s.consumers.isNotEmpty()) Section("أكثر شي ياخذ ذاكرة") {
                    Panel(padding = 0) { s.consumers.take(6).forEachIndexed { i, c -> ListRow(divider = i < minOf(5, s.consumers.lastIndex)) { StatusLine(Tone.idle, c.label + if (c.protected) " · محمي" else "", "${Math.round(c.rssMb)} ميجا") } } }
                }
                s.disks.filter { it.mount != "/" }.takeIf { it.isNotEmpty() }?.let { ds ->
                    Section("الأقراص") { Panel(padding = 0) { ds.forEach { ListRow { StatusLine(Tone.idle, it.mount, "${pct(it.pct)} من ${Math.round(it.totalGb)} GB") } } } }
                }
                extra()
            }
        }
    }
}

private fun heroLine(m: Metric, s: ServerView): String = when (m.key) {
    "mem" -> s.mem?.let { "متاح ${mb(it.availMb)} من ${mb(it.totalMb)}" } ?: "الذاكرة"
    "swap" -> s.mem?.let { "الـ Swap مستخدم ${mb(it.swapUsedMb)} من ${mb(it.swapTotalMb)}" } ?: "الـ Swap"
    "disk" -> s.disks.firstOrNull { it.mount == "/" }?.let { "مستخدم ${Math.round(it.usedGb)} من ${Math.round(it.totalGb)} GB" } ?: "القرص"
    else -> if (s.load.isNotEmpty()) "التحميل ${s.load.joinToString(" · ") { "%.2f".format(it) }}" else "المعالج"
}

private fun mb(x: Double) = if (x >= 1024) "%.1f جيجا".format(x / 1024) else "${Math.round(x)} ميجا"

@Composable
private fun BigRing(m: Metric) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val frac = ((m.value ?: 0.0) / 100).toFloat().coerceIn(0f, 1f)
    Box(Modifier.size(124.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(124.dp)) {
            val w = 11.dp.toPx(); val inset = w / 2
            val sz = Size(size.width - w, size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(w))
            drawArc(m.tone, -90f, 360f * frac, false, Offset(inset, inset), sz, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(pct(m.value), style = MaterialTheme.typography.headlineMedium, color = if (m.level > 0) m.tone else MaterialTheme.colorScheme.onSurface)
            Text(m.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** خط صغير لآخر ساعة (الأقدم يمين مثل اتجاه القراءة) */
@Composable
private fun Spark(values: List<Double>, color: Color) {
    Canvas(Modifier.fillMaxWidth().height(22.dp)) {
        val p = Path()
        values.forEachIndexed { i, v ->
            val x = size.width - i * size.width / (values.size - 1)
            val y = size.height * (1f - (v / 100.0).toFloat().coerceIn(0f, 1f))
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, color, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun stateText(s: String) = when (s) {
    "active" -> "شغالة"; "failed" -> "فاشلة"; "inactive" -> "واقفة"; "missing" -> "غير موجودة"; "changing" -> "تتغير"; else -> "غير معروف"
}
