package io.github.haroonjadoon.firmscope

import android.content.Context
import io.github.haroonjadoon.firmscope.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

class FirmwareStore(context: Context) {
    val prefs = context.applicationContext.getSharedPreferences("firmscope", Context.MODE_PRIVATE)
    companion object { private val lock = Any() }
    fun json(key: String) = runCatching { JSONObject(prefs.getString(key, "{}")!!) }.getOrDefault(JSONObject())
    fun array(key: String) = runCatching { JSONArray(prefs.getString(key, "[]")!!) }.getOrDefault(JSONArray())
    fun devices() = prefs.getStringSet("devices", emptySet()).orEmpty().sorted()
    fun watched() = prefs.getStringSet("watched", emptySet()).orEmpty().toSet()
    fun device(key: String): FirmwareCore.Device { val pair=key.split(":");return FirmwareCore.Device(pair[0],pair[1]) }
    fun saveDevice(device: FirmwareCore.Device, watch: Boolean? = null) = synchronized(lock) {
        val saved=devices().toMutableSet();saved.add(device.key())
        val edit=prefs.edit().putStringSet("devices",saved)
        if(watch!=null) { val watches=watched().toMutableSet(); if(watch) watches.add(device.key()) else watches.remove(device.key()); edit.putStringSet("watched",watches) }
        edit.apply()
    }
    fun removeDevice(key: String) = synchronized(lock) {
        prefs.edit().putStringSet("devices",devices().toMutableSet().apply { remove(key) })
            .putStringSet("watched",watched().toMutableSet().apply { remove(key) }).apply()
    }
    fun result(key: String): JSONObject? = json("result:$key").takeIf { it.has("model") }
    fun last(): JSONObject? {
        val key=prefs.getString("lastModel","")+":"+prefs.getString("lastCsc","")
        result(key)?.let { return it }
        val history=array("history")
        for(i in 0 until history.length()) { val item=history.optJSONObject(i) ?: continue; if(item.optString("model")+":"+item.optString("csc")==key) return item }
        return null
    }
    fun feed(value: JSONObject): FirmwareCore.Feed {
        val previous=value.optJSONArray("previous") ?: JSONArray()
        return FirmwareCore.Feed(value.optString("latest"),value.optString("android"),(0 until previous.length()).map { previous.optString(it) })
    }
    fun official(snapshot: JSONObject): FirmwareCore.Feed {
        val source=feed(snapshot.getJSONObject("official"))
        val known=source.previous.toMutableList()
        val notes=snapshot.optJSONObject("notes")?.optJSONArray("releases") ?: JSONArray()
        for(i in 0 until notes.length()) notes.optJSONObject(i)?.optString("build")?.takeIf { it.isNotEmpty() }?.let { known.add(it) }
        return FirmwareCore.Feed(source.latest,source.androidVersion,known)
    }
    fun matches(snapshot: JSONObject): Map<String,String> {
        val values=snapshot.optJSONObject("localMatches") ?: JSONObject()
        return values.keys().asSequence().associateWith { values.optString(it) }
    }
    fun choice(snapshot: JSONObject) = FirmwareCore.selectTest(feed(snapshot.getJSONObject("test")),official(snapshot),matches(snapshot))
    fun summary(snapshot: JSONObject) = FirmwareCore.summarizeTest(feed(snapshot.getJSONObject("test")),official(snapshot),matches(snapshot))
    fun olderTestReason(snapshot: JSONObject, build: String): String {
        if(build.isEmpty() || snapshot.optJSONObject("official")?.optString("status")!="OK")return ""
        val released=snapshot.getJSONObject("official").optString("latest")
        val notes=snapshot.optJSONObject("notes")?.optJSONArray("releases") ?: JSONArray()
        fun published(value: String): java.time.LocalDate? {
            for(i in 0 until notes.length()) {
                val note=notes.optJSONObject(i) ?: continue
                if(note.optString("build")==FirmwareCore.ap(value))return runCatching { java.time.LocalDate.parse(note.optString("date")) }.getOrNull()
            }
            return null
        }
        val testDate=published(build);val releasedDate=published(released)
        if(testDate!=null && releasedDate!=null)return if(testDate.isBefore(releasedDate)) "Published $testDate; latest available published $releasedDate." else ""
        return FirmwareCore.olderBuildReason(build,released)
    }
    private fun encode(result: FirmwareCore.Result): JSONObject {
        val value=JSONObject().put("status",result.status.name).put("message",result.message).put("source",result.source).put("httpCode",result.httpCode)
        result.feed?.let { value.put("latest",it.latest).put("android",it.androidVersion).put("previous",JSONArray(it.previous)) }
        return value
    }
    fun fetch(device: FirmwareCore.Device, progress: (String)->Unit = {}): JSONObject {
        val pool=Executors.newFixedThreadPool(3)
        try {
            progress("Checking Samsung's test list…")
            val test=pool.submit<FirmwareCore.Result> { FirmwareCore.fetch(device,true) }
            val released=pool.submit<FirmwareCore.Result> { FirmwareCore.fetch(device,false) }
            val notes=pool.submit<SamsungInfo.Document> { SamsungInfo.fetch(device) }
            val snapshot=JSONObject().put("appVersion",FirmwareCore.CLIENT_VERSION).put("matchingMethods",JSONArray(listOf("MD5","HMAC-SHA256")))
                .put("model",device.model).put("csc",device.csc).put("time",System.currentTimeMillis())
                .put("test",encode(test.get())).put("official",encode(released.get()))
            progress("Reading release notes…")
            val doc=notes.get()
            val releases=JSONArray()
            doc.releases.forEach { releases.put(JSONObject().put("build",it.build).put("android",it.android).put("date",it.date).put("patch",it.patch).put("notes",it.notes)) }
            snapshot.put("notes",JSONObject().put("name",doc.name).put("source",doc.source).put("message",doc.message).put("releases",releases))
            // Documentation is auxiliary: never overwrite the feed's explicit latest field.
            if(snapshot.getJSONObject("test").optString("status")=="OK") {
                snapshot.put("localMatches",json("recovered:${device.key()}"))
                if(snapshot.getJSONObject("official").optString("status")=="OK") {
                    progress("Looking for test build names on your phone…")
                    enrich(snapshot,false)
                }
            }
            return snapshot
        } finally { pool.shutdownNow() }
    }
    fun enrich(snapshot: JSONObject, thorough: Boolean, progress: (Int,Int)->Unit = {_,_->}) {
        val key=snapshot.getString("model")+":"+snapshot.getString("csc")
        val recovered=synchronized(lock) { json("recovered:$key") }
        val test=feed(snapshot.getJSONObject("test"))
        val seed=snapshot.getJSONObject("official").optString("latest")
        val known=feed(snapshot.getJSONObject("official")).previous+recovered.keys().asSequence().map { recovered.optString(it) }.toList()
        val seeds=(listOf(seed)+test.previous.filter { !FirmwareCore.isHash(it) }+known).distinct()
        FirmwareCore.matchKnown(test,seeds).forEach { (hash,build)->recovered.put(hash,build) }
        // A supplied latest hash remains the only discovery target. Direct known-name
        // comparisons still identify released duplicates elsewhere in the complete list.
        val targets=when {
            FirmwareCore.isHash(test.latest)->listOf(test.latest)
            test.latest.isEmpty()->test.previous.filter { FirmwareCore.isHash(it) }
            else->emptyList()
        }.filter { !FirmwareCore.matchesCandidate(it,recovered.optString(it.lowercase())) }
        val pending=FirmwareCore.Feed("","",targets)
        val existing=(test.previous+test.latest).distinct().count { FirmwareCore.matchesCandidate(it,recovered.optString(it.lowercase())) }
        val discovery=FirmwareCore.discoverExpanded(pending,seeds,if(thorough)1500000 else 250000) { tried,found -> progress(tried,existing+found) }
        discovery.matches.forEach { (hash,build)->recovered.put(hash,build) }
        synchronized(lock) {
            val current=json("recovered:$key")
            recovered.keys().forEach { current.put(it,recovered.getString(it)) }
            prefs.edit().putString("recovered:$key",current.toString()).apply()
            snapshot.put("localMatches",current).put("searchAttempts",discovery.tried).put("searchLimitReached",discovery.limitReached)
                .put("matchingMethods",JSONArray(listOf("MD5","HMAC-SHA256"))).put("appVersion",FirmwareCore.CLIENT_VERSION)
        }
    }
    fun remember(snapshot: JSONObject): Boolean = synchronized(lock) {
        val key=snapshot.getString("model")+":"+snapshot.getString("csc")
        val baseline=json("snapshot:$key")
        val events=array("events")
        val additions=JSONArray()
        val seen=json("seen:$key")
        var changed=false
        for(name in listOf("test","official")) {
            val fresh=snapshot.getJSONObject(name)
            if(fresh.optString("status")!="OK") continue
            val before=baseline.optJSONObject(name)
            if(before!=null) {
                val diff=FeedChanges(feed(before),feed(fresh))
                if(diff.changed()) {
                    changed=true
                    additions.put(JSONObject().put("model",snapshot.getString("model")).put("csc",snapshot.getString("csc"))
                        .put("time",snapshot.getLong("time")).put("kind",name).put("added",JSONArray(diff.added)).put("removed",JSONArray(diff.removed))
                        .put("latestChanged",diff.latestChanged).put("androidChanged",diff.androidChanged).put("latest",fresh.optString("latest")))
                }
            } else additions.put(JSONObject().put("model",snapshot.getString("model")).put("csc",snapshot.getString("csc"))
                .put("time",snapshot.getLong("time")).put("kind","first:$name").put("added",JSONArray()).put("removed",JSONArray()))
            baseline.put(name,fresh)
            if(name=="test") (feed(fresh).previous+feed(fresh).latest).filter { it.isNotEmpty() }.forEach { if(!seen.has(it)) seen.put(it,snapshot.getLong("time")) }
        }
        snapshot.put("changes",additions).put("firstSeen",seen)
        val newEvents=JSONArray()
        for(i in 0 until additions.length()) newEvents.put(additions.getJSONObject(i))
        for(i in 0 until minOf(events.length(),400-newEvents.length())) newEvents.put(events.getJSONObject(i))
        val history=array("history");val trimmed=JSONArray().put(snapshot)
        for(i in 0 until minOf(history.length(),99)) trimmed.put(history.getJSONObject(i))
        prefs.edit().putString("snapshot:$key",baseline.toString()).putString("seen:$key",seen.toString()).putString("events",newEvents.toString())
            .putString("result:$key",snapshot.toString()).putString("history",trimmed.toString()).apply()
        changed
    }
    fun retainMatches(snapshot: JSONObject) = synchronized(lock) {
        val key=snapshot.getString("model")+":"+snapshot.getString("csc")
        val history=array("history")
        for(i in 0 until history.length()) {
            val item=history.getJSONObject(i)
            if(item.optString("model")==snapshot.getString("model") && item.optString("csc")==snapshot.getString("csc") && item.optLong("time")==snapshot.getLong("time")) { history.put(i,snapshot);break }
        }
        val edit=prefs.edit().putString("history",history.toString())
        if(snapshot.optLong("time")>=json("result:$key").optLong("time")) edit.putString("result:$key",snapshot.toString())
        edit.apply()
    }
    fun verify(snapshot: JSONObject, candidate: String): Boolean = synchronized(lock) {
        val full=candidate.trim().uppercase(java.util.Locale.ROOT)
        val parts=full.split("/")
        if(parts.size!=3 || FirmwareCore.buildSuffix(parts[0]).isEmpty() || !parts[1].matches(Regex("[A-Z0-9]{8,}")) ||
            (parts[2].isNotEmpty() && FirmwareCore.buildSuffix(parts[2]).isEmpty())) return false
        val test=feed(snapshot.getJSONObject("test"))
        val hits=FirmwareCore.matchKnown(test,listOf(full))
        if(hits.isEmpty()) return false
        val key=snapshot.getString("model")+":"+snapshot.getString("csc")
        val recovered=json("recovered:$key")
        hits.forEach { (hash,build)->recovered.put(hash,build) }
        prefs.edit().putString("recovered:$key",recovered.toString()).apply()
        snapshot.put("localMatches",recovered).put("appVersion",FirmwareCore.CLIENT_VERSION)
            .put("matchingMethods",JSONArray(listOf("MD5","HMAC-SHA256")));retainMatches(snapshot);true
    }
    fun exportBookmarks(): String = synchronized(lock) {
        val values=JSONArray();devices().forEach { key->val d=device(key);values.put(JSONObject().put("model",d.model).put("csc",d.csc).put("watch",watched().contains(key))) }
        JSONObject().put("app","FirmPulse").put("format",1).put("devices",values).toString(2)
    }
    fun importBookmarks(value: String): Int = synchronized(lock) {
        require(value.length<=1048576) { "The backup is too large." }
        val backup=JSONObject(value);require(backup.optString("app")=="FirmPulse" && backup.optInt("format")==1) { "Select a FirmPulse bookmark backup." }
        val values=backup.getJSONArray("devices");require(values.length()<=100) { "Import up to 100 devices at once." }
        val validated=(0 until values.length()).map { i -> val item=values.getJSONObject(i); FirmwareCore.Device(item.getString("model"),item.getString("csc")) to item.optBoolean("watch") }
        val saved=devices().toMutableSet();val watch=watched().toMutableSet()
        validated.forEach { (d,w)->saved.add(d.key());if(w) watch.add(d.key()) }
        prefs.edit().putStringSet("devices",saved).putStringSet("watched",watch).apply();validated.size
    }
    fun clearHistory() = synchronized(lock) {
        val edit=prefs.edit().remove("history").remove("events")
        prefs.all.keys.filter { it.startsWith("snapshot:")||it.startsWith("seen:")||it.startsWith("result:") }.forEach { edit.remove(it) }
        edit.apply()
    }
    fun clearMatches() = synchronized(lock) {
        val edit=prefs.edit()
        prefs.all.keys.filter { it.startsWith("recovered:") }.forEach { edit.remove(it) }
        prefs.all.keys.filter { it.startsWith("result:") }.forEach { key -> val snapshot=json(key);snapshot.remove("localMatches");edit.putString(key,snapshot.toString()) }
        val history=array("history")
        for(i in 0 until history.length()) history.getJSONObject(i).remove("localMatches")
        edit.putString("history",history.toString()).apply()
    }
    fun support(name: String): Pair<String,Long> {
        var html=prefs.getString("supportHtml","").orEmpty();var time=prefs.getLong("supportTime",0)
        if(html.isEmpty() || System.currentTimeMillis()-time>86400000) {
            html=SamsungInfo.read("https://security.samsungmobile.com/workScope.smsb","security.samsungmobile.com")
            time=System.currentTimeMillis();prefs.edit().putString("supportHtml",html).putLong("supportTime",time).apply()
        }
        return SamsungInfo.cadence(html,name) to time
    }
}
