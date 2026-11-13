package com.amjadoh.sirfari

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Calendar

enum class Tab(val label: String, val icon: ImageVector) {
    Home("الرئيسية", SirfariIcons.Home),
    Tasks("المهام", SirfariIcons.Tasks),
    Server("السيرفر", SirfariIcons.Server),
    Space("المساحة", SirfariIcons.Space),
    Files("المخرجات", SirfariIcons.Files),
}

private const val REFRESH_MS = 20_000L

/** نطاق التطبيق: القرار بالبصمة يكمّل حتى لو طلعت من صفحة المهمة (ما ينقطع بالنص) */
val LocalAppScope = staticCompositionLocalOf<CoroutineScope?> { null }

/** يزيد مع كل «اسحب للتحديث»: كل شاشة تعيد تحميل بياناتها هي (المساحة، المخرجات، المشاريع، التشخيص) */
val LocalRefresh = compositionLocalOf { 0 }

/** عدد القرارات اللي تنتظرك: المهام اللي خطتها جاهزة + طلبات root */
fun decisionsCount(o: Overview?): Int = if (o == null) 0 else o.tasks.count { it.status == "awaiting" && !it.planPending } + o.adminPending

/**
 * هيكل سيرفري 2.1: رأس بسيط (تحية أو اسم الشاشة + الإعدادات)، والمحتوى، وشريط تبويبات عائم تحت
 * عليه عدد اللي ينتظرك. التحديث كل 20 ثانية والتطبيق قدامك بس.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SirfariApp(
    repo: Repo, api: Api, actions: Actions, openTask: MutableState<String?>,
    dark: Boolean = true, onToggleDark: () -> Unit = {}, fingerprint: @Composable () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var overview by remember { mutableStateOf<Repo.Result<Overview>?>(null) }
    var server by remember { mutableStateOf<Repo.Result<ServerView>?>(null) }
    var admin by remember { mutableStateOf<JSONObject?>(null) }
    var selectedTask by rememberSaveable { mutableStateOf<String?>(null) }
    var showCommand by rememberSaveable { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(openTask.value) {
        openTask.value?.let { selectedTask = it; tab = Tab.Tasks; showSettings = false; openTask.value = null }
    }

    suspend fun refresh() {
        overview = repo.load("/api/overview", Models::overview)
        if (tab == Tab.Server || tab == Tab.Home || server == null) server = repo.load("/api/server", Models::server)
        if (tab == Tab.Tasks || tab == Tab.Home) admin = runCatching { api.get("/api/admin") }.getOrNull() ?: admin
        now = System.currentTimeMillis()
    }
    val refreshNow: () -> Unit = { scope.launch { refresh() } }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(tab) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                refresh()
                delay(REFRESH_MS)
            }
        }
    }

    BackHandler(enabled = showSettings || tab != Tab.Home || selectedTask != null) {
        when {
            showSettings -> showSettings = false
            tab == Tab.Tasks && selectedTask != null -> selectedTask = null
            else -> tab = Tab.Home
        }
    }

    val badge = decisionsCount(overview?.data)
    // صفحة المهمة والجوال مقفول: نخفي شريط التبويبات عشان شريط القرار ياخذ مكانه تحت
    val hideNav = !showSettings && tab == Tab.Tasks && selectedTask != null && androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 700

    CompositionLocalProvider(LocalAppScope provides scope, LocalRefresh provides tick) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Header(
                    tab = tab, settings = showSettings,
                    onSettings = { showSettings = !showSettings },
                    onCommand = if (tab == Tab.Tasks && !showSettings) ({ showCommand = true }) else null,
                )
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { scope.launch { refreshing = true; tick++; refresh(); refreshing = false } },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    if (tab == Tab.Tasks && !showSettings) {
                        // المهام تدير تمريرها بنفسها: القائمة تتمرر، وصفحة المهمة لها شريط قرار ثابت تحت
                        TasksScreen(
                            overview, admin, now, selectedTask, { selectedTask = it }, api, actions,
                            onChanged = refreshNow, bottomInset = if (hideNav) 0 else 96,
                        )
                    } else {
                        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
                            Column(
                                Modifier.widthIn(max = if (tab == Tab.Home) 1000.dp else 760.dp).fillMaxWidth()
                                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 112.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp),
                            ) {
                                when {
                                    showSettings -> SettingsScreen(dark, onToggleDark, fingerprint)
                                    tab == Tab.Home -> HomeScreen(
                                        overview, server, admin, now, actions,
                                        openServer = { tab = Tab.Server },
                                        openTask = { selectedTask = it; tab = Tab.Tasks },
                                        openTasks = { selectedTask = null; tab = Tab.Tasks },
                                        onRetry = refreshNow, onChanged = refreshNow,
                                    )
                                    tab == Tab.Server -> ServerScreen(server, now, onRetry = refreshNow) { DiagnosisSection(api) { refreshNow() } }
                                    tab == Tab.Space -> SpaceScreen(api)
                                    else -> FilesScreen(api)
                                }
                            }
                        }
                    }
                }
            }
            if (!hideNav) FloatingNav(
                current = if (showSettings) null else tab, badge = badge,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) { tab = it; showSettings = false }
        }
    }
    if (showCommand) CommandSheet(api, actions, onClose = { showCommand = false }, onSent = refreshNow)
}

private val DAYS = listOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")
private val MONTHS = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")

@Composable
private fun Header(tab: Tab, settings: Boolean, onSettings: () -> Unit, onCommand: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            if (!settings && tab == Tab.Home) {
                val c = remember { Calendar.getInstance() }
                val hour = c.get(Calendar.HOUR_OF_DAY)
                Text("${DAYS[c.get(Calendar.DAY_OF_WEEK) - 1]} · ${c.get(Calendar.DAY_OF_MONTH)} ${MONTHS[c.get(Calendar.MONTH)]}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (hour in 4..11) "صباح الخير يا أمجد" else "مساء الخير يا أمجد", style = MaterialTheme.typography.titleLarge)
            } else {
                Text(if (settings) "الإعدادات" else tab.label, style = MaterialTheme.typography.titleLarge)
            }
        }
        if (onCommand != null) {
            Row(
                Modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable(onClick = onCommand).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(SirfariIcons.Plus, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text("أمر جديد", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }
        SquareIcon(if (settings) SirfariIcons.Back else SirfariIcons.Settings, if (settings) "رجوع" else "الإعدادات", onClick = onSettings)
    }
}

/** شريط التبويبات العائم، وعلى «المهام» عدد اللي ينتظرك */
@Composable
private fun FloatingNav(current: Tab?, badge: Int, modifier: Modifier = Modifier, onSelect: (Tab) -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(modifier.fillMaxWidth().widthIn(max = 640.dp).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().height(68.dp).shadow(18.dp, shape, clip = false)
                .clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh).border(1.dp, LocalSirfari.current.line, shape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { t ->
                val sel = t == current
                val color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    Modifier.weight(1f).fillMaxSize().clickable(onClickLabel = t.label) { onSelect(t) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(width = 52.dp, height = 30.dp).clip(RoundedCornerShape(15.dp))
                                .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) { Icon(t.icon, contentDescription = null, tint = color, modifier = Modifier.size(21.dp)) }
                        if (t == Tab.Tasks && badge > 0) Text(
                            "$badge", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).clip(RoundedCornerShape(9.dp)).background(Tone.warn).padding(horizontal = 6.dp),
                        )
                    }
                    Text(t.label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal), color = color, maxLines = 1)
                }
            }
        }
    }
}

/** الإعدادات: المظهر، والبصمة وربط الجوال وفحص الاتصال */
@Composable
private fun SettingsScreen(dark: Boolean, onToggleDark: () -> Unit, fingerprint: @Composable () -> Unit) {
    Section("المظهر") {
        Panel(padding = 6) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(true to "داكن", false to "فاتح").forEach { (d, label) ->
                    val sel = d == dark
                    Row(
                        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                            .clickable { if (!sel) onToggleDark() },
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (d) SirfariIcons.Moon else SirfariIcons.Sun, null, Modifier.size(18.dp), tint = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("  $label", style = MaterialTheme.typography.labelLarge, color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    Section("البصمة والاتصال") { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { fingerprint() } }
}
