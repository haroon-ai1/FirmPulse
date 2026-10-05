package io.github.haroonjadoon.firmscope

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.net.Uri
import android.os.*
import android.view.*
import android.widget.*
import io.github.haroonjadoon.firmscope.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.Future

class MainActivity : Activity() {
    private lateinit var store: FirmwareStore
    private lateinit var ui: PulseUi
    private lateinit var root: FrameLayout
    private lateinit var content: LinearLayout
    private lateinit var scrolling: ScrollView
    private var renderedTab=-1
    private lateinit var navigation: LinearLayout
    private lateinit var dock: PulseGlassDock
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private val worker=Executors.newSingleThreadExecutor()
    private var job: Future<*>?=null
    private var busy=false
    private var resumed=false
    private var visibleDialog: AlertDialog?=null
    private val starHandler=Handler(Looper.getMainLooper())
    private val starPrompt=Runnable { showStarPromptIfReady() }
    private var tab=0
    private var activityMode=0
    private var toolsFilter=0
    private var toolsLimit=30
    private var hiddenExpanded=false
    private var query=""
    private var selected: JSONObject?=null
    private var modelCode=""
    private var regionCode=""
    private val tabNames=listOf("Home","Devices","Activity","Tools","Settings")
    private val cscSuggestions=listOf("XAA · United States","ATT · AT&T","TMB · T-Mobile","VZW · Verizon","EUX · Europe","INS · India","PAK · Pakistan","KOO · South Korea","XAC · Canada","BTU · United Kingdom","XSG · United Arab Emirates","XSA · Australia","THL · Thailand","XID · Indonesia","XXV · Vietnam","ZTO · Brazil")
    private val modelSuggestions=listOf("SM-S926B","SM-S926U","SM-S926U1","SM-S926N","SM-S928B","SM-S928U","SM-S928U1","SM-S928N","SM-S921B","SM-S921U","SM-S921U1","SM-S938B","SM-S938U","SM-S938U1","SM-S938N","SM-S936B","SM-S931B","SM-S918B","SM-S918U","SM-S918U1","SM-S916B","SM-S911B","SM-A556B","SM-A546B","SM-A356B")

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if(Build.VERSION.SDK_INT>=30)window.setDecorFitsSystemWindows(false)
        store=FirmwareStore(this)
        modelCode=state?.getString("model") ?: store.prefs.getString("lastModel",if(Build.MANUFACTURER.equals("samsung",true)) FirmwareCore.normalizeModel(Build.MODEL).takeIf { it.matches(Regex("[A-Z]{2,3}-[A-Z0-9]{3,12}")) }.orEmpty() else "").orEmpty()
        regionCode=state?.getString("csc") ?: store.prefs.getString("lastCsc","").orEmpty()
        tab=state?.getInt("tab") ?: 0
        selected=state?.getString("selected")?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: store.result("$modelCode:$regionCode") ?: store.last()?.takeIf { it.optString("model")==modelCode && it.optString("csc")==regionCode }
        intent.getStringExtra("device")?.let { selectKey(it) }
        buildShell()
        WatchJob.schedule(this)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent);setIntent(intent);intent.getStringExtra("device")?.let { selectKey(it);tab=0;render() } }
    private fun selectKey(key: String) { runCatching { store.device(key) }.getOrNull()?.let { modelCode=it.model;regionCode=it.csc;selected=store.result(key);saveSelection() } }
    private fun saveSelection() { store.prefs.edit().putString("lastModel",modelCode).putString("lastCsc",regionCode).apply() }
    private fun buildShell() {
        val theme=store.prefs.getString("theme","system")
        val dark=theme=="dark" || (theme=="system" && resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES)
        ui=PulseUi(this,dark)
        if(Build.VERSION.SDK_INT<30) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility=if(dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        @Suppress("DEPRECATION")
        window.statusBarColor=ui.background
        @Suppress("DEPRECATION")
        window.navigationBarColor=ui.background
        root=FrameLayout(this).apply { background=android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            intArrayOf(ui.background,Color.parseColor(if(dark) "#101C2D" else "#E7EDF7"))) }
        val shell=ui.col();root.addView(shell,FrameLayout.LayoutParams(-1,-1))
        val header=ui.row().apply { setPadding(ui.dp(20),ui.dp(12),ui.dp(20),ui.dp(8)) }
        header.addView(PulseIcon(this,"pulse",ui.blue),LinearLayout.LayoutParams(ui.dp(28),ui.dp(28)).apply { rightMargin=ui.dp(10) })
        header.addView(ui.label("FirmPulse",22,ui.ink,true),ui.weight())
        header.addView(ui.icon("bell","Notification settings") { tab=4;render() },LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)))
        shell.addView(header)
        val message=ui.row().apply { setPadding(ui.dp(20),0,ui.dp(20),ui.dp(5)) }
        progress=ProgressBar(this).apply { visibility=View.GONE }
        message.addView(progress,LinearLayout.LayoutParams(ui.dp(18),ui.dp(18)).apply { rightMargin=ui.dp(8) })
        status=ui.label("Test builds, at a glance",11,ui.muted);message.addView(status,ui.weight());shell.addView(message)
        val scroll=ScrollView(this).apply { isFillViewport=true;isVerticalScrollBarEnabled=false }
        scrolling=scroll;renderedTab=-1
        content=ui.col().apply { setPadding(ui.dp(18),ui.dp(10),ui.dp(18),ui.dp(112)) }
        scroll.addView(content);shell.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        dock=PulseGlassDock(this,ui).apply { glass=store.prefs.getBoolean("glass",true) }
        navigation=ui.row().apply { setPadding(ui.dp(5),ui.dp(5),ui.dp(5),ui.dp(5)) }
        dock.addView(navigation,FrameLayout.LayoutParams(-1,-2))
        root.addView(dock,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM).apply { leftMargin=ui.dp(14);rightMargin=ui.dp(14);bottomMargin=ui.dp(12) })
        tabNames.forEachIndexed { index,name ->
            val item=ui.col().apply {
                gravity=Gravity.CENTER;minimumHeight=ui.dp(58);setPadding(ui.dp(2),ui.dp(7),ui.dp(2),ui.dp(7))
                contentDescription=name;isClickable=true;isFocusable=true
                background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(30,160,195,255)),null,ui.shape(radius=25))
                setOnClickListener { if(tab!=index) { tab=index;query="";toolsLimit=30;hiddenExpanded=false;render() } }
                PulseMotion.press(this)
            }
            item.addView(PulseIcon(this,name.lowercase(),ui.muted),LinearLayout.LayoutParams(ui.dp(21),ui.dp(21)))
            item.addView(ui.label(name,10,ui.muted),LinearLayout.LayoutParams(-2,-2).apply { topMargin=ui.dp(4) })
            navigation.addView(item,ui.weight())
        }
        scroll.setOnScrollChangeListener { _,_,_,_,_->dock.refreshBackdrop(scrolling) }
        root.setOnApplyWindowInsetsListener { _,insets ->
            if(Build.VERSION.SDK_INT>=30) {
                val bars=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime() or WindowInsets.Type.mandatorySystemGestures())
                root.setPadding(bars.left,bars.top,bars.right,bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                root.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
            }
            if(Build.VERSION.SDK_INT>=30)WindowInsets.CONSUMED else insets
        }
        setContentView(root)
        if(Build.VERSION.SDK_INT>=30)window.decorView.post {
            val appearance=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if(dark)0 else appearance,appearance)
        }
        root.requestApplyInsets();render()
    }
    private fun render() {
        val savedScroll=scrolling.scrollY
        val previousTab=renderedTab;val changedTab=previousTab!=tab;renderedTab=tab
        content.animate().cancel();content.alpha=1f;content.translationX=0f
        content.removeAllViews()
        tabNames.forEachIndexed { index,name ->
            val item=navigation.getChildAt(index) as LinearLayout
            item.isSelected=index==tab
            if(Build.VERSION.SDK_INT>=30)item.stateDescription=if(index==tab) "Selected" else null
            val tint=if(index==tab) ui.blueInk else ui.muted
            (item.getChildAt(0) as PulseIcon).setTint(tint)
            (item.getChildAt(1) as TextView).setTextColor(tint)
        }
        dock.select(tab,changedTab&&previousTab>=0)
        when(tab) { 0->home();1->devices();2->activity();3->tools();else->settings() }
        progress.visibility=if(busy) View.VISIBLE else View.GONE
        if(!busy)updateStatus()
        if(changedTab) {
            scrolling.scrollTo(0,0)
            if(previousTab>=0)PulseMotion.enter(content,if(tab>previousTab)1 else -1)
        }
        scrolling.post { scrolling.scrollTo(0,if(changedTab)0 else savedScroll) }
        dock.refreshBackdrop(scrolling)
    }
    private fun updateStatus() {
        val snapshot=selected
        status.text=if(snapshot==null) "Test builds, at a glance" else {
            val ok=listOf("test","official").count { snapshot.optJSONObject(it)?.optString("status")=="OK" }
            "Updated ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(snapshot.optLong("time")))} · $ok/2 sources"
        }
    }
    private fun selector(parent: LinearLayout=content) {
        val card=ui.card(parent)
        val row=ui.row()
        val texts=ui.col()
        val name=selected?.optJSONObject("notes")?.optString("name").orEmpty()
        texts.addView(ui.label(name.ifEmpty { modelCode.ifEmpty { "Choose your Samsung phone" } },16,ui.ink,true))
        texts.addView(ui.label(if(modelCode.isEmpty()||regionCode.isEmpty()) "Enter a model and region code" else "$modelCode · $regionCode",12,ui.muted),ui.margin(5))
        row.addView(texts,ui.weight());row.addView(ui.icon("edit","Change model and region") { searchDialog() },LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)))
        card.addView(row)
        if(modelCode.startsWith("SM-S")) {
            val notice=ui.card(parent,"notice")
            notice.addView(ui.label("S-series support coming soon",13,ui.warning,true))
            notice.addView(ui.label("Please wait. Test and beta results may not be fully accurate yet.",12,ui.muted),ui.margin(5))
        }
    }
    private fun home() {
        selector()
        val snapshot=selected
        if(snapshot==null) {
            val card=ui.card(content,"blue")
            card.addView(ui.pill("TEST BUILDS"));card.addView(ui.label("Follow what Samsung is testing",22,ui.ink,true),ui.margin(10,8))
            card.addView(ui.label("Choose your model and region to begin.",13,ui.muted))
            card.addView(ui.action("Choose a phone",true) { searchDialog() },ui.margin(14))
        } else {
            testCard(snapshot)
            content.addView(ui.action("Check again",true) { checkCurrent() },ui.margin(0,12))
            releasedCard(snapshot)
            installedCard(snapshot)
            val change=snapshot.optJSONArray("changes") ?: JSONArray()
            if(change.length()>0) {
                val card=ui.card(content);card.addView(ui.label("Recent changes",14,ui.ink,true))
                card.addView(ui.label(eventTitle(change.getJSONObject(0)),13,ui.muted),ui.margin(7))
                ui.setting(card,"View activity") { tab=2;activityMode=0;render() }
            }
        }
        if(snapshot!=null) {
            val r=ui.row();r.addView(ui.action("Change phone") { searchDialog() },ui.weight());r.addView(ui.action("Save device") { saveCurrent() },ui.weight().apply { leftMargin=ui.dp(8) });content.addView(r)
        }
    }
    private fun testCard(snapshot: JSONObject) {
        val data=snapshot.getJSONObject("test")
        val card=ui.card(content,"blue")
        val heading=ui.row();heading.addView(ui.pill("TEST BUILDS"));heading.addView(Space(this),ui.weight())
        heading.addView(ui.icon("share","Share test build card") { shareCard(store.choice(snapshot).build) },LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)))
        heading.addView(ui.icon("info","About this result") {
            val choice=store.choice(snapshot);val info=store.summary(snapshot)
            textDialog("Test result",choice.explanation+"\n\n${maxOf(0,info.hiddenCount-verifiedCount(snapshot))} names remain hidden. ${info.releasedOverlapCount} identified entries are already released. A test-list entry does not confirm a public beta or rollout.")
        },LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));card.addView(heading)
        if(data.optString("status")!="OK") {
            card.addView(ui.label("Couldn't check test builds",21,ui.ink,true),ui.margin(10,6));card.addView(ui.label(failure(data),13,ui.muted));return
        }
        val choice=store.choice(snapshot);val info=store.summary(snapshot)
        card.addView(ui.label(if(choice.build.isEmpty()) choice.label else FirmwareCore.shortBuild(choice.build).ifEmpty { FirmwareCore.ap(choice.build) },22,ui.ink,true),ui.margin(4,6))
        if(choice.build.isNotEmpty())card.addView(ui.label(choice.label,13,ui.blueInk,true),ui.margin(0,6))
        olderBuildNotice(card,snapshot,choice.build)
        card.addView(ui.label(if(choice.build.isEmpty()) "Latest test name unavailable" else if(choice.published) "Listed as latest by Samsung" else "Latest status unconfirmed",12,ui.muted))
        val android=data.optString("android")
        if(android.isNotEmpty())card.addView(ui.label("Android $android · listed by Samsung",13,ui.muted),ui.margin(7))
        val count=(store.feed(data).previous+store.feed(data).latest).filter { it.isNotEmpty() }.distinct().size
        val matched=verifiedCount(snapshot)
        val named=(store.feed(data).previous+store.feed(data).latest).filter { it.isNotEmpty() }.distinct().count { !FirmwareCore.isHash(it) }+matched
        card.addView(ui.label("$count ${if(count==1) "entry" else "entries"} · ${info.releasedOverlapCount} released · ${maxOf(0,named-info.releasedOverlapCount)} named",11,ui.muted),ui.margin(8,2))
        ui.setting(card,if(choice.build.isEmpty()) "Explore test entries" else "View build details") {
            if(choice.build.isEmpty()) { tab=3;render() } else buildDetails(choice.build,snapshot,true)
        }
        if(info.betaClue.isNotEmpty() && info.betaClue!=choice.build) {
            val beta=ui.card(content,"blue")
            val betaHeading=ui.row();betaHeading.addView(ui.pill("BETA-STYLE BUILD"));betaHeading.addView(Space(this),ui.weight())
            betaHeading.addView(ui.icon("share","Share beta build card") { shareCard(info.betaClue) },LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));beta.addView(betaHeading)
            beta.addView(ui.label(FirmwareCore.shortBuild(info.betaClue),20,ui.ink,true),ui.margin(8,4))
            olderBuildNotice(beta,snapshot,info.betaClue)
            beta.addView(ui.label("Public beta availability unconfirmed",12,ui.muted));ui.setting(beta,"View beta build") { buildDetails(info.betaClue,snapshot,true) }
        }
    }
    private fun olderBuildNotice(parent: LinearLayout,snapshot: JSONObject,build: String) {
        val reason=store.olderTestReason(snapshot,build)
        if(reason.isEmpty())return
        parent.addView(ui.label("Older build found",13,ui.warning,true),ui.margin(9,4))
        parent.addView(ui.label("This appears older than the latest available build. A newer test build hasn't been identified.",12,ui.muted))
        ui.setting(parent,"Why this looks older") { textDialog("Older build",reason+"\n\nA build-code month is a clue, not a release date. A newer hidden test build may exist.") }
    }
    private fun releasedCard(snapshot: JSONObject) {
        val data=snapshot.getJSONObject("official");val value=data.optString("latest")
        val card=ui.card(content,"mint")
        val heading=ui.row();heading.addView(ui.pill("LATEST AVAILABLE",true));heading.addView(Space(this),ui.weight())
        heading.addView(ui.icon("share","Share latest available card") { shareCard() },LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));card.addView(heading)
        if(data.optString("status")!="OK") { card.addView(ui.label("Couldn't check released firmware",17,ui.ink,true),ui.margin(8,4));card.addView(ui.label(failure(data),12,ui.muted));return }
        val row=ui.row();row.addView(ui.label(if(value.isEmpty()) "No latest build listed" else if(FirmwareCore.isHash(value)) "Build name hidden" else FirmwareCore.shortBuild(value).ifEmpty { FirmwareCore.ap(value) },21,ui.ink,true),ui.weight())
        if(data.optString("android").isNotEmpty())row.addView(ui.label("Android ${data.optString("android")}",13,ui.muted))
        card.addView(row,ui.margin(8,4))
        bestNote(snapshot)?.let { note ->
            val fields=listOfNotNull(note.optString("date").takeIf { it.isNotEmpty() }?.let { "Published $it" },note.optString("patch").takeIf { it.isNotEmpty() }?.let { "Security patch $it" })
            if(fields.isNotEmpty())card.addView(ui.label(fields.joinToString("\n"),12,ui.muted),ui.margin(6))
        }
        ui.setting(card,"Release notes & full build") { buildDetails(value,snapshot,false) }
    }
    private fun installedCard(snapshot: JSONObject) {
        if(!Build.MANUFACTURER.equals("samsung",true) || FirmwareCore.normalizeModel(Build.MODEL)!=snapshot.getString("model")) return
        val card=ui.card(content)
        card.addView(ui.label("On this phone",13,ui.ink,true));val released=snapshot.getJSONObject("official").optString("latest")
        val installed=Build.DISPLAY
        card.addView(ui.label(installed,12,ui.muted).apply { setTextIsSelectable(true) },ui.margin(6))
        ui.line(card,"Android",Build.VERSION.RELEASE)
        ui.line(card,"Security patch",Build.VERSION.SECURITY_PATCH.ifEmpty { "Not reported" })
        if(released.isNotEmpty() && !FirmwareCore.isHash(released)) card.addView(ui.label(if(installed.contains(FirmwareCore.ap(released))) "Matches the released build" else "Differs from the listed released build",12,ui.blueInk),ui.margin(6))
        ui.setting(card,"More phone information") {
            textDialog("This phone","Model: ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE}\nBuild: ${Build.DISPLAY}\nSecurity patch: ${Build.VERSION.SECURITY_PATCH}\nRadio software: ${runCatching { Build.getRadioVersion() }.getOrNull().orEmpty().ifEmpty { "Not reported" }}\n\nAn available build does not guarantee an OTA update for this phone.")
        }
    }
    private fun bestNote(snapshot: JSONObject): JSONObject? {
        val build=FirmwareCore.ap(snapshot.getJSONObject("official").optString("latest"))
        val notes=snapshot.optJSONObject("notes")?.optJSONArray("releases") ?: return null
        for(i in 0 until notes.length()) { val note=notes.getJSONObject(i);if(note.optString("build")==build)return note }
        return null
    }
    private fun failure(data: JSONObject)=when(data.optString("status")) {
        "NOT_FOUND"->"No list was found for this model and region. Check both codes."
        "HTTP_ERROR"->"Samsung returned a server error (${data.optInt("httpCode")}). Try again or use another network."
        "PARSE_ERROR"->"Samsung's response couldn't be read. Try again or share the report for troubleshooting."
        else->"Couldn't reach Samsung. Check your connection and try again."
    }
    private fun devices() {
        ui.section(content,"Your devices")
        content.addView(ui.action("Add a device",true) { searchDialog(true) },ui.margin(0,12))
        val saved=store.devices()
        if(saved.isEmpty()) content.addView(ui.label("No saved devices yet. Add your phone using its model and region code.",14,ui.muted))
        saved.forEach { key ->
            val d=store.device(key);val result=store.result(key);val card=ui.card(content)
            val row=ui.row();val title=ui.col();title.addView(ui.label(result?.optJSONObject("notes")?.optString("name").orEmpty().ifEmpty { d.model },16,ui.ink,true));title.addView(ui.label("${d.model} · ${d.csc}",12,ui.muted),ui.margin(4));row.addView(title,ui.weight())
            val watch=Switch(this).apply { contentDescription="Watch ${d.model} ${d.csc}";text="Watch";textSize=11f;setTextColor(ui.muted);isChecked=store.watched().contains(key);ui.tintSwitch(this);setOnCheckedChangeListener { _,checked -> store.saveDevice(d,checked);WatchJob.schedule(this@MainActivity) } };row.addView(watch);card.addView(row)
            if(result!=null) {
                val badges=ui.row();val released=result.getJSONObject("official").optString("latest")
                if(released.isNotEmpty()&&!FirmwareCore.isHash(released))badges.addView(ui.pill(FirmwareCore.shortBuild(released).ifEmpty { "Released" },true))
                val test=if(result.getJSONObject("test").optString("status")=="OK") store.choice(result).build else ""
                badges.addView(ui.pill(if(test.isEmpty()) "Test unknown" else "Test: ${FirmwareCore.shortBuild(test)}"),LinearLayout.LayoutParams(-2,-2).apply { leftMargin=ui.dp(7) })
                card.addView(badges,ui.margin(10,6));card.addView(ui.label("Checked ${date(result.optLong("time"))}",11,ui.muted))
            }
            val actions=ui.row();actions.addView(ui.action("Open") { selectKey(key);tab=0;render() },ui.weight());actions.addView(ui.action("Check") { checkDevice(d) },ui.weight().apply { leftMargin=ui.dp(6) });card.addView(actions,ui.margin(9))
            ui.destructive(card,"Remove device") { confirm("Remove ${d.model} / ${d.csc}?","Its saved checks will stay in history.") { store.removeDevice(key);WatchJob.schedule(this);render() } }
        }
        content.addView(ui.action("Check all saved devices",true) { batch(saved) },ui.margin(2,9))
        content.addView(ui.action("Compare region codes") { compareDialog() },ui.margin(0,9))
        ui.setting(content,"Watchlist help") { textDialog("Watchlist","The switch selects devices to watch. Enable Watchlist alerts in Settings for notifications.") }
    }
    private fun activity() {
        ui.section(content,"Activity")
        val segments=ui.row()
        listOf("Changes","Releases").forEachIndexed { i,name->segments.addView(ui.action(name,activityMode==i) { activityMode=i;render() },ui.weight().apply { if(i>0)leftMargin=ui.dp(7) }) };content.addView(segments,ui.margin(0,12))
        if(activityMode==1) { releaseHistory();return }
        val events=store.array("events")
        if(events.length()==0)content.addView(ui.label("Check a phone to start its timeline. Dates here show when this app observed a change.",14,ui.muted))
        for(i in 0 until minOf(events.length(),50)) {
            val event=events.getJSONObject(i);val card=ui.card(content,if(event.optString("kind").contains("official")) "mint" else "blue")
            card.addView(ui.label(eventTitle(event),15,ui.ink,true));card.addView(ui.label("${event.optString("model")} · ${event.optString("csc")}\n${date(event.optLong("time"))}",12,ui.muted),ui.margin(6))
            val a=event.optJSONArray("added")?:JSONArray();val r=event.optJSONArray("removed")?:JSONArray()
            if(a.length()+r.length()>0)card.addView(ui.label("${a.length()} entries added · ${r.length()} removed",12,ui.muted),ui.margin(6))
            ui.setting(card,"View change") { textDialog("Firmware list change",eventText(event)) }
        }
        val history=store.array("history")
        content.addView(ui.action("Open saved checks (${history.length()})") {
            if(history.length()==0)toast("No saved checks yet") else {
                val labels=(0 until history.length()).map { val h=history.getJSONObject(it);"${h.optString("model")} / ${h.optString("csc")}\n${date(h.optLong("time"))}" }
                choose("Saved checks",labels) { i->selected=history.getJSONObject(i);modelCode=selected!!.getString("model");regionCode=selected!!.getString("csc");tab=0;render();status.text="Viewing a saved check. Refresh for a current result." }
            }
        },ui.margin(3))
    }
    private fun eventTitle(event: JSONObject): String {
        val kind=event.optString("kind")
        if(kind.startsWith("first:"))return if(kind.endsWith("test")) "First test list saved" else "First released list saved"
        return if(kind=="test") "Samsung's test list changed" else "Released firmware list changed"
    }
    private fun eventText(event: JSONObject)=buildString {
        append("${event.optString("model")} / ${event.optString("csc")}\nObserved ${date(event.optLong("time"))}\n\n${eventTitle(event)}\n")
        for(kind in listOf("added","removed")) { val values=event.optJSONArray(kind)?:JSONArray();append("\n${kind.replaceFirstChar { it.uppercase() }} (${values.length()}):\n");for(i in 0 until values.length())append("${values.getString(i)}\n") }
        append("\nAn entry appearing in a test list does not by itself identify a new build or confirm a rollout.")
    }
    private fun releaseHistory() {
        selector()
        val snapshot=selected ?: return
        val metadata=snapshot.optJSONObject("notes")?:JSONObject();val notes=metadata.optJSONArray("releases")?:JSONArray()
        if(notes.length()==0) {
            content.addView(ui.label("Release notes unavailable",12,ui.muted),ui.margin(0,10))
            val feed=store.feed(snapshot.getJSONObject("official"))
            (listOf(feed.latest)+feed.previous).filter { it.isNotEmpty()&&!FirmwareCore.isHash(it) }.distinct().take(30).forEach { build->
                val card=ui.card(content,"mint");card.addView(ui.pill("RELEASED",true))
                card.addView(ui.label(FirmwareCore.shortBuild(build).ifEmpty { FirmwareCore.ap(build) },20,ui.ink,true),ui.margin(8,4))
                ui.setting(card,"View build") { buildDetails(build,snapshot,false) }
            }
        }
        for(i in 0 until notes.length()) {
            val note=notes.getJSONObject(i);val card=ui.card(content,"mint")
            card.addView(ui.pill("RELEASED",true));card.addView(ui.label(FirmwareCore.shortBuild(note.optString("build")).ifEmpty { note.optString("build") },20,ui.ink,true),ui.margin(9,4))
            ui.line(card,"Published",note.optString("date").ifEmpty { "Not listed" });ui.line(card,"Android",note.optString("android").ifEmpty { "Not listed" });ui.line(card,"Security patch",note.optString("patch").ifEmpty { "Not listed" })
            ui.setting(card,"What's changed") { textDialog("Samsung release notes",noteText(note)); }
        }
        content.addView(ui.action("Open Samsung's release notes") { open(metadata.optString("source")) },ui.margin(2,9))
        content.addView(ui.action("Check security update schedule") { support() },ui.margin(0,8))
        val support=store.prefs.getString("cadence:$modelCode","").orEmpty()
        if(support.isNotEmpty())content.addView(ui.label(support,13,ui.muted))
    }
    private fun noteText(note: JSONObject)="${note.optString("build")}\nAndroid: ${note.optString("android").ifEmpty { "Not listed" }}\nPublished: ${note.optString("date").ifEmpty { "Not listed" }}\nSecurity patch: ${note.optString("patch").ifEmpty { "Not listed" }}\n\n${note.optString("notes").ifEmpty { "No description provided." }}"
    private fun support() {
        val name=selected?.optJSONObject("notes")?.optString("name").orEmpty()
        val model=modelCode
        if(name.isEmpty()) { toast("Check this phone's release notes first");return }
        runWork("Checking Samsung's support list…",{
            val (cadence,time)=store.support(name)
            val result=if(cadence.isEmpty()) "No exact match in Samsung's support list. Support status is unconfirmed." else "$name: $cadence security updates. Timing can vary by carrier and region.\nChecked ${date(time)}"
            store.prefs.edit().putString("cadence:$model",result).apply();result
        }) { textDialog("Security update schedule",it as String);render() }
    }
    private fun tools() {
        ui.section(content,"Build explorer")
        selector()
        val snapshot=selected
        if(snapshot==null) { content.addView(ui.action("Check a phone first",true) { searchDialog() });return }
        val data=snapshot.getJSONObject("test")
        if(data.optString("status")!="OK") { content.addView(ui.label(failure(data),14,ui.muted));return }
        val search=entryField("Search a build or identifier",query)
        content.addView(search,ui.margin(0,6))
        content.addView(ui.action("Filter results") { query=search.text.toString().trim();toolsLimit=30;render() },ui.margin(0,10))
        val filters=ui.row();listOf("All","Hidden","Matched").forEachIndexed { i,name->filters.addView(ui.action(name,toolsFilter==i) { query=search.text.toString().trim();toolsFilter=i;toolsLimit=30;render() },ui.weight().apply { if(i>0)leftMargin=ui.dp(5) }) };content.addView(filters,ui.margin(0,12))
        val values=(store.feed(data).latest.let { if(it.isEmpty()) emptyList() else listOf(it) }+store.feed(data).previous).distinct()
        val matches=store.matches(snapshot)
        val filtered=values.filter { value->val match=matches[value.lowercase()].orEmpty(); val verified=FirmwareCore.matchesCandidate(value,match);(query.isEmpty() || value.contains(query,true) || match.contains(query,true)) && (toolsFilter==0 || toolsFilter==1&&FirmwareCore.isHash(value)&&!verified || toolsFilter==2&&verified) }
        content.addView(ui.label("${filtered.size} ${if(filtered.size==1) "entry" else "entries"} · ${verifiedCount(snapshot)} matched",12,ui.muted),ui.margin(0,10))
        content.addView(ui.action("Find test build names locally",true) { findNames() },ui.margin(0,8))
        content.addView(ui.action("Check a known build name") { verifyDialog() },ui.margin(0,12))
        val named=filtered.filter { value->!FirmwareCore.isHash(value)||FirmwareCore.matchesCandidate(value,matches[value.lowercase()].orEmpty()) }
        val hidden=filtered-named.toSet()
        fun entry(value: String) {
            val mapped=matches[value.lowercase()].orEmpty();val matched=FirmwareCore.matchesCandidate(value,mapped);val build=if(matched)mapped else if(!FirmwareCore.isHash(value))value else ""
            val released=build.isNotEmpty()&&FirmwareCore.isReleasedBuild(build,store.official(snapshot))
            val card=ui.card(content,if(released) "mint" else "normal")
            card.addView(ui.pill(when { released->"ALREADY RELEASED";matched->"NAME MATCHED";build.isEmpty()->"NAME HIDDEN";FirmwareCore.isBetaStyle(build)->"BETA-STYLE";else->"VISIBLE BUILD" },released))
            card.addView(ui.label(if(build.isEmpty()) value.take(8)+"…"+value.takeLast(6) else FirmwareCore.shortBuild(build).ifEmpty { FirmwareCore.ap(build) },19,ui.ink,true),ui.margin(8,4))
            val first=snapshot.optJSONObject("firstSeen")?.optLong(value,0) ?: 0
            if(first>0)card.addView(ui.label("First observed here ${date(first)}",11,ui.muted),ui.margin(7))
            ui.setting(card,"Full entry & details") { if(build.isEmpty())textDialog("Test identifier",value+"\n\nNo build name has been verified. This identifier does not reveal an Android or One UI version.") else buildDetails(build,snapshot,true,value) }
        }
        if(named.isNotEmpty()) {
            content.addView(ui.label("Identified · ${named.size}",13,ui.ink,true),ui.margin(4,10))
            named.take(toolsLimit).forEach { entry(it) }
            if(named.size>toolsLimit)content.addView(ui.action("Show more identified entries") { toolsLimit+=30;render() },ui.margin(0,10))
        }
        if(hidden.isNotEmpty()) {
            val expanded=hiddenExpanded||toolsFilter==1||query.isNotEmpty()
            val summary=ui.card(content);summary.addView(ui.pill("NAME HIDDEN"))
            ui.setting(summary,"${hidden.size} hidden entries",if(expanded) "Hide" else "Show") {
                if(toolsFilter==1||query.isNotEmpty()) { toolsFilter=0;query="" }
                hiddenExpanded=!expanded;toolsLimit=30;render()
            }
            if(expanded) {
                content.addView(ui.label("Showing ${minOf(toolsLimit,hidden.size)} of ${hidden.size}",12,ui.muted),ui.margin(0,10))
                hidden.take(toolsLimit).forEach { entry(it) }
                if(hidden.size>toolsLimit)content.addView(ui.action("Show 30 more") { toolsLimit+=30;render() },ui.margin(0,10))
            }
        }
        if(filtered.isEmpty())content.addView(ui.label("No matching entries",14,ui.muted),ui.margin(2,12))
        content.addView(ui.action("Export JSON or CSV") { exportResults() },ui.margin(0,8))
        content.addView(ui.action("Share a result card") { shareCard() },ui.margin(0,8))
        content.addView(ui.action("Share full report") { shareText(report(snapshot)) },ui.margin(0,8))
        content.addView(ui.action("Open Samsung's test list") { open(data.optString("source")) },ui.margin(0,8))
    }
    private fun verifiedCount(snapshot: JSONObject): Int {
        val test=store.feed(snapshot.getJSONObject("test"));val matches=store.matches(snapshot)
        return (test.previous+test.latest).distinct().count { FirmwareCore.matchesCandidate(it,matches[it.lowercase()].orEmpty()) }
    }
    private fun buildDetails(build: String,snapshot: JSONObject,test: Boolean,identifier: String="") {
        val suffix=FirmwareCore.buildSuffix(build)
        val parts=build.split("/")
        val details=buildString {
            append("${snapshot.getString("model")} / ${snapshot.getString("csc")}\n\n$build\n")
            if(test) {
                store.olderTestReason(snapshot,build).takeIf { it.isNotEmpty() }?.let { append("\nOlder build found. A newer test build hasn't been identified.\n$it\n") }
                if(snapshot.getString("model").startsWith("SM-S"))append("\nS-series support coming soon. Test and beta results may not be fully accurate yet.\n")
            }
            if(test)append("\nA test-list entry does not confirm a public beta or release date.\n")
            if(FirmwareCore.isReleasedBuild(build,store.official(snapshot)))append("\nThis build also appears in released software.\n")
            if(identifier.isNotEmpty())append("\nSamsung identifier: $identifier\n")
            if(suffix.isNotEmpty()) {
                append("\nBuild-code details (advanced)\nBootloader revision: ${suffix[1].digitToInt(36)}\nSoftware branch: ${suffix[2]}\nBuild revision: ${suffix[5]}\nEncoded build month: ${FirmwareCore.codeMonth(build)}\nThis month is part of the name, not its release date.\n")
                val released=FirmwareCore.buildSuffix(snapshot.getJSONObject("official").optString("latest"))
                if(test&&released.isNotEmpty())append("\n${if(released[1]==suffix[1]) "Uses the same bootloader revision as the released build." else "Has a different bootloader revision from the released build."} This does not establish whether downgrading is possible.\n")
            }
            if(parts.size==3)append("\nMain software (AP): ${parts[0]}\nRegion/carrier software (CSC): ${parts[1]}\nModem software (CP): ${parts[2].ifEmpty { "Not provided" }}\n")
            if(!test)bestNote(snapshot)?.let { append("\n${noteText(it)}\n") }
        }
        textDialog(if(test) "Test build details" else "Released build details",details)
    }
    private fun findNames() {
        val snapshot=selected?.let { JSONObject(it.toString()) } ?: return
        if(snapshot.getJSONObject("test").optString("status")!="OK" || snapshot.getJSONObject("official").optString("latest").isEmpty()) { toast("A released build and test list are needed for this search");return }
        val key=snapshot.getString("model")+":"+snapshot.getString("csc")
        runWork("Looking for names on your phone…",{
            store.enrich(snapshot,true) { tried,found->runOnUiThread { if(!isDestroyed)status.text="$tried possibilities checked · $found matches" } }
            if(!Thread.currentThread().isInterrupted)store.retainMatches(snapshot)
            snapshot
        }) { value->if("$modelCode:$regionCode"==key)selected=value as JSONObject;render();textDialog("Local search finished","${snapshot.optInt("searchAttempts")} possibilities checked. ${verifiedCount(snapshot)} entries have matching build names.\n\nSome names may remain hidden. A match proves that a name fits an identifier, not that it is the latest test build.") }
    }
    private fun verifyDialog() {
        val snapshot=selected ?: run { toast("Check a phone first");return }
        if(busy) { toast("Wait for the current check");return }
        val form=ui.col().apply { setPadding(ui.dp(20),ui.dp(10),ui.dp(20),0) }
        form.addView(ui.label("Paste the complete build string, including its three parts separated by /. FirmPulse checks it against the selected test list on this phone.",13,ui.muted))
        val field=entryField("Full build string");form.addView(field,ui.margin(10))
        val dialog=dialogBuilder().setTitle("Check a known build name").setView(form).setPositiveButton("Check",null).setNegativeButton("Cancel",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val guess=field.text.toString().trim();if(guess.isEmpty()||guess.length>300) { field.error="Enter a complete build name";return@setOnClickListener }
            if(store.verify(snapshot,guess)) { selected=snapshot;dialog.dismiss();render();textDialog("Name verified","The full name matches an identifier in this test list. It is saved locally. Latest status and download availability are not confirmed.") }
            else field.error="No identifier in this test list matches that exact name."
        } };dialog.show()
    }
    private fun settings() {
        ui.section(content,"Settings")
        val developer=ui.card(content)
        developer.gravity=Gravity.CENTER_HORIZONTAL
        developer.addView(ui.label("H",26,ui.ink,true).apply {
            gravity=Gravity.CENTER
            background=android.graphics.drawable.GradientDrawable().apply { shape=android.graphics.drawable.GradientDrawable.OVAL;setColor(if(ui.dark)Color.parseColor("#283F55") else Color.parseColor("#DCEAFF")) }
        },LinearLayout.LayoutParams(ui.dp(56),ui.dp(56)).apply { topMargin=ui.dp(4);bottomMargin=ui.dp(10) })
        developer.addView(ui.label("Haroon",20,ui.ink,true).apply { gravity=Gravity.CENTER })
        developer.addView(ui.label("Developer",11,ui.muted),LinearLayout.LayoutParams(-2,-2).apply { topMargin=ui.dp(4);bottomMargin=ui.dp(12) })
        val socials=ui.row()
        listOf("GitHub" to "https://github.com/haroon-ai1","Website" to "https://haroon-ai1.vercel.app/","LinkedIn" to "https://pk.linkedin.com/in/haroon-ai").forEachIndexed { i,(label,url)->
            socials.addView(ui.action(label) { open(url) },ui.weight().apply { if(i>0)leftMargin=ui.dp(6) })
        }
        developer.addView(socials,ui.margin())
        val appearance=ui.card(content);appearance.addView(ui.label("APPEARANCE",11,ui.muted,true))
        switchRow(appearance,"Glass effects",store.prefs.getBoolean("glass",true)) { enabled->store.prefs.edit().putBoolean("glass",enabled).apply();dock.glass=enabled;dock.refreshBackdrop(scrolling) }
        switchRow(appearance,"Reduce motion",store.prefs.getBoolean("reduceMotion",false)) { enabled->store.prefs.edit().putBoolean("reduceMotion",enabled).apply();if(enabled) { content.animate().cancel();content.alpha=1f;content.translationX=0f;dock.select(tab,false) } }
        ui.setting(appearance,"Theme",when(store.prefs.getString("theme","system")) { "dark"->"Dark";"light"->"Light";else->"Use phone setting" }) {
            if(busy) { toast("Wait for this check to finish");return@setting }
            choose("Choose a theme",listOf("Dark","Light","Use phone setting")) { i->store.prefs.edit().putString("theme",listOf("dark","light","system")[i]).apply();buildShell() }
        }
        val alerts=ui.card(content);alerts.addView(ui.label("NOTIFICATIONS",11,ui.muted,true))
        switchRow(alerts,"Watchlist alerts",store.prefs.getBoolean("alerts",false)) { enabled->
            if(enabled&&Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),31)
            else { store.prefs.edit().putBoolean("alerts",enabled).apply();if(!WatchJob.schedule(this))toast("Android couldn't schedule the checks");render() }
        }
        ui.setting(alerts,"Check interval","${store.prefs.getInt("interval",6)} hours") { choose("Check interval",listOf("Every hour","Every 3 hours","Every 6 hours","Every 12 hours","Every 24 hours")) { i->store.prefs.edit().putInt("interval",listOf(1,3,6,12,24)[i]).apply();WatchJob.schedule(this);render() } }
        switchRow(alerts,"Wi-Fi only",store.prefs.getBoolean("wifiOnly",true)) { enabled->store.prefs.edit().putBoolean("wifiOnly",enabled).apply();WatchJob.schedule(this) }
        ui.setting(alerts,"How alerts work") { textDialog("Watchlist alerts","Choose watched phones in Devices. Android may delay background checks to save battery.") }
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)alerts.addView(ui.label("Notification permission is currently off.",12,ui.muted),ui.margin(7))
        ui.setting(alerts,"Android notification settings") { openNotificationSettings() }
        val data=ui.card(content);data.addView(ui.label("YOUR DATA",11,ui.muted,true))
        ui.setting(data,"Export saved devices") { exportBookmarks() }
        ui.setting(data,"Import saved devices") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="application/json";addCategory(Intent.CATEGORY_OPENABLE) },42) }
        ui.setting(data,"Clear history") { confirm("Clear local history?","Saved devices and verified build names will stay. Previous-check comparisons and first-observed dates will be cleared.") { store.clearHistory();selected=null;render() } }
        ui.setting(data,"Clear matched build names") { confirm("Clear matched names?","Firmware lists and saved devices will stay. Hidden names can be searched again.") { store.clearMatches();selected?.remove("localMatches");render() } }
        val about=ui.card(content);about.addView(ui.label("ABOUT",11,ui.muted,true))
        ui.setting(about,"About FirmPulse",FirmwareCore.CLIENT_VERSION) { textDialog("FirmPulse","Samsung test and released firmware, in one place.\n\nDeveloped by Haroon.\nVersion ${FirmwareCore.CLIENT_VERSION}") }
        ui.setting(about,"Privacy & sources") { textDialog("Privacy & sources","Your model and region code are sent to Samsung to retrieve firmware lists and release notes. Samsung sees your IP address.\n\nSaved devices, checks, observed changes and matched names stay on your phone. Optional alerts check your watched devices in the background. Wi-Fi only is on by default.\n\nExporting or sharing sends the chosen file or report to the app you select. Android backup is off. No IMEI, serial number, location, contacts or phone-state access is requested.\n\nSources:\nfota-cloud-dn.ospserver.net\ndoc.samsungmobile.com\nsecurity.samsungmobile.com\n\nTest names can remain hidden. First-observed dates belong to this app; they are not Samsung release dates. Downgrade safety and OTA eligibility are not established by a build code.") }
        about.addView(ui.label("Android 9+",11,ui.muted),ui.margin(8))
        val repo=ui.card(content,"blue")
        repo.addView(ui.label("SUPPORT FIRMPULSE",11,ui.blueInk,true))
        ui.setting(repo,"Star FirmPulse on GitHub") { open("https://github.com/haroon-ai1/FirmPulse") }
    }
    private fun switchRow(parent: LinearLayout,title: String,checked: Boolean,changed: (Boolean)->Unit) {
        val row=ui.row();row.addView(ui.label(title,14),ui.weight());row.addView(Switch(this).apply { contentDescription=title;isChecked=checked;ui.tintSwitch(this);setOnCheckedChangeListener { _,value->changed(value) } });parent.addView(row,ui.margin(4,2))
    }
    private fun entryField(hint: String,value: String="")=EditText(this).apply {
        this.hint=hint;setText(value);setTextColor(ui.ink);setHintTextColor(ui.muted);textSize=14f;minHeight=ui.dp(50);isSingleLine=true;setPadding(ui.dp(12),ui.dp(8),ui.dp(12),ui.dp(8));background=ui.shape(radius=11);inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }
    private fun dropdown(values: List<String>,hint: String,value: String="")=AutoCompleteTextView(this).apply {
        this.hint=hint;setTextColor(ui.ink);setHintTextColor(ui.muted);textSize=14f;minHeight=ui.dp(50);isSingleLine=true;threshold=1
        setPadding(ui.dp(12),ui.dp(8),ui.dp(12),ui.dp(8));background=ui.shape(radius=11);setDropDownBackgroundDrawable(android.graphics.drawable.ColorDrawable(ui.surface))
        val adapter=object : ArrayAdapter<String>(this@MainActivity,android.R.layout.simple_dropdown_item_1line,values) {
            override fun getView(position: Int,convert: View?,parent: ViewGroup): View = (super.getView(position,convert,parent) as TextView).apply { setTextColor(ui.ink);setBackgroundColor(ui.surface);minHeight=ui.dp(48);textSize=14f }
        };setAdapter(adapter);setText(value,false)
        inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }
    private fun showChoices(field: AutoCompleteTextView) {
        (field.adapter as? ArrayAdapter<*>)?.filter?.filter(null) { if(field.isAttachedToWindow)field.showDropDown() }
    }
    private fun searchDialog(save: Boolean=false) {
        if(busy) { toast("Wait for the current check to finish");return }
        val form=ui.col().apply { setPadding(ui.dp(18),ui.dp(10),ui.dp(18),ui.dp(14)) }
        form.addView(ui.label("Phone model",12,ui.ink,true),ui.margin(12,6))
        val suggestions=(store.devices().map { store.device(it).model }+modelSuggestions).distinct()
        val model=dropdown(suggestions,"SM-S926U or S926U",modelCode)
        val modelRow=ui.row();modelRow.addView(model,ui.weight());modelRow.addView(ui.icon("dropdown","Show model choices") { showChoices(model) },LinearLayout.LayoutParams(ui.dp(48),ui.dp(50)).apply { leftMargin=ui.dp(5) });form.addView(modelRow)
        form.addView(ui.action("Choose ending: B, U, U1, N…") {
            val variants=listOf("B","U","U1","N","W","0","E","F","M","C","Q")
            choose("Use your phone's exact ending",variants) { i->try { model.setText(FirmwareCore.withVariant(model.text.toString(),variants[i]),false) } catch(e: IllegalArgumentException) { model.error=e.message } }
        },ui.margin(8))
        form.addView(ui.label("Region or carrier code (CSC)",12,ui.ink,true),ui.margin(12,6))
        val region=dropdown((store.devices().map { store.device(it).csc }+cscSuggestions).distinct(),"XAA, EUX, PAK…",regionCode)
        region.setOnItemClickListener { _,_,_,_->region.setText(region.text.toString().substringBefore(" ·"),false) }
        val regionRow=ui.row();regionRow.addView(region,ui.weight());regionRow.addView(ui.icon("dropdown","Show region choices") { showChoices(region) },LinearLayout.LayoutParams(ui.dp(48),ui.dp(50)).apply { leftMargin=ui.dp(5) });form.addView(regionRow)
        form.addView(ui.action("Where do I find these codes?") { help() },ui.margin(10))
        val scroll=ScrollView(this);scroll.addView(form)
        val dialog=dialogBuilder().setTitle(if(save) "Add a device" else "Choose a phone").setView(scroll).setPositiveButton(if(save) "Save & check" else "Check",null).setNegativeButton("Cancel",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            model.error=null;region.error=null
            val d=try { FirmwareCore.Device(model.text.toString(),region.text.toString().substringBefore(" ·")) } catch(e: IllegalArgumentException) { if(e.message.orEmpty().startsWith("CSC"))region.error=e.message else model.error=e.message;return@setOnClickListener }
            modelCode=d.model;regionCode=d.csc;saveSelection();selected=store.result(d.key());if(save)store.saveDevice(d,true);dialog.dismiss();tab=0;render();checkDevice(d)
        } };dialog.show()
    }
    private fun help()=textDialog("Find your model & region","Model: Settings → About phone → Model number. Keep the full ending, such as B, U, U1 or N. These endings identify different phone variants.\n\nRegion/carrier code (CSC): Settings → About phone → Software information → Service provider software version. Find your active three-character code. If several codes are listed, check which is active for your phone.\n\nYou can type either field manually or use the dropdown. Selecting your country alone may not identify your phone's active CSC. A missing list can mean the model/code combination is incorrect.")
    private fun checkCurrent() { runCatching { FirmwareCore.Device(modelCode,regionCode) }.onSuccess { checkDevice(it) }.onFailure { searchDialog() } }
    private fun checkDevice(device: FirmwareCore.Device) {
        if(modelCode.isEmpty()) { modelCode=device.model;regionCode=device.csc;saveSelection() }
        runWork("Checking ${device.model} / ${device.csc}…",{
            val snapshot=store.fetch(device) { message->runOnUiThread { if(!isDestroyed)status.text=message } }
            if(!Thread.currentThread().isInterrupted)store.remember(snapshot)
            snapshot
        }) { value->checkFinished(value as JSONObject) }
    }
    private fun checkFinished(snapshot: JSONObject) {
        if("$modelCode:$regionCode"=="${snapshot.getString("model")}:${snapshot.getString("csc")}")selected=snapshot
        render();status.text="Check complete · ${snapshot.getString("model")} / ${snapshot.getString("csc")}" 
        if(listOf("test","official").none { snapshot.optJSONObject(it)?.optString("status")=="OK" })return
        if(!store.prefs.getBoolean("starCheckCompleted",false))store.prefs.edit().putBoolean("starCheckCompleted",true).putLong("starPromptDue",System.currentTimeMillis()+5000).apply()
        scheduleStarPrompt()
    }
    private fun scheduleStarPrompt() {
        starHandler.removeCallbacks(starPrompt)
        if(!resumed || !store.prefs.getBoolean("starCheckCompleted",false) || store.prefs.getBoolean("starPromptShown",false))return
        starHandler.postDelayed(starPrompt,maxOf(0,store.prefs.getLong("starPromptDue",0)-System.currentTimeMillis()))
    }
    private fun showStarPromptIfReady() {
        if(!resumed || isFinishing || isDestroyed || store.prefs.getBoolean("starPromptShown",false))return
        if(busy || visibleDialog?.isShowing==true) { starHandler.postDelayed(starPrompt,500);return }
        store.prefs.edit().putBoolean("starPromptShown",true).apply()
        dialogBuilder().setTitle("Enjoying FirmPulse?")
            .setMessage("Support Haroon by starring FirmPulse on GitHub.")
            .setPositiveButton("Star on GitHub") { _,_->open("https://github.com/haroon-ai1/FirmPulse") }
            .setNegativeButton("Not now",null).show()
    }
    override fun onResume() { super.onResume();resumed=true;scheduleStarPrompt() }
    override fun onPause() { resumed=false;starHandler.removeCallbacks(starPrompt);super.onPause() }
    private fun batch(keys: List<String>) {
        if(keys.isEmpty()) { toast("Save a device first");return }
        runWork("Checking saved devices…",{
            var count=0
            for(key in keys) {
                if(Thread.currentThread().isInterrupted)break
                val d=store.device(key)
                runOnUiThread { if(!isDestroyed)status.text="Checking ${++count} of ${keys.size}: ${d.model} / ${d.csc}" }
                val snapshot=store.fetch(d);if(!Thread.currentThread().isInterrupted)store.remember(snapshot)
            };keys.size
        }) { selected=store.result("$modelCode:$regionCode") ?: selected;render();status.text="Saved-device checks complete" }
    }
    private fun saveCurrent() {
        val d=runCatching { FirmwareCore.Device(modelCode,regionCode) }.getOrNull() ?: run { searchDialog(true);return }
        store.saveDevice(d,true);WatchJob.schedule(this);toast("Device saved to your watchlist")
    }
    private fun compareDialog() {
        if(busy) { toast("Wait for the current check");return }
        val form=ui.col().apply { setPadding(ui.dp(20),ui.dp(10),ui.dp(20),0) }
        val model=entryField("Full model",modelCode);val codes=entryField("Region codes, separated by commas",regionCode)
        form.addView(ui.label("Compare up to six region codes for the same exact phone model. Different model endings are different variants.",13,ui.muted));form.addView(model,ui.margin(10));form.addView(codes,ui.margin(10))
        val dialog=dialogBuilder().setTitle("Compare region codes").setView(form).setPositiveButton("Compare",null).setNegativeButton("Cancel",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val values=codes.text.toString().split(Regex("[,\\s]+" )).filter { it.isNotBlank() }.distinct()
            if(values.isEmpty()||values.size>6) { codes.error="Enter one to six three-character codes";return@setOnClickListener }
            val devices=try { values.map { FirmwareCore.Device(model.text.toString(),it) } } catch(e: IllegalArgumentException) { codes.error=e.message;return@setOnClickListener }
            dialog.dismiss();runWork("Comparing region codes…",{
                devices.mapIndexed { i,d->runOnUiThread { if(!isDestroyed)status.text="Comparing ${i+1} of ${devices.size}: ${d.csc}" };store.fetch(d).also { if(!Thread.currentThread().isInterrupted)store.remember(it) } }
            }) { result->@Suppress("UNCHECKED_CAST") val snapshots=result as List<JSONObject>;showComparison(snapshots) }
        } };dialog.show()
    }
    private fun showComparison(snapshots: List<JSONObject>) {
        val report=buildString {
            append("FirmPulse region comparison\n${snapshots.first().optString("model")}\n\n")
            snapshots.forEach { snapshot->
                val released=snapshot.getJSONObject("official");val test=snapshot.getJSONObject("test")
                append("${snapshot.getString("csc")}\nReleased: ${if(released.optString("status")=="OK") FirmwareCore.shortBuild(released.optString("latest")).ifEmpty { "Not identified" } else "Check failed"}\n")
                val choice=store.choice(snapshot)
                append("Test: ${if(test.optString("status")=="OK") if(choice.build.isEmpty()) "Not identified" else FirmwareCore.shortBuild(choice.build)+" · "+choice.label else "Check failed"}\n")
                bestNote(snapshot)?.let { append("Published: ${it.optString("date")} · Patch: ${it.optString("patch")}\n") }
                append("Checked: ${date(snapshot.optLong("time"))}\n\n")
            }
            append("A published build does not guarantee an OTA for every phone. Test candidates are not confirmed rollout announcements.")
        }
        val panel=ui.col().apply { setPadding(ui.dp(18),ui.dp(14),ui.dp(18),ui.dp(12));setBackgroundColor(ui.background) }
        panel.addView(ui.label(snapshots.first().getString("model"),18,ui.ink,true),ui.margin(0,12))
        val headings=ui.row();listOf("Region","Available","Testing").forEach { headings.addView(ui.label(it,12,ui.muted,true),ui.weight()) };panel.addView(headings,ui.margin(0,10))
        snapshots.forEach { snapshot->
            val card=ui.card(panel)
            val row=ui.row();row.addView(ui.label(snapshot.getString("csc"),16,ui.ink,true),ui.weight())
            val release=snapshot.getJSONObject("official")
            val available=if(release.optString("status")=="OK") FirmwareCore.shortBuild(release.optString("latest")).ifEmpty { "Unknown" } else "Failed"
            row.addView(ui.label(available,15,if(ui.dark) ui.mint else ui.mintInk,true),ui.weight())
            val test=snapshot.getJSONObject("test");val choice=store.choice(snapshot)
            row.addView(ui.label(if(test.optString("status")!="OK") "Failed" else if(choice.build.isEmpty()) "Unknown" else FirmwareCore.shortBuild(choice.build),15,ui.blueInk,true),ui.weight());card.addView(row)
            if(test.optString("status")=="OK"&&choice.build.isNotEmpty())card.addView(ui.label("Testing: ${choice.label}",11,ui.muted),ui.margin(6))
            bestNote(snapshot)?.let { card.addView(ui.label("Published ${it.optString("date")} · Patch ${it.optString("patch")}",11,ui.muted),ui.margin(7)) }
        }
        panel.addView(ui.label("A listed build does not guarantee an OTA update. Test candidates may not be Samsung's latest test build.",12,ui.muted))
        val scroll=ScrollView(this).apply { addView(panel) }
        dialogBuilder().setTitle("Region comparison").setView(scroll).setPositiveButton("Close",null)
            .setNeutralButton("Share") { _,_->shareText(report) }
            .setNegativeButton("Save devices") { _,_->snapshots.forEach { store.saveDevice(FirmwareCore.Device(it.getString("model"),it.getString("csc"))) };render();toast("Compared devices saved") }.show()
        selected=store.result("$modelCode:$regionCode") ?: selected
    }
    private fun runWork(message: String,task: ()->Any,done: (Any)->Unit) {
        if(busy) { toast("Wait for the current check to finish");return }
        busy=true;status.text=message;progress.visibility=View.VISIBLE
        job=worker.submit {
            try {
                val value=task()
                if(!Thread.currentThread().isInterrupted)runOnUiThread { if(!isDestroyed&&!isFinishing) { busy=false;progress.visibility=View.GONE;updateStatus();done(value) } }
            } catch(error: Exception) { if(!Thread.currentThread().isInterrupted)runOnUiThread { if(!isDestroyed&&!isFinishing) { busy=false;progress.visibility=View.GONE;status.text="Couldn't finish. Please try again.";textDialog("Check interrupted",error.message.orEmpty().take(250)) } } }
        }
    }
    private fun report(snapshot: JSONObject)=buildString {
        append("FirmPulse ${FirmwareCore.CLIENT_VERSION}\n${snapshot.getString("model")} / ${snapshot.getString("csc")}\nChecked ${date(snapshot.getLong("time"))}\nMatching methods: MD5 and HMAC-SHA256\n")
        if(snapshot.getString("model").startsWith("SM-S"))append("S-series support coming soon. Test and beta results may not be fully accurate yet.\n")
        store.olderTestReason(snapshot,store.choice(snapshot).build).takeIf { it.isNotEmpty() }?.let { append("Older build found: $it A newer test build hasn't been identified.\n") }
        for(name in listOf("test","official")) {
            val data=snapshot.getJSONObject(name)
            append("\n${if(name=="test") "Test" else "Released"} list: ${data.optString("status")}\nSamsung's latest field: ${data.optString("latest").ifEmpty { "Empty" }}\nAndroid: ${data.optString("android").ifEmpty { "Not stated" }}\n")
            if(name=="test") { val choice=store.choice(snapshot);append("Displayed result: ${choice.label}\nBuild: ${choice.build.ifEmpty { "Not identified" }}\n${choice.explanation}\n") }
            val entries=store.feed(data).previous;val matches=store.matches(snapshot)
            entries.forEach { value->append("$value\n");val matched=matches[value.lowercase()].orEmpty();if(FirmwareCore.matchesCandidate(value,matched))append("Matched name: $matched\n") }
            append("Source: ${data.optString("source")}\n")
        }
        bestNote(snapshot)?.let { append("\n${noteText(it)}\n") }
        append("\nObserved times are local checks. Test entries do not establish public beta availability, installability or rollout dates.")
    }
    private fun exportResults() {
        val snapshot=selected ?: return
        choose("Export this check",listOf("JSON: complete result","CSV: all test entries")) { i ->
            if(i==0) createDocument("FirmPulse-${snapshot.getString("model")}-${snapshot.getString("csc")}.json","application/json",snapshot.toString(2).toByteArray())
            else {
                fun csv(value: String)="\""+value.replace("\"","\"\"")+"\""
                val test=store.feed(snapshot.getJSONObject("test"));val matches=store.matches(snapshot)
                val rows=buildString { append("model,csc,entry,matched_build,already_released,first_observed\r\n");(listOf(test.latest)+test.previous).filter { it.isNotEmpty() }.distinct().forEach { value->val match=matches[value.lowercase()].orEmpty().takeIf { FirmwareCore.matchesCandidate(value,it) }.orEmpty();val build=match.ifEmpty { if(FirmwareCore.isHash(value)) "" else value };val seen=snapshot.optJSONObject("firstSeen")?.optLong(value,0)?:0;append(listOf(snapshot.getString("model"),snapshot.getString("csc"),value,match,if(build.isNotEmpty()&&FirmwareCore.isReleasedBuild(build,store.official(snapshot))) "yes" else "unconfirmed",if(seen>0)date(seen) else "").joinToString(",") { csv(it) });append("\r\n") } }
                createDocument("FirmPulse-test-entries.csv","text/csv",rows.toByteArray())
            }
        }
    }
    private var pendingExport: ByteArray?=null
    private fun createDocument(name: String,type: String,data: ByteArray) {
        pendingExport=data
        File(cacheDir,"pending-export.bin").writeBytes(data)
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE);this.type=type;putExtra(Intent.EXTRA_TITLE,name) },43)
    }
    private fun exportBookmarks()=createDocument("FirmPulse-saved-devices.json","application/json",store.exportBookmarks().toByteArray())
    override fun onActivityResult(request: Int,result: Int,data: Intent?) {
        super.onActivityResult(request,result,data)
        if(request==43) {
            val pending=File(cacheDir,"pending-export.bin")
            val bytes=pendingExport ?: pending.takeIf { it.isFile }?.readBytes();pendingExport=null
            if(result==RESULT_OK && data?.data!=null && bytes!=null)runWork("Saving your file…",{
                contentResolver.openOutputStream(data.data!!,"wt")!!.use { it.write(bytes) };pending.delete();true
            }) { toast("File saved") } else pending.delete()
        }
        if(request==42 && result==RESULT_OK && data?.data!=null)runWork("Reading saved devices…",{
            val input=contentResolver.openInputStream(data.data!!) ?: error("File unavailable")
            val bytes=input.use { stream ->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true) { val count=stream.read(buffer);if(count<0)break;require(output.size()+count<=1048576) { "The backup is too large" };output.write(buffer,0,count) };output.toByteArray()
            }
            store.importBookmarks(bytes.toString(Charsets.UTF_8))
        }) { count->WatchJob.schedule(this);render();toast("$count saved devices imported") }
    }
    private fun shareText(text: String) {
        val send=Intent(Intent.ACTION_SEND).apply { type="text/plain" }
        if(text.length<=100000)send.putExtra(Intent.EXTRA_TEXT,text) else {
            val directory=File(cacheDir,"shares").apply { mkdirs() };val file=File(directory,"FirmPulse-report-${System.currentTimeMillis()}.txt");file.writeText(text)
            val uri=Uri.parse("content://$packageName.share/${file.name}");send.putExtra(Intent.EXTRA_STREAM,uri);send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);send.clipData=ClipData.newUri(contentResolver,"FirmPulse report",uri)
        }
        startActivity(Intent.createChooser(send,"Share firmware result"))
    }
    private fun shareCard(testBuildOverride: String="") {
        val snapshot=selected ?: return
        val bitmap=Bitmap.createBitmap(1080,1440,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap)
        val background=Paint(Paint.ANTI_ALIAS_FLAG).apply { shader=LinearGradient(0f,0f,1080f,1440f,intArrayOf(Color.rgb(9,23,44),Color.rgb(17,49,86)),null,Shader.TileMode.CLAMP) };canvas.drawRect(0f,0f,1080f,1440f,background)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        fun text(value: String,y: Float,size: Float,color: Int=Color.WHITE,bold: Boolean=false) {
            paint.shader=null;paint.color=color;paint.textSize=size;paint.typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)
            while(paint.measureText(value)>952f && paint.textSize>18f)paint.textSize-=1f
            canvas.drawText(value,64f,y,paint)
        }
        fun box(top: Float,bottom: Float,tint: Int) { paint.shader=null;paint.color=tint;canvas.drawRoundRect(48f,top,1032f,bottom,28f,28f,paint) }
        fun fullBuild(value: String,y: Float) {
            value.split("/").take(3).forEachIndexed { index,part->if(part.isNotEmpty())text(part,y+index*34f,26f,Color.rgb(209,224,242)) }
        }
        val choice=store.choice(snapshot);val info=store.summary(snapshot)
        val build=testBuildOverride.ifEmpty { info.betaClue.ifEmpty { choice.build } }
        val test=snapshot.getJSONObject("test");val released=snapshot.getJSONObject("official")
        val testOk=test.optString("status")=="OK";val releasedOk=released.optString("status")=="OK"
        val beta=FirmwareCore.isBetaStyle(build)
        text("FirmPulse",108f,56f,Color.rgb(144,199,255),true)
        text("${snapshot.getString("model")} / ${snapshot.getString("csc")}",165f,30f)
        box(205f,740f,Color.rgb(28,66,112))
        text(if(beta) "BETA / TEST BUILD" else "TEST BUILD",262f,25f,Color.rgb(153,205,255),true)
        text(if(!testOk) "Check unavailable" else if(build.isEmpty()) "Not identified" else FirmwareCore.shortBuild(build).ifEmpty { FirmwareCore.ap(build) },349f,61f,Color.WHITE,true)
        val status=if(!testOk) "Couldn't check Samsung's list" else if(build.isEmpty()) "Latest test name unavailable" else if(choice.published&&choice.build==build) "Listed in Samsung's latest test field" else "Test candidate · latest status unconfirmed"
        text(status,399f,27f,Color.rgb(195,218,246));if(testOk)fullBuild(build,447f)
        val old=testOk&&store.olderTestReason(snapshot,build).isNotEmpty()
        if(old)text("Older build · newer test build not identified",573f,27f,Color.rgb(255,214,157),true)
        val month=if(testOk)FirmwareCore.codeMonth(build) else ""
        text(if(month.isEmpty()) "Build month: not identified" else "Build-code month: $month",622f,25f,Color.rgb(195,218,246))
        val notes=snapshot.optJSONObject("notes")?.optJSONArray("releases") ?: JSONArray()
        val testDate=(0 until notes.length()).mapNotNull { notes.optJSONObject(it) }.firstOrNull { it.optString("build")==FirmwareCore.ap(build) }?.optString("date").orEmpty()
        text("Published date: "+testDate.ifEmpty { "not supplied" },668f,25f,Color.rgb(195,218,246))
        text("A build-code month is not a release date.",706f,22f,Color.rgb(171,197,229))
        box(765f,1170f,Color.rgb(24,76,57))
        text("LATEST AVAILABLE",822f,25f,Color.rgb(181,247,206),true)
        val available=released.optString("latest")
        text(if(!releasedOk) "Check unavailable" else FirmwareCore.shortBuild(available).ifEmpty { "Not identified" },901f,57f,Color.WHITE,true)
        if(releasedOk)fullBuild(available,952f)
        val note=bestNote(snapshot)
        text("Published: "+note?.optString("date").orEmpty().ifEmpty { "not supplied" },1084f,25f,Color.rgb(197,237,216))
        text("Security patch: "+note?.optString("patch").orEmpty().ifEmpty { "not supplied" },1127f,25f,Color.rgb(197,237,216))
        text("Checked ${date(snapshot.optLong("time"))}",1224f,25f,Color.rgb(180,203,235))
        text(if(snapshot.getString("model").startsWith("SM-S")) "S-series support coming soon · results may be inaccurate" else "Test entries do not confirm a public beta or rollout date.",1273f,24f,Color.rgb(180,203,235))
        text("github.com/haroon-ai1/FirmPulse",1325f,26f,Color.rgb(156,204,255))
        text("Developed by Haroon",1378f,24f,Color.rgb(180,203,235))
        val directory=File(cacheDir,"shares").apply { mkdirs() }
        val file=File(directory,"FirmPulse-${System.currentTimeMillis()}.png");file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        val uri=Uri.parse("content://$packageName.share/${file.name}")
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="image/png";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);clipData=ClipData.newUri(contentResolver,"FirmPulse result",uri) },"Share result card"))
    }
    private fun dialogBuilder()=object : AlertDialog.Builder(this,if(ui.dark) R.style.PulseDialogDark else R.style.PulseDialogLight) {
        override fun create(): AlertDialog = super.create().also { dialog->
            visibleDialog=dialog
            dialog.window?.setWindowAnimations(if(PulseMotion.enabled(this@MainActivity)) R.style.PulseDialogMotion else 0)
        }
    }
    private fun textDialog(title: String,text: String) {
        val view=ui.label(text,14).apply { setPadding(ui.dp(20),ui.dp(16),ui.dp(20),ui.dp(16));setTextIsSelectable(true) }
        val scroll=ScrollView(this).apply { addView(view);setBackgroundColor(ui.background) }
        dialogBuilder().setTitle(title).setView(scroll).setPositiveButton("Close",null)
            .setNeutralButton("Copy") { _,_->(getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(title,text));toast("Copied") }
            .setNegativeButton("Share") { _,_->shareText(text) }.show()
    }
    private fun choose(title: String,values: List<String>,picked: (Int)->Unit) { dialogBuilder().setTitle(title).setItems(values.toTypedArray()) { _,i->picked(i) }.setNegativeButton("Cancel",null).show() }
    private fun confirm(title: String,message: String,action: ()->Unit) { if(busy) { toast("Wait for the current check");return };dialogBuilder().setTitle(title).setMessage(message).setPositiveButton("Continue") { _,_->action() }.setNegativeButton("Cancel",null).show() }
    private fun open(url: String) { if(!url.startsWith("https://")) { toast("No source available");return };runCatching { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }.onFailure { toast("No browser available") } }
    private fun openNotificationSettings() { startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,packageName)) }
    override fun onRequestPermissionsResult(code: Int,permissions: Array<out String>,results: IntArray) { super.onRequestPermissionsResult(code,permissions,results);if(code==31) { val granted=results.firstOrNull()==PackageManager.PERMISSION_GRANTED;store.prefs.edit().putBoolean("alerts",granted).apply();WatchJob.schedule(this);render();if(!granted)toast("Alerts are off. You can enable them in Android settings.") } }
    private fun toast(message: String)=Toast.makeText(this,message,Toast.LENGTH_SHORT).show()
    private fun date(time: Long)=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(time))
    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out);out.putString("model",modelCode);out.putString("csc",regionCode);out.putInt("tab",tab);selected?.toString()?.takeIf { it.length<150000 }?.let { out.putString("selected",it) } }
    override fun onDestroy() { starHandler.removeCallbacks(starPrompt);job?.cancel(true);worker.shutdownNow();super.onDestroy() }
}
