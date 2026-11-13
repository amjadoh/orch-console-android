package com.amjadoh.sirfari

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

// «الإجراءات الذكية»: وصفات صيانة ثابتة من السيرفر (ops/maintenance/catalog.mjs). التطبيق يعرض وش بيصير،
// وبعد بصمتك root يعيد فحص «مشغول؟» وينفّذ أوامر ثابتة ويتحقق. التطبيق ما يرسل أوامر أبداً، معرّف الوصفة بس.

data class Guard(val state: String, val why: String)
data class Recipe(
    val id: String, val label: String, val area: String, val effect: String, val impact: String, val rollback: String,
    val protected: Boolean, val guard: Guard, val pending: Boolean,
)
data class Suggestion(val id: String, val why: String)
data class Catalog(val available: Boolean, val recipes: List<Recipe>, val suggestions: List<Suggestion>, val areas: Map<String, String>) {
    fun recipe(id: String) = recipes.firstOrNull { it.id == id }
}

private fun JSONArray?.objs(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

object Maint {
    fun catalog(o: JSONObject): Catalog {
        val areas = o.optJSONObject("areas")?.let { a -> a.keys().asSequence().associateWith { a.optString(it) } } ?: emptyMap()
        return Catalog(
            available = o.optBoolean("available"),
            recipes = o.optJSONArray("recipes").objs().map {
                val g = it.optJSONObject("guard")
                Recipe(it.optString("id"), it.optString("label"), it.optString("area"), it.optString("effect"), it.optString("impact"), it.optString("rollback"),
                    it.optBoolean("protected"), Guard(g?.optString("state") ?: "later", g?.optString("why").orEmpty()), it.optBoolean("pending"))
            },
            suggestions = o.optJSONArray("suggestions").objs().map { Suggestion(it.optString("id"), it.optString("why")) },
            areas = areas,
        )
    }
}

/** يفتح ورقة الإجراء من أي مكان (الرئيسية، السيرفر، تحليل Claude) */
val LocalOpenRecipe = compositionLocalOf<(String, String?) -> Unit> { { _, _ -> } }

private val AREA_ORDER = listOf("memory", "disk", "service", "data")

/** قسم «إجراءات ذكية» في السيرفر: المقترح الحين بسببه، وتحته كل الإجراءات */
@Composable
fun SmartActions(c: Catalog?) {
    if (c == null || !c.available) return
    val open = LocalOpenRecipe.current
    var all by rememberSaveable { mutableStateOf(false) }
    Section("إجراءات ذكية", if (all) "اطوِ" else "كل الإجراءات", { all = !all }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (c.suggestions.isEmpty() && !all) Panel(padding = 14) {
                Text("ما فيه شي يحتاج إجراء الحين", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            c.suggestions.forEach { s -> c.recipe(s.id)?.let { r -> SuggestionCard(r, s.why) { open(r.id, s.why) } } }
            if (all) Panel(padding = 0) {
                val list = c.recipes.sortedBy { AREA_ORDER.indexOf(it.area).let { i -> if (i < 0) 9 else i } }
                list.forEachIndexed { i, r ->
                    ListRow(Modifier.clickable { open(r.id, null) }, divider = i < list.lastIndex) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(r.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(c.areas[r.area] ?: r.area, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            when {
                                r.pending -> Chip("ينتظر بصمتك", Tone.warn)
                                r.protected -> Chip("محمي", Tone.run)
                                r.guard.state == "busy" -> Chip("مشغول", Tone.warn)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestionCard(r: Recipe, why: String, onOpen: () -> Unit) {
    Panel(padding = 14, modifier = Modifier.clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(if (r.protected) SirfariIcons.Shield else SirfariIcons.Sparkle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(r.label, style = MaterialTheme.typography.titleSmall)
                Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("وش بيصير؟", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** ورقة الإجراء: وش يسوي، وش يتأثر، التراجع، الحالة الحين، وبعدها «اطلب ووافق بالبصمة» */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeSheet(r: Recipe, why: String?, actions: Actions, onClose: () -> Unit, onDone: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var msg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var agree by remember { mutableStateOf(!r.protected) }
    LaunchedEffect(msg) { if (msg?.first == true) { delay(2500); onDone(); onClose() } }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(if (r.protected) "إجراء محمي" else "إجراء ذكي", if (r.protected) Tone.run else MaterialTheme.colorScheme.primary)
                when (r.guard.state) {
                    "busy" -> Chip("مشغول الحين", Tone.warn)
                    "unknown" -> Chip("ما تأكدت", Tone.warn)
                    "idle" -> Chip("جاهز", Tone.ok)
                    else -> {}
                }
            }
            Text(r.label, style = MaterialTheme.typography.headlineSmall)
            why?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Panel(padding = 16) {
                Fact("وش يسوي", r.effect)
                Fact("وش يتأثر", r.impact)
                Fact("التراجع", r.rollback)
                if (r.guard.why.isNotBlank()) Fact("الحين", r.guard.why)
            }
            Text("وقت التنفيذ root يتأكد من جديد إنه ما يقطع شي. لو مشغول ما ينفّذ ويقول لك.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (r.protected) Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { agree = !agree }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = agree, onCheckedChange = { agree = it })
                Text("أفهم إن هذا يمس شي محمي ويوقف لحظات", style = MaterialTheme.typography.bodyMedium)
            }
            if (r.pending) Text("نفس الإجراء ينتظر بصمتك في «يحتاج قرارك».", color = Tone.warn, style = MaterialTheme.typography.bodySmall)
            FingerprintButton(
                "اطلب ووافق بالبصمة", "تم", listOf("إرسال الطلب لـ root", "بصمتك", "root يفحص وينفّذ"), enabled = agree && !r.pending,
                action = { onStep ->
                    onStep(0)
                    val req = actions.requestRecipe(r.id, why.orEmpty())
                    onStep(1)
                    actions.adminApprove(req) { onStep(2) }
                },
                onResult = { ok, m -> msg = ok to m },
            )
            msg?.let { (ok, m) -> Text(m, color = if (ok) Tone.ok else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun Fact(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
