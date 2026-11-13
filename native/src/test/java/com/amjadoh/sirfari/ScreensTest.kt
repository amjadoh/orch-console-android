package com.amjadoh.sirfari

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.graphics.Canvas
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * يرسم شاشات سيرفري الأصلي ببيانات بنفس شكل السيرفر، بالمقاس المقفول (411dp) والمفتوح (840dp)،
 * ويتأكد إنها ترسم بدون ما تطيح، ويحفظ صورة لكل وحدة (تنرفع مع البناء في GitHub للمراجعة).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreensTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val now = 1_700_000_000_000L

    private val overview = Models.overview(JSONObject("""
        {"counts":{"awaiting":1,"running":1,"doneToday":3,"failed":0},"adminPending":1,"server":{"bad":0,"warn":1},
         "health":{"level":"watch","problems":[{"key":"mem","title":"الذاكرة","level":"watch","line":"متاح 900 ميجا من 8 جيجا"}]},
         "tasks":[{"taskId":"MSR-0002","projectId":"masroof","title":"أضف تصدير PDF للمصروفات","status":"awaiting"},
                  {"taskId":"ORC-0011","projectId":"alatheel-orchestrator","title":"تحسين المتابعة الحية","status":"running"},
                  {"taskId":"SAB-0004","projectId":"SAB_Visa","title":"مطابقة الأقساط","status":"done"}]}
    """))
    private val server = Models.server(JSONObject("""
        {"server":{"at":$now,"host":{"name":"amjad-server","cpuPct":23,"load":[0.42,0.55,0.61],"uptimeSec":600000,
           "mem":{"totalMb":7945,"availMb":3896,"usedMb":4049,"swapTotalMb":4096,"swapUsedMb":1200},
           "disks":[{"mount":"/","pct":61,"usedGb":45,"totalGb":75}],"tailscale":{"state":"Running","online":true},
           "consumers":[{"label":"مدير الأعمال","protected":true,"rssMb":910},{"label":"orchd","protected":false,"rssMb":180}]},
         "services":[{"label":"المنظّم","group":"orch","state":"active","protected":false},{"label":"سيرفري","group":"orch","state":"active"},
                     {"label":"بوت تلقرام","group":"bots","state":"active","protected":true},{"label":"fail2ban","group":"infra","state":"active"}],
         "timers":[{"label":"النسخ الاحتياطي","kind":"backup","result":"success","lastAt":${now - 7_200_000}}]},
         "docker":{"containers":[{"label":"n8n","state":"exited","status":"","onDemand":true}]},
         "alerts":[{"level":"warn","text":"الـ swap فوق 25%"}]}
    """))
    private val task = Models.task(JSONObject("""
        {"taskId":"MSR-0002","projectId":"masroof","title":"أضف تصدير PDF للمصروفات","status":"awaiting","first":"Claude","second":"Codex",
         "request":"أضف زر تصدير PDF في شاشة المصروفات الشهرية",
         "plan":{"summary":"زر جديد في شاشة الشهر يصدّر PDF بالعربي","steps":["إضافة PdfExporter","زر في الشاشة","اختبار للحالة الفاضية"],
                 "files":["app/src/main/java/PdfExporter.kt"],"risks":["الخط العربي في PDF"],"planner":"Claude"},
         "planState":"ready","planPending":false,"planRevising":false,"revisionsLeft":3,
         "attempts":{"total":1,"max":3,"next":null},
         "activity":{"at":$now,"live":true,"agent":"Claude","items":[{"kind":"read","tool":"Read","path":"app/Expense.kt"},{"kind":"run","tool":"Bash","cmd":"./gradlew test"}]},
         "issueUrl":"https://github.com/amjadoh/orch-inbox/issues/12"}
    """))

    private fun screen(name: String, scroll: Boolean = true, dark: Boolean? = null, content: @Composable () -> Unit) {
        rule.setContent {
            SirfariTheme(dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    if (scroll) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) { content() }
                    else Column(Modifier.fillMaxSize()) { content() }
                }
            }
        }
        rule.waitForIdle()
        // نرسم الشاشة نفسها (PixelCopy ما يشتغل في Robolectric بدون عتاد)
        val v = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
        val dir = File("build/shots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val admin = JSONObject("""{"requests":[{"id":"adm-1111111111111111","action":"install","state":"pending","summary":"تحديث سيرفري"}]}""")

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun homeLight() {
        screen("home-light-411", dark = false) { HomeScreen(Repo.Result(overview, now, false, null), Repo.Result(server, now, false, null), admin, now, null, {}, {}) }
        rule.onNodeWithText("شغّال الحين").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w840dp-h900dp-xhdpi")
    fun homeOpened() {
        screen("home-840", dark = true) { HomeScreen(Repo.Result(overview, now, false, null), Repo.Result(server, now, false, null), admin, now, null, {}, {}) }
        rule.onNodeWithText("يحتاج قرارك").assertIsDisplayed()
    }

    private val catalog = Maint.catalog(JSONObject("""
        {"available":true,"areas":{"memory":"الذاكرة","disk":"القرص"},
         "suggestions":[{"id":"zram-on","why":"الـ Swap 99% ومافيه احتياط."},{"id":"restart-openclaw","why":"مدير الأعمال ماخذ 2700 ميجا."}],
         "recipes":[{"id":"zram-on","label":"تفعيل zram (Swap مضغوط في الرام)","area":"memory","effect":"x","impact":"y","rollback":"z","protected":false,"guard":{"state":"idle","why":""},"pending":false},
                    {"id":"restart-openclaw","label":"إعادة تشغيل مدير الأعمال","area":"memory","effect":"x","impact":"y","rollback":"z","protected":true,"guard":{"state":"later","why":"يتأكد وقت التنفيذ"},"pending":false},
                    {"id":"clean-gradle","label":"تنظيف كاش Gradle","area":"disk","effect":"x","impact":"y","rollback":"z","protected":false,"guard":{"state":"later","why":""},"pending":true}]}
    """))

    private val sessionsView = SessionsModel.parse(JSONObject("""
        {"installed":true,"list":"⚪ ما فيه جلسات مفتوحة.\n\n📂 تقدر تفتح: المصروف، الاخطبوط، tv-remote\n",
         "all":{"at":$now,"stale":false,"sessions":[
           {"id":"1580698-77","pid":1580698,"start":77,"kind":"claude-app","label":"Claude (التطبيق)","project":"alatheel-orchestrator","ageSec":172800,"rssMb":327,"state":"working","stateSec":30},
           {"id":"1555928-66","pid":1555928,"start":66,"kind":"claude-app","label":"Claude (التطبيق)","project":"اسطبل-الأثيل","ageSec":176400,"rssMb":219,"state":"ready","stateSec":90000},
           {"id":"1761-5","pid":1761,"start":5,"kind":"codex-app","label":"Codex (التطبيق)","project":null,"ageSec":666000,"rssMb":93,"state":"unknown","stateSec":null}]}}
    """))

    @Test
    fun sessionsParse() {
        org.junit.Assert.assertEquals(3, sessionsView.all.size)
        org.junit.Assert.assertEquals(listOf("المصروف", "الاخطبوط", "tv-remote"), sessionsView.openable)
        org.junit.Assert.assertNotNull("تشتغل = ما تنقفل", sessionsView.all[0].blockReason)
        org.junit.Assert.assertNull("جاهزة من زمان = تنقفل", sessionsView.all[1].blockReason)
        org.junit.Assert.assertNotNull("Codex ما ينقفل من هنا", sessionsView.all[2].blockReason)
        org.junit.Assert.assertNull(sessionsView.all[2].project)
    }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun sessionsList() {
        val a = actions()
        screen("sessions-411", dark = true) { SessionsSection(sessionsView, a) {} }
        rule.onNodeWithText("الجلسات المفتوحة").assertIsDisplayed()
        rule.onNodeWithText("اسطبل-الأثيل").assertIsDisplayed()
    }

    @Test
    fun catalogParses() {
        org.junit.Assert.assertEquals(listOf("zram-on", "restart-openclaw"), catalog.suggestions.map { it.id })
        org.junit.Assert.assertTrue(catalog.recipe("restart-openclaw")!!.protected)
        org.junit.Assert.assertTrue(catalog.recipe("clean-gradle")!!.pending)
    }

    private fun actions(): Actions {
        val act = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val device = DeviceKey(act)
        return Actions(Api("http://127.0.0.1:9"), Signer(act, device, "android:apk-key-hash:test"), device)
    }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun homeFolded() {
        screen("home-411", dark = true) { HomeScreen(Repo.Result(overview, now, false, null), Repo.Result(server, now, false, null), admin, now, null, {}, {}) }
        rule.onNodeWithText("ينتظرون قرارك").assertIsDisplayed()
        rule.onNodeWithText("وافق بالبصمة", substring = true).assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun serverFolded() {
        screen("server-411", dark = true) { ServerScreen(Repo.Result(server, now, false, null), now, smart = { SmartActions(catalog) }) }
        rule.onNodeWithText("تفعيل zram (Swap مضغوط في الرام)").assertExists()
        rule.onNodeWithText("الذاكرة").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun offlineShowsLastSnapshot() {
        screen("home-offline-411") { HomeScreen(Repo.Result(overview, now - 300_000, true, "ما وصلت للسيرفر. تأكد إن Tailscale شغّال"), null, null, now, null, {}, {}) }
        rule.onNodeWithText("بدون اتصال", substring = true).assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun taskDecisionsFolded() {
        val a = actions()
        screen("task-411", scroll = false, dark = true) { TaskPage(task, a, {}, {}, {}, showBack = true) }
        rule.onNodeWithText("وافق بالبصمة").assertIsDisplayed()
        rule.onNodeWithText("آخر حركات Claude وهو يخطط").assertExists()
    }

    @Test @Config(qualifiers = "w840dp-h900dp-night-xhdpi")
    fun shellOpenedDark() {
        // هيكل التطبيق كامل (بدون سيرفر: يطلع «ما وصلت»)، والتحديث الدوري ما نخليه يدور في الاختبار
        rule.mainClock.autoAdvance = false
        val a = actions()
        val api = Api("http://127.0.0.1:9")
        val repo = Repo(rule.activity, api)
        rule.setContent { SirfariTheme(dark = true) { SirfariApp(repo, api, a, androidx.compose.runtime.mutableStateOf(null)) {} } }
        rule.mainClock.advanceTimeBy(500)
        Thread.sleep(1500) // الطلب يفشل على IO
        rule.mainClock.advanceTimeBy(500)
        val v = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
        File("build/shots").mkdirs()
        FileOutputStream(File("build/shots/shell-840-dark.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        rule.onNodeWithText("المخرجات").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun chartRenders() {
        val pts = (0 until 60).map { Point(now - (60 - it) * 60_000L, 20.0 + (it % 10) * 3, 50.0 + (it % 7)) }
        screen("chart-411") { HistoryChart(pts) }
        rule.onNodeWithText("آخر ساعة").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w840dp-h900dp-xhdpi")
    fun tasksOpened() {
        val a = actions()
        val admin = JSONObject("""{"requests":[{"id":"adm-1111111111111111","action":"install","state":"pending","summary":"تحديث سيرفري"}]}""")
        screen("tasks-840") { TaskList(Repo.Result(overview, now, false, null), admin, now, null, {}, a, {}) }
        rule.onNodeWithText("طلبات root").assertIsDisplayed()
    }
}
