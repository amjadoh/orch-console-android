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
}
