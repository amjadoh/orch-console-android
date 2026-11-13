package com.amjadoh.sirfari

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/**
 * الرئيسية («لوحة قرارات»): فوق كم قرار ينتظرك وحالة السيرفر باختصار، وبعدها القرارات نفسها
 * (توافق من هنا على طول بعد ما تشوف وش بيتغير)، وبعدها اللي شغّال الحين بمراحله. كله في شاشة وحدة.
 */
@Composable
fun HomeScreen(
    r: Repo.Result<Overview>?, server: Repo.Result<ServerView>?, admin: JSONObject?, now: Long, actions: Actions?,
    openServer: () -> Unit, openTask: (String) -> Unit, openTasks: () -> Unit = {}, onRetry: () -> Unit = {}, onChanged: () -> Unit = {},
    smart: @Composable () -> Unit = {},
) {
    FreshnessBar(r, now, onRetry)
    val o = r?.data
    if (o == null) { if (r?.offline != true) LoadState(null, onRetry); return }
    var approving by remember { mutableStateOf<String?>(null) }
    var adminReq by remember { mutableStateOf<JSONObject?>(null) }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 700.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Hero(o, server?.data, now, openServer)
                    smart()
                    Running(o, openTask)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Decisions(o, admin, 4, openTask, openTasks, { approving = it }, { adminReq = it })
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Hero(o, server?.data, now, openServer)
                smart()
                Decisions(o, admin, 2, openTask, openTasks, { approving = it }, { adminReq = it })
                Running(o, openTask)
            }
        }
    }

    if (actions != null) {
        approving?.let { id -> ApproveSheet(id, actions.api, actions, onClose = { approving = null }, onDone = onChanged, openTask = openTask) }
        adminReq?.let { req -> AdminSheet(req, actions, onClose = { adminReq = null }, onDone = onChanged) }
    }
}

/** البطاقة الكبيرة: كم قرار ينتظرك + نبض السيرفر في سطر + أربع مقاييس */
@Composable
private fun Hero(o: Overview, s: ServerView?, now: Long, openServer: () -> Unit) {
    val ext = LocalSirfari.current
    val glow = MaterialTheme.colorScheme.primary.copy(alpha = if (ext.dark) 0.20f else 0.14f)
    val shape = RoundedCornerShape(28.dp)
    val n = decisionsCount(o)
    val (lc, line) = when (o.healthLevel) {
        "ok" -> Tone.ok to "السيرفر سليم"
        "watch" -> Tone.warn to (o.problems.firstOrNull()?.let { "انتبه: ${it.title}" } ?: "فيه شي تتابعه")
        "act" -> Tone.bad to (o.problems.firstOrNull()?.let { "يحتاج تدخل: ${it.title}" } ?: "يحتاج تدخل")
        else -> Tone.idle to "حالة السيرفر غير معروفة"
    }
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(ext.hero)
            .drawBehind { drawRect(Brush.radialGradient(listOf(glow, Color.Transparent), center = Offset(size.width * 0.85f, 0f), radius = size.width * 0.9f)) }
            .border(1.dp, ext.heroLine, shape).clickable(onClickLabel = "تفاصيل السيرفر", onClick = openServer)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dot(lc, 8, halo = true)
            Text(line, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (o.maintenanceUntil != null && o.maintenanceUntil > now) Chip("وضع الصيانة شغّال", Tone.run)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("$n", style = MaterialTheme.typography.headlineLarge, color = if (n > 0) Tone.warn else MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(bottom = 6.dp)) {
                Text(if (n > 0) "ينتظرون قرارك" else "ما عليك قرارات", style = MaterialTheme.typography.titleMedium)
                Text("شغّالة ${o.running} · خلصت اليوم ${o.doneToday}" + if (o.failed > 0) " · فشلت ${o.failed}" else "",
                    style = MaterialTheme.typography.labelMedium, color = if (o.failed > 0) Tone.bad else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (s != null && s.at != null) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniMeter("المعالج", "cpu", s.cpuPct, Modifier.weight(1f))
            MiniMeter("الذاكرة", "mem", s.mem?.usedPct, Modifier.weight(1f))
            MiniMeter("القرص", "disk", s.disks.firstOrNull { it.mount == "/" }?.pct ?: s.disks.firstOrNull()?.pct, Modifier.weight(1f))
            MiniMeter("Swap", "swap", s.mem?.swapPct, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniMeter(label: String, metric: String, v: Double?, modifier: Modifier) {
    val c = Limits.tone(metric, v)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Meter((v ?: 0.0) / 100, c)
        Text(pct(v), style = MaterialTheme.typography.labelLarge, color = if (Limits.level(metric, v) > 0) c else MaterialTheme.colorScheme.onSurface)
    }
}

/** «يحتاج قرارك»: أول مهمة ببطاقة كبيرة وزر موافقة، والباقي صفوف مختصرة */
@Composable
private fun Decisions(
    o: Overview, admin: JSONObject?, max: Int, openTask: (String) -> Unit, openTasks: () -> Unit,
    onApprove: (String) -> Unit, onAdmin: (JSONObject) -> Unit,
) {
    val tasks = o.tasks.filter { it.status == "awaiting" }
    val reqs = pendingAdmin(admin)
    val total = tasks.size + reqs.size.coerceAtLeast(o.adminPending)
    if (total == 0) {
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Tone.ok.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(SirfariIcons.Shield, null, tint = Tone.ok, modifier = Modifier.size(22.dp))
                }
                Column {
                    Text("كل شي ماشي", style = MaterialTheme.typography.titleSmall)
                    Text("أول ما تجهز خطة توصلك هنا وفي الإشعارات", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        return
    }
    Section("يحتاج قرارك", if (total > max) "الكل ($total)" else null, openTasks) {
        var shown = 0
        tasks.firstOrNull()?.let { t -> DecisionCard(t, { openTask(t.taskId) }, { onApprove(t.taskId) }); shown++ }
        reqs.take((max - shown).coerceAtLeast(0)).forEach { req -> AdminRow(req) { onAdmin(req) }; shown++ }
        tasks.drop(1).take((max - shown).coerceAtLeast(0)).forEach { t -> CompactTask(t, Tone.warn, "تنتظرك") { openTask(t.taskId) } }
    }
}

@Composable
private fun DecisionCard(t: TaskCard, onOpen: () -> Unit, onApprove: () -> Unit) {
    Panel(padding = 18) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(if (t.planPending) "الخطة تنكتب" else "الخطة جاهزة", if (t.planPending) Tone.run else Tone.warn)
            Mono("${t.projectId} · ${t.taskId}")
        }
        Text(t.title.ifBlank { t.taskId }, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
        val who = listOfNotNull(t.first?.let { "$it ينفذ" }, t.second?.let { "$it يراجع" }).joinToString(" و")
        if (who.isNotBlank()) Text(who, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (t.planPending) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary)
                    .clickable(enabled = !t.planPending, onClick = onApprove),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SirfariIcons.Fingerprint, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary)
                Text("  وافق بالبصمة", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
            }
            Box(
                Modifier.width(96.dp).height(48.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)).clickable(onClick = onOpen),
                contentAlignment = Alignment.Center,
            ) { Text("الخطة", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun AdminRow(req: JSONObject, onClick: () -> Unit) {
    Panel(padding = 14, modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Tone.run.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(SirfariIcons.Shield, null, tint = Tone.run, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("طلب root: ${req.optString("summary").ifBlank { req.optString("action") }}", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("يحتاج بصمتك · شوف وش بيصير قبل", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(SirfariIcons.Fingerprint, "وافق", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun CompactTask(t: TaskCard, color: Color, label: String, onClick: () -> Unit) {
    Panel(padding = 14, modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Dot(color)
            Column(Modifier.weight(1f)) {
                Text(t.title.ifBlank { t.taskId }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Mono("${t.projectId} · ${t.taskId}")
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

private val STAGES = listOf("خطة", "ينفذ", "يراجع", "يفحص")

/** «شغّال الحين»: كل مهمة بمراحلها الحقيقية (مو نسبة وهمية) */
@Composable
private fun Running(o: Overview, openTask: (String) -> Unit) {
    val running = o.tasks.filter { it.status == "running" }
    if (running.isEmpty()) return
    Section("شغّال الحين") {
        running.take(2).forEach { t ->
            Panel(padding = 16, modifier = Modifier.clickable { openTask(t.taskId) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.title.ifBlank { t.taskId }, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Dot(Tone.run, 7, halo = true)
                    Text(" ${(if (t.stage >= 2) t.second else t.first) ?: ""}", style = MaterialTheme.typography.labelMedium, color = Tone.run)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    STAGES.forEachIndexed { i, s ->
                        val cur = i == t.stage; val past = i < t.stage
                        Box(
                            Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (cur) Tone.run.copy(alpha = 0.28f) else if (past) Tone.run.copy(alpha = 0.10f) else Color.Transparent)
                                .border(1.dp, if (cur || past) Color.Transparent else LocalSirfari.current.line, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (past) "$s ✓" else s, style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (cur) FontWeight.Bold else FontWeight.Normal),
                                color = if (cur) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
