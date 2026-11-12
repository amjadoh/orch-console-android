package com.amjadoh.sirfari

import org.json.JSONArray
import org.json.JSONObject

// شكل بيانات سيرفري كما يرجّعها السيرفر (/api/overview و/api/server). كل حقل اختياري:
// لو السيرفر أقدم أو أحدث، نعرض اللي نعرفه وما نطيح.

data class Problem(val key: String, val title: String, val level: String, val line: String)
data class TaskCard(val taskId: String, val projectId: String, val title: String, val status: String, val step: String)
data class Overview(
    val awaiting: Int, val running: Int, val doneToday: Int, val failed: Int,
    val healthLevel: String, val problems: List<Problem>, val tasks: List<TaskCard>,
    val adminPending: Int, val serverBad: Int, val serverWarn: Int, val maintenanceUntil: Long?,
)

data class Disk(val mount: String, val pct: Double, val usedGb: Double, val totalGb: Double)
data class Mem(val totalMb: Double, val availMb: Double, val swapTotalMb: Double, val swapUsedMb: Double) {
    val usedPct get() = if (totalMb > 0) (totalMb - availMb) / totalMb * 100 else 0.0
    val swapPct get() = if (swapTotalMb > 0) swapUsedMb / swapTotalMb * 100 else 0.0
}
data class Service(val label: String, val group: String, val state: String, val protected: Boolean)
data class Container(val label: String, val state: String, val status: String, val onDemand: Boolean)
data class Timer(val label: String, val kind: String, val result: String, val lastAt: Long?)
data class Alert(val level: String, val text: String)
data class Consumer(val label: String, val rssMb: Double, val protected: Boolean)
data class ServerView(
    val at: Long?, val hostName: String?, val cpuPct: Double?, val load: List<Double>, val uptimeSec: Long?,
    val mem: Mem?, val disks: List<Disk>, val services: List<Service>, val containers: List<Container>,
    val timers: List<Timer>, val alerts: List<Alert>, val consumers: List<Consumer>, val tailscaleOnline: Boolean?,
)

private fun JSONObject.str(k: String) = if (has(k) && !isNull(k)) optString(k) else null
private fun JSONObject.num(k: String) = if (has(k) && !isNull(k)) optDouble(k).takeIf { !it.isNaN() } else null
private inline fun <T> JSONArray?.items(f: (JSONObject) -> T?): List<T> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it)?.let(f) }

object Models {
    fun overview(o: JSONObject): Overview {
        val c = o.optJSONObject("counts") ?: JSONObject()
        val h = o.optJSONObject("health") ?: JSONObject()
        val s = o.optJSONObject("server") ?: JSONObject()
        return Overview(
            awaiting = c.optInt("awaiting"), running = c.optInt("running"), doneToday = c.optInt("doneToday"), failed = c.optInt("failed"),
            healthLevel = h.str("level") ?: "unknown",
            problems = h.optJSONArray("problems").items { Problem(it.optString("key"), it.optString("title"), it.optString("level"), it.optString("line")) },
            tasks = o.optJSONArray("tasks").items { TaskCard(it.optString("taskId"), it.optString("projectId"), it.optString("title"), it.optString("status"), it.optString("step")) },
            adminPending = o.optInt("adminPending"), serverBad = s.optInt("bad"), serverWarn = s.optInt("warn"),
            maintenanceUntil = h.num("maintenanceUntil")?.toLong(),
        )
    }

    fun server(o: JSONObject): ServerView {
        val srv = o.optJSONObject("server")
        val host = srv?.optJSONObject("host") ?: JSONObject()
        val m = host.optJSONObject("mem")
        val load = host.optJSONArray("load")
        return ServerView(
            at = srv?.num("at")?.toLong(),
            hostName = host.str("name"),
            cpuPct = host.num("cpuPct"),
            load = if (load == null) emptyList() else (0 until load.length()).map { load.optDouble(it) }.filter { !it.isNaN() },
            uptimeSec = host.num("uptimeSec")?.toLong(),
            mem = m?.let { Mem(it.optDouble("totalMb"), it.optDouble("availMb"), it.optDouble("swapTotalMb"), it.optDouble("swapUsedMb")) },
            disks = host.optJSONArray("disks").items { Disk(it.optString("mount"), it.optDouble("pct"), it.optDouble("usedGb"), it.optDouble("totalGb")) },
            services = srv?.optJSONArray("services").items { Service(it.optString("label"), it.optString("group"), it.optString("state"), it.optBoolean("protected")) },
            containers = o.optJSONObject("docker")?.optJSONArray("containers").items { Container(it.optString("label"), it.optString("state"), it.optString("status"), it.optBoolean("onDemand")) },
            timers = srv?.optJSONArray("timers").items { Timer(it.optString("label"), it.optString("kind"), it.optString("result"), it.num("lastAt")?.toLong()) },
            alerts = o.optJSONArray("alerts").items { Alert(it.optString("level"), it.optString("text")) },
            consumers = host.optJSONArray("consumers").items { Consumer(it.optString("label"), it.optDouble("rssMb"), it.optBoolean("protected")) },
            tailscaleOnline = host.optJSONObject("tailscale")?.optBoolean("online"),
        )
    }
}
