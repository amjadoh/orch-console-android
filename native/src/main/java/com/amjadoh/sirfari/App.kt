package com.amjadoh.sirfari

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: ImageVector) {
    Home("الرئيسية", SirfariIcons.Home),
    Tasks("المهام", SirfariIcons.Tasks),
    Server("السيرفر", SirfariIcons.Server),
    Space("المساحة", SirfariIcons.Space),
    Files("المخرجات", SirfariIcons.Files),
}

private const val REFRESH_MS = 20_000L

/**
 * هيكل التطبيق: خمس تبويبات مثل سيرفري. NavigationSuiteScaffold يختار الشكل حسب الشاشة:
 * شريط تحت والجوال مطوي، وشريط جانبي لما ينفتح (HONOR Magic V5) أو على شاشة عريضة.
 * التحديث كل 20 ثانية والتطبيق قدامك بس، ويوقف بالخلفية.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SirfariApp(repo: Repo, fingerprint: @Composable () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var overview by remember { mutableStateOf<Repo.Result<Overview>?>(null) }
    var server by remember { mutableStateOf<Repo.Result<ServerView>?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        overview = repo.load("/api/overview", Models::overview)
        if (tab == Tab.Server || server == null) server = repo.load("/api/server", Models::server)
        now = System.currentTimeMillis()
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(tab) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                refresh()
                delay(REFRESH_MS)
            }
        }
    }

    BackHandler(enabled = showKey || tab != Tab.Home) { if (showKey) showKey = false else tab = Tab.Home }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Tab.entries.forEach { t ->
                item(
                    selected = tab == t && !showKey,
                    onClick = { tab = t; showKey = false },
                    icon = { Icon(t.icon, contentDescription = null) },
                    label = { Text(t.label) },
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (showKey) "البصمة" else tab.label, fontWeight = FontWeight.SemiBold) },
                    actions = {
                        IconButton(onClick = { showKey = !showKey }) { Icon(SirfariIcons.Key, contentDescription = "البصمة") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { pad ->
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { scope.launch { refreshing = true; refresh(); refreshing = false } },
                modifier = Modifier.fillMaxSize().padding(pad),
            ) {
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        when {
                            showKey -> fingerprint()
                            tab == Tab.Home -> HomeScreen(overview, now) { tab = Tab.Server }
                            tab == Tab.Server -> ServerScreen(server, now)
                            else -> Later(tab)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Later(tab: Tab) {
    val phase = when (tab) { Tab.Tasks -> "المرحلة 2"; else -> "المرحلة 3" }
    Panel(tab.label) {
        Text("هذي الشاشة تنبني في $phase من النسخة الأصلية. للحين استخدمها من تطبيق سيرفري الحالي.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
