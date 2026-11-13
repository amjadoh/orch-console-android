package com.amjadoh.sirfari

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * زر البصمة: «وافق بالبصمة» ← بصمتك ← التحقق ← التنفيذ ← «بدأ التنفيذ ✓» (يتلوّن ويهتز خفيف).
 * النجاح يعني إن السيرفر أكّد، مو بس إن البصمة نجحت. يشتغل على نطاق التطبيق، فما ينقطع لو طلعت من الصفحة.
 */
@Composable
fun FingerprintButton(
    label: String, doneLabel: String, steps: List<String>, modifier: Modifier = Modifier,
    enabled: Boolean = true, danger: Boolean = false,
    action: suspend (onStep: (Int) -> Unit) -> String, onResult: (ok: Boolean, msg: String) -> Unit,
) {
    val scope = LocalAppScope.current ?: rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(0) }
    val base = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val bg by animateColorAsState(if (done) Tone.ok else base, tween(350), label = "fp")
    Button(
        onClick = {
            if (busy || done) return@Button
            busy = true; step = 0
            scope.launch {
                val r = runCatching { action { step = it } }
                busy = false
                r.onSuccess { done = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress); onResult(true, it) }
                    .onFailure { onResult(false, it.message ?: "صار خطأ") }
            }
        },
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = MaterialTheme.colorScheme.onPrimary, disabledContainerColor = bg.copy(alpha = 0.35f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                done -> Text("✓  $doneLabel", style = MaterialTheme.typography.labelLarge)
                busy -> {
                    CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Text(steps.getOrElse(step.coerceAtMost(steps.lastIndex)) { "…" }, style = MaterialTheme.typography.labelLarge)
                }
                else -> {
                    Icon(SirfariIcons.Fingerprint, null, Modifier.size(22.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** قبل البصمة: «وش بيتغير؟» من الخطة نفسها، ثم الموافقة. يفتح من الرئيسية. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ApproveSheet(taskId: String, api: Api, actions: Actions, onClose: () -> Unit, onDone: () -> Unit, openTask: (String) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var t by remember { mutableStateOf<TaskDetail?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(taskId, reload) { err = null; runCatching { Models.task(api.get("/api/tasks/$taskId")) }.fold({ t = it }, { err = it.message }) }
    LaunchedEffect(msg) { if (msg?.first == true) { delay(1300); onDone(); onClose() } }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val d = t
            if (d == null) { LoadState(err) { reload++ }; return@Column }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(if (d.planReady) "الخطة جاهزة" else "الخطة لسا تنكتب", if (d.planReady) Tone.warn else Tone.run)
                Mono("${d.projectId} · ${d.taskId}")
            }
            Text(d.title.ifBlank { d.taskId }, style = MaterialTheme.typography.headlineSmall)
            d.plan?.let { p ->
                Panel(padding = 16) {
                    Text("وش بيتغير؟", style = MaterialTheme.typography.titleSmall)
                    if (p.summary.isNotBlank()) Text(p.summary, style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("${p.steps.size} خطوات", MaterialTheme.colorScheme.primary)
                        Chip("${p.files.size} ملفات", MaterialTheme.colorScheme.primary)
                        Chip(if (p.risks.isEmpty()) "بدون مخاطر مذكورة" else "${p.risks.size} مخاطر", if (p.risks.isEmpty()) Tone.ok else Tone.warn)
                    }
                    p.risks.take(3).forEach { r ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(SirfariIcons.Warn, null, Modifier.size(16.dp), tint = Tone.warn)
                            Text(r, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    val who = listOfNotNull(d.first?.let { "$it ينفذ" }, d.second?.let { "$it يراجع" }).joinToString(" و")
                    if (who.isNotBlank()) Text(who, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FingerprintButton(
                "وافق بالبصمة", "بدأ التنفيذ", Actions.STEPS["approve"] ?: emptyList(), enabled = d.planReady,
                action = { onStep -> actions.decide(d.taskId, "approve", null, onStep) },
                onResult = { ok, m -> msg = ok to m },
            )
            msg?.let { (ok, m) -> Text(m, color = if (ok) Tone.ok else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(onClick = { onClose(); openTask(d.taskId) }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) { Text("افتح الخطة كاملة") }
        }
    }
}

/** طلب root: وش بيصير بالضبط، ثم بصمتك أو الرفض */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSheet(req: JSONObject, actions: Actions, onClose: () -> Unit, onDone: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = LocalAppScope.current ?: rememberCoroutineScope()
    var msg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var rejecting by remember { mutableStateOf(false) }
    LaunchedEffect(msg) { if (msg?.first == true) { delay(1300); onDone(); onClose() } }
    val action = req.optString("action")
    val effect = when (action) {
        "install" -> "يثبّت نسخة جديدة من الخدمات على السيرفر ويعيد تشغيلها. التطبيق ممكن يفصل ثواني."
        "fix" -> "إجراء صيانة من «الإجراءات الذكية». root يتأكد إنه ما يقطع شي قبل ما ينفّذ."
        "trust" -> "يضيف هالجهاز بالذات لقائمة الأجهزة اللي تقدر توافق على طلبات root. ما يشيل أي جهاز."
        else -> "ينفّذ «$action» بصلاحية root."
    }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("طلب root", Tone.run)
                Mono(req.optString("id"))
            }
            Text(req.optString("summary").ifBlank { action }, style = MaterialTheme.typography.headlineSmall)
            Panel(padding = 16) {
                Text("وش بيصير؟", style = MaterialTheme.typography.titleSmall)
                Text(effect, style = MaterialTheme.typography.bodyMedium)
            }
            if (!actions.trustedFor(req)) Panel(padding = 14) {
                Text("جوالك الأصلي مو موثوق عند root للحين", style = MaterialTheme.typography.titleSmall, color = Tone.warn)
                Text("وافق على هالطلب من سيرفري في المتصفح (Chrome). أول تحديث تثبّته يوثّق جوالك، وبعدها توافق من هنا.", style = MaterialTheme.typography.bodySmall)
            } else FingerprintButton(
                "وافق بالبصمة", "وافقت، root ينفّذ", listOf("بصمتك", "التحقق من بصمتك", "root ينفّذ"),
                action = { _ -> actions.adminApprove(req) }, onResult = { ok, m -> msg = ok to m },
            )
            OutlinedButton(
                onClick = {
                    if (rejecting) return@OutlinedButton
                    rejecting = true
                    scope.launch { msg = runCatching { actions.adminReject(req.optString("id")); "انرفض الطلب" }.fold({ true to it }, { false to (it.message ?: "صار خطأ") }); rejecting = false }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("ارفض") }
            msg?.let { (ok, m) -> Text(m, color = if (ok) Tone.ok else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** طلبات root المعلّقة من رد /api/admin */
fun pendingAdmin(admin: JSONObject?): List<JSONObject> {
    val list = admin?.optJSONArray("requests") ?: return emptyList()
    return (0 until list.length()).mapNotNull { list.optJSONObject(it) }.filter { it.optString("state") == "pending" }
}

