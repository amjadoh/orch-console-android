package com.amjadoh.sirfari

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    @Test
    fun overviewFromServerShape() {
        val o = Models.overview(JSONObject("""
            {"counts":{"awaiting":2,"running":1,"doneToday":3,"failed":0},
             "tasks":[{"taskId":"MSR-0002","projectId":"masroof","title":"تصليح","status":"awaiting","step":"approve"}],
             "server":{"bad":1,"warn":2},"adminPending":1,
             "health":{"level":"watch","problems":[{"key":"mem","title":"الذاكرة","level":"watch","line":"متاح 900 ميجا"}],"maintenanceUntil":null}}
        """))
        assertEquals(2, o.awaiting); assertEquals(1, o.adminPending); assertEquals("watch", o.healthLevel)
        assertEquals("MSR-0002", o.tasks.single().taskId); assertEquals("الذاكرة", o.problems.single().title)
        assertEquals(3, o.serverBad + o.serverWarn); assertNull(o.maintenanceUntil)
    }

    @Test
    fun overviewToleratesMissingFields() {
        val o = Models.overview(JSONObject("{}"))
        assertEquals(0, o.awaiting); assertEquals("unknown", o.healthLevel); assertTrue(o.tasks.isEmpty())
    }

    @Test
    fun serverFromServerShape() {
        val s = Models.server(JSONObject("""
            {"server":{"at":1700000000000,"host":{"name":"amjad-server","cpuPct":12.5,"load":[0.3,0.5,0.4],"uptimeSec":600000,
               "mem":{"totalMb":8000,"availMb":2000,"usedMb":6000,"swapTotalMb":4000,"swapUsedMb":1000},
               "disks":[{"mount":"/","pct":61,"usedGb":45,"totalGb":75}],"tailscale":{"state":"Running","online":true},
               "consumers":[{"label":"مدير الأعمال","protected":true,"rssMb":900}]},
             "services":[{"label":"orchd","group":"orch","state":"active","protected":false}],
             "timers":[{"label":"النسخ الاحتياطي","kind":"backup","result":"success","lastAt":1699990000000}]},
             "docker":{"containers":[{"label":"n8n","state":"exited","status":"","onDemand":true}]},
             "alerts":[{"level":"warn","text":"الـ swap"}]}
        """))
        assertEquals(75.0, s.mem!!.usedPct, 0.01); assertEquals(25.0, s.mem!!.swapPct, 0.01)
        assertEquals(61.0, s.disks.single().pct, 0.0); assertEquals(3, s.load.size)
        assertEquals("orchd", s.services.single().label); assertTrue(s.containers.single().onDemand)
        assertEquals(true, s.tailscaleOnline); assertEquals("warn", s.alerts.single().level)
    }

    @Test
    fun serverWithoutCollector() {
        val s = Models.server(JSONObject("""{"server":null,"docker":null,"alerts":[{"level":"bad","text":"ما فيه بيانات"}]}"""))
        assertNull(s.at); assertNull(s.mem); assertEquals(1, s.alerts.size)
    }

    @Test
    fun taskDecisionsFollowWebRules() {
        val t = Models.task(JSONObject("""
            {"taskId":"MSR-0002","projectId":"masroof","title":"x","status":"awaiting","first":"Claude","second":"Codex","request":"r",
             "plan":{"summary":"s","steps":["a","b"],"files":["app/A.kt"],"risks":[],"planner":"Claude"},
             "planState":"ready","planPending":false,"planRevising":false,"revisionsLeft":3,
             "attempts":{"attempt":1,"total":1,"max":3,"next":null},
             "activity":{"at":1,"live":true,"agent":"Claude","items":[{"kind":"edit","tool":"Edit","path":"app/A.kt","cmd":null}]}}
        """))
        assertTrue(t.planReady); assertTrue(t.canRevise); assertTrue(t.canSwap)
        assertEquals(2, t.plan!!.steps.size); assertEquals("app/A.kt", t.activity!!.items.single().path)
        val pending = Models.task(JSONObject("""{"taskId":"A-0001","status":"awaiting","planState":"running","planPending":true,"revisionsLeft":3}"""))
        assertTrue(!pending.planReady && !pending.canRevise && !pending.canSwap)
        val failed = Models.task(JSONObject("""{"taskId":"A-0001","status":"failed","attempts":{"total":1,"max":3,"next":null}}"""))
        assertTrue(failed.canRetry)
        val last = Models.task(JSONObject("""{"taskId":"A-0001","status":"failed","attempts":{"total":3,"max":3,"next":null}}"""))
        assertTrue(!last.canRetry)
    }

    @Test
    fun sessionsOpenNowAndNewProject() {
        val v = SessionsModel.parse(JSONObject("""{"installed":true,"list":"🟢 المفتوح:\n• orch-console-android — آخر استخدام قبل 3 دقيقة\n\n📂 تقدر تفتح: المصروف، tv-remote\n"}"""))
        assertEquals(listOf("orch-console-android"), v.openNow)
        assertEquals(listOf("المصروف", "tv-remote"), v.openable)
        assertEquals(listOf("orch-console-android", "tv-remote", "المصروف"), v.allProjects)
        assertNull(validNewProject("متجر العطور", v.allProjects))
        assertNull(validNewProject("shop_2.0", v.allProjects))
        assertTrue(validNewProject("TV-Remote", v.allProjects)!!.contains("فيه مشروع"))
        for (bad in listOf("", " ", "a/b", "-x", "a..b", "x.bak", "app-e2194b3", "it's", "a".repeat(41))) assertTrue(bad, validNewProject(bad, v.allProjects) != null)
    }

    @Test
    fun codexThreads() {
        val v = SessionsModel.parse(JSONObject("""{"installed":true,"list":"","codex":{"at":1,"ok":true,"threads":[
            {"id":"01a1d6bd-0fce-77b3-888b-ddc3ffcfce35","name":"المصروف","project":"المصروف","state":"ready","stateSec":1200,"subagents":2},
            {"id":"01a1d6bd-0fce-77b3-888b-ddc3ffcfce36","name":null,"project":"tv-remote","state":"working","stateSec":5,"subagents":0},
            {"id":"01a1d6bd-0fce-77b3-888b-ddc3ffcfce37","name":"x","state":"ready","stateSec":60,"subagents":0},
            {"id":"01a1d6bd-0fce-77b3-888b-ddc3ffcfce38","name":"y","state":"unknown","stateSec":null,"subagents":0}]},
            "all":{"at":1,"stale":false,"sessions":[{"id":"9-1","pid":9,"start":1,"kind":"codex-app","label":"خدمة Codex","project":null,"ageSec":10,"rssMb":300,"state":"unknown","stateSec":null}]}}"""))
        assertEquals(5, v.all.size)
        val (a, b, c, d) = v.all.take(4)
        assertTrue(a.isCodexThread && a.project == "المصروف" && a.subagents == 2)
        assertNull("جاهزة من 20 دقيقة: تنقفل", a.blockReason)
        assertEquals("tv-remote", b.project)
        assertTrue("تشتغل", b.blockReason != null)
        assertTrue("خلصت قبل دقيقة", c.blockReason != null)
        assertTrue("غير معروفة", d.blockReason != null)
        assertTrue("الخدمة نفسها ما تنقفل", v.all[4].blockReason!!.contains("خدمة Codex"))
        assertTrue(!v.codexDown)
        assertTrue(SessionsModel.parse(JSONObject("""{"codex":{"at":1,"ok":false,"threads":[]}}""")).codexDown)
        assertTrue(!v.codexPartial)
        assertTrue(SessionsModel.parse(JSONObject("""{"codex":{"at":1,"ok":true,"complete":false,"threads":[]}}""")).codexPartial)
    }
}
