package io.github.haroonjadoon.firmscope

import android.app.AlertDialog
import android.app.job.JobScheduler
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.TextView
import io.github.haroonjadoon.firmscope.core.FirmwareCore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FirmPulseUiTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private val store get()=FirmwareStore(context)
    @Before fun reset() { store.prefs.edit().clear().putString("theme","dark").commit() }
    private val released="S926USQS6DZI1/S926UOYN6DZI1/S926USQS6DZI1"
    private val candidate="S926USQU6DZJ3/S926UOYN6DZJ3/S926USQU6DZJ3"
    private fun feed(latest: String,entries: List<String> = emptyList(),state: String="OK") = JSONObject().put("status",state).put("latest",latest).put("android","16").put("previous",JSONArray(entries)).put("source","https://fota-cloud-dn.ospserver.net/firmware/XAA/SM-S926U/version.test.xml")
    private fun fixture(matched: Boolean=false): JSONObject {
        val hash=FirmwareCore.md5(if(matched)candidate else released)
        return JSONObject().put("model","SM-S926U").put("csc","XAA").put("time",1791130200000L)
            .put("official",feed(released)).put("test",feed("",listOf(hash)))
            .put("localMatches",JSONObject().put(hash,if(matched)candidate else released))
            .put("notes",JSONObject().put("name","Galaxy S24+").put("source","https://doc.samsungmobile.com/SM-S926U/XAA/doc.html")
                .put("releases",JSONArray().put(JSONObject().put("build",FirmwareCore.ap(released)).put("android","16").put("date","2026-10-01").put("patch","2026-09-05").put("notes","Security has been improved."))))
    }
    private fun seed(snapshot: JSONObject) { store.remember(snapshot);store.prefs.edit().putString("lastModel","SM-S926U").putString("lastCsc","XAA").commit() }
    private fun views(view: View): List<View> = listOf(view)+(if(view is ViewGroup)(0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList())
    private fun texts(view: View)=views(view).filterIsInstance<TextView>().map { it.text.toString() }
    private fun nav(activity: MainActivity,name: String) { views(activity.window.decorView).first { it.contentDescription?.toString()==name }.performClick() }
    private fun click(view: View,text: String) {
        var target: View=views(view).filterIsInstance<TextView>().first { it.text.toString()==text }
        while(!target.isClickable && target.parent is View)target=target.parent as View
        assertTrue("$text needs a clickable target",target.performClick())
    }
    private fun render(activity: MainActivity,name: String) {
        val view=activity.findViewById<View>(android.R.id.content)
        val width=786;val height=1704
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
        val dir=File(System.getProperty("user.dir"),"build/ui-previews").apply { mkdirs() }
        File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        for(tab in listOf("Home","Devices","Activity","Tools","Settings")) {
            val item=views(view).first { it.contentDescription?.toString()==tab }
            assertTrue("Tab $tab must remain visible",item.width>0&&item.height>0&&item.bottom<=height)
        }
    }
    @Test fun emptyLaunchAndAllTabsRender() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        assertTrue(texts(activity.window.decorView).contains("Follow what Samsung is testing"))
        for(name in listOf("Home","Devices","Activity","Tools","Settings")) { nav(activity,name);render(activity,"empty-${name.lowercase()}") }
        controller.pause().stop().destroy()
    }
    @Test fun releasedHashDoesNotBecomeTestHeadline() {
        seed(fixture());val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        val strings=texts(activity.window.decorView)
        assertTrue(strings.contains("Test build not identified"));assertEquals(1,strings.count { it=="DZI1" })
        render(activity,"home-unresolved")
        controller.pause().stop().destroy()
    }
    @Test fun identifiedTestAndOtherTabsRender() {
        seed(fixture(true));store.saveDevice(FirmwareCore.Device("SM-S926U","XAA"),true)
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        assertTrue(texts(activity.window.decorView).contains("DZJ3"));assertTrue(texts(activity.window.decorView).contains("Test candidate"))
        for(name in listOf("Home","Devices","Activity","Tools","Settings")) { nav(activity,name);render(activity,"populated-${name.lowercase()}") }
        controller.pause().stop().destroy()
    }
    @Test fun manualFieldsAndModelEndingChoicesRemainEditable() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        click(activity.window.decorView,"Choose a phone")
        val dialog=ShadowAlertDialog.getLatestAlertDialog();val fields=views(dialog.window!!.decorView).filterIsInstance<AutoCompleteTextView>()
        assertEquals(2,fields.size);fields[0].setText("S926U1",false);fields[1].setText("XYZ",false)
        click(dialog.window!!.decorView,"Choose ending: B, U, U1, N…")
        val choice=ShadowAlertDialog.getLatestAlertDialog();val list=choice.listView
        list.performItemClick(list.adapter.getView(0,null,list),0,list.adapter.getItemId(0))
        assertEquals("SM-S926B",fields[0].text.toString());assertEquals("XYZ",fields[1].text.toString())
        dialog.dismiss();controller.pause().stop().destroy()
    }
    @Test fun backupImportValidatesWholeFileBeforeWriting() {
        store.saveDevice(FirmwareCore.Device("SM-S926U1","XAA"),true)
        val valid=store.exportBookmarks();val before=store.devices()
        val invalid=JSONObject(valid).getJSONArray("devices").put(JSONObject().put("model","SM-S928B/other").put("csc","INS"))
        try { store.importBookmarks(JSONObject().put("app","FirmPulse").put("format",1).put("devices",invalid).toString());fail("Invalid import must fail") } catch(expected: IllegalArgumentException) {}
        assertEquals(before,store.devices())
        store.prefs.edit().clear().commit();assertEquals(1,store.importBookmarks(valid));assertTrue(store.watched().contains("SM-S926U1:XAA"))
    }
    @Test fun failedChecksPreserveSuccessfulComparisonBaseline() {
        val first=fixture();store.remember(first)
        val failed=fixture().put("time",first.getLong("time")+1000).put("test",feed("",state="HTTP_ERROR"))
        store.remember(failed)
        val next=fixture().put("time",first.getLong("time")+2000)
        assertFalse(store.remember(next));assertEquals(released,store.json("snapshot:SM-S926U:XAA").getJSONObject("official").getString("latest"))
    }
    @Test fun notificationScheduleRespectsOptInAndInterval() {
        assertTrue(WatchJob.schedule(context));assertTrue(context.getSystemService(JobScheduler::class.java).allPendingJobs.isEmpty())
        store.prefs.edit().putBoolean("alerts",true).putInt("interval",3).commit();assertTrue(WatchJob.schedule(context))
        val spec=context.getSystemService(JobScheduler::class.java).allPendingJobs.single();assertEquals(10800000L,spec.intervalMillis);assertTrue(spec.isPersisted)
        store.prefs.edit().putBoolean("alerts",false).commit();WatchJob.schedule(context);assertTrue(context.getSystemService(JobScheduler::class.java).allPendingJobs.isEmpty())
    }
    @Test fun shareProviderDoesNotAllowTraversalOrWrites() {
        val controller=Robolectric.buildContentProvider(ShareProvider::class.java).create();val provider=controller.get()
        File(context.cacheDir,"shares").mkdirs();File(context.cacheDir,"shares/test.png").writeBytes(byteArrayOf(1,2,3))
        val uri=Uri.parse("content://io.github.haroonjadoon.firmscope.share/test.png")
        assertEquals("image/png",provider.getType(uri));provider.openFile(uri,"r").close()
        try { provider.openFile(uri,"rw");fail("Writes must be rejected") } catch(expected: IllegalArgumentException) {}
        try { provider.getType(Uri.parse("content://io.github.haroonjadoon.firmscope.share/../test.png"));fail("Traversal must fail") } catch(expected: IllegalArgumentException) {}
        controller.shutdown()
    }
    @Test fun lightThemeRestoresAndRenders() {
        seed(fixture(true));store.prefs.edit().putString("theme","light").commit()
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get();render(activity,"home-light")
        controller.recreate();assertTrue(texts(controller.get().window.decorView).contains("DZJ3"));controller.pause().stop().destroy()
    }
    @Test fun clearingMatchedNamesAlsoClearsStoredResultsAndHistory() {
        val snapshot=fixture(true);seed(snapshot)
        store.prefs.edit().putString("recovered:SM-S926U:XAA",snapshot.getJSONObject("localMatches").toString()).commit()
        store.clearMatches()
        assertFalse(store.result("SM-S926U:XAA")!!.has("localMatches"))
        assertFalse(store.array("history").getJSONObject(0).has("localMatches"))
        assertFalse(store.prefs.contains("recovered:SM-S926U:XAA"))
    }
    @Test fun oldSavedCheckCannotOverwriteNewerResultDuringMatching() {
        val older=fixture(true);val newer=fixture().put("time",older.getLong("time")+1000)
        store.remember(older);store.remember(newer);store.retainMatches(older)
        assertEquals(newer.getLong("time"),store.result("SM-S926U:XAA")!!.getLong("time"))
    }
    @Test fun compactHomeKeepsRefreshAccessibleBeforeSecondarySections() {
        seed(fixture(true));val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        val strings=texts(activity.window.decorView)
        assertTrue(strings.indexOf("Check again")>=0);assertTrue(strings.indexOf("Check again")<strings.indexOf("LATEST AVAILABLE"))
        render(activity,"home-compact");controller.pause().stop().destroy()
    }
    @Test @Config(sdk=[28]) fun minimumAndroidVersionLaunchesAndSchedules() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        assertTrue(texts(activity.window.decorView).contains("FirmPulse"));nav(activity,"Settings")
        assertTrue(texts(activity.window.decorView).contains("Wi-Fi only"));controller.pause().stop().destroy()
    }
    @Test fun largeFontKeepsAllTabsUsable() {
        RuntimeEnvironment.setFontScale(1.5f)
        seed(fixture(true));val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        for(name in listOf("Home","Devices","Activity","Tools","Settings")) { nav(activity,name);render(activity,"large-${name.lowercase()}") }
        controller.pause().stop().destroy();RuntimeEnvironment.setFontScale(1f)
    }
    private fun renderDialog(dialog: AlertDialog,name: String) {
        val view=dialog.window!!.decorView
        view.measure(View.MeasureSpec.makeMeasureSpec(700,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1150,View.MeasureSpec.EXACTLY));view.layout(0,0,700,1150)
        val bitmap=Bitmap.createBitmap(700,1150,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
        val dir=File(System.getProperty("user.dir"),"build/ui-previews").apply { mkdirs() }
        File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
    @Test fun darkSearchDialogUsesReadableDarkTitleAndFields() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        click(activity.window.decorView,"Choose a phone")
        val dialog=ShadowAlertDialog.getLatestAlertDialog()
        val title=views(dialog.window!!.decorView).filterIsInstance<TextView>().first { it.text.toString()=="Choose a phone" }
        assertEquals(android.graphics.Color.parseColor("#F0F5FF"),title.currentTextColor)
        val labels=views(dialog.window!!.decorView).filterIsInstance<TextView>().filter { it.text.toString() in listOf("Phone model","Region or carrier code (CSC)") }
        assertEquals(2,labels.size);assertTrue(labels.all { it.currentTextColor==title.currentTextColor })
        renderDialog(dialog,"search-dark");dialog.dismiss();controller.pause().stop().destroy()
    }
    @Test fun lightSearchDialogUsesDarkReadableTitle() {
        store.prefs.edit().putString("theme","light").commit()
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        click(activity.window.decorView,"Choose a phone");val dialog=ShadowAlertDialog.getLatestAlertDialog()
        val title=views(dialog.window!!.decorView).filterIsInstance<TextView>().first { it.text.toString()=="Choose a phone" }
        assertEquals(android.graphics.Color.parseColor("#112B48"),title.currentTextColor)
        renderDialog(dialog,"search-light");dialog.dismiss();controller.pause().stop().destroy()
    }
    @Test fun longOpaqueIdentifierIsNeverShownAsVisibleBuild() {
        val opaque="d77ea9576c3dce257b4b6ed7d5c9f1f6a495022e592d5b5b35a7450dfa052a13"
        seed(fixture().put("test",feed("",listOf(opaque))).put("localMatches",JSONObject()))
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get();nav(activity,"Tools")
        val strings=texts(activity.window.decorView);assertTrue(strings.contains("NAME HIDDEN"));assertFalse(strings.contains("VISIBLE BUILD"));assertFalse(strings.contains(opaque))
        render(activity,"opaque-identifier");controller.pause().stop().destroy()
    }
    @Test fun systemBarInsetsKeepNavigationAboveGestureArea() {
        seed(fixture(true));val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        val root=(activity.findViewById<View>(android.R.id.content) as ViewGroup).getChildAt(0)
        val insets=android.view.WindowInsets.Builder().setInsets(android.view.WindowInsets.Type.systemBars(),android.graphics.Insets.of(0,48,0,40)).build()
        root.dispatchApplyWindowInsets(insets)
        assertEquals(48,root.paddingTop);assertEquals(40,root.paddingBottom)
        render(activity,"home-with-system-bars")
        for(name in listOf("Home","Devices","Activity","Tools","Settings")) {
            val view=views(root).first { it.contentDescription?.toString()==name }
            val location=IntArray(2);view.getLocationInWindow(location)
            assertTrue("$name must stay above gesture area",location[1]+view.height<=1704-40)
        }
        controller.pause().stop().destroy()
    }
    @Test fun tabChangeResetsScrollPosition() {
        val many=(0 until 80).map { FirmwareCore.md5("entry-$it") }
        seed(fixture().put("test",feed("",many)));val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        nav(activity,"Tools");render(activity,"tools-many")
        val scrolling=views(activity.window.decorView).filterIsInstance<android.widget.ScrollView>().first()
        scrolling.scrollTo(0,500);assertTrue(scrolling.scrollY>0)
        nav(activity,"Home");Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(0,scrolling.scrollY)
        controller.pause().stop().destroy()
    }
    @Test fun newerManualNameVerificationPersistsAndDisplaysCandidate() {
        val hmac=FirmwareCore.hmacSha256(candidate);val md5=FirmwareCore.md5(candidate)
        val snapshot=fixture().put("test",feed("",listOf(hmac,md5))).put("localMatches",JSONObject())
        assertTrue(store.verify(snapshot,"  ${candidate.lowercase()}  "))
        assertEquals(candidate,snapshot.getJSONObject("localMatches").getString(hmac))
        assertEquals(candidate,store.json("recovered:SM-S926U:XAA").getString(md5))
        assertEquals(candidate,store.choice(snapshot).build);assertFalse(store.choice(snapshot).published)
        seed(snapshot);val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        val strings=texts(activity.window.decorView)
        assertTrue(strings.contains("DZJ3"));assertTrue(strings.contains("Test candidate"))
        assertTrue(strings.contains("Latest status unconfirmed"))
        nav(activity,"Tools");assertFalse(texts(activity.window.decorView).contains("NAME HIDDEN"))
        render(activity,"newer-matched-tools");controller.pause().stop().destroy()
    }
    @Test fun newerReleasedLatestIsExcludedFromTestHeadline() {
        val hmac=FirmwareCore.hmacSha256(released)
        val snapshot=fixture().put("test",feed(hmac)).put("localMatches",JSONObject())
        assertTrue(store.verify(snapshot,released));assertTrue(store.choice(snapshot).build.isEmpty())
        seed(snapshot);val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        val strings=texts(activity.window.decorView)
        assertTrue(strings.contains("Test build not identified"));assertEquals(1,strings.count { it=="DZI1" })
        controller.pause().stop().destroy()
    }
    @Test fun enrichmentMatchesReleasedHistoryBeforeCandidateSearch() {
        val old="S926USQU6DZH1/S926UOYN6DZH1/S926USQU6DZH1"
        val snapshot=fixture().put("official",feed(released,listOf(old)))
            .put("test",feed("",listOf(FirmwareCore.hmacSha256(released),FirmwareCore.md5(old))))
            .put("localMatches",JSONObject())
        store.enrich(snapshot,false)
        assertEquals(2,snapshot.getJSONObject("localMatches").length())
        assertEquals(0,snapshot.getInt("searchAttempts"))
        assertFalse(snapshot.getBoolean("searchLimitReached"));assertTrue(store.choice(snapshot).build.isEmpty())
        assertEquals(2,store.summary(snapshot).releasedOverlapCount)
        assertEquals(FirmwareCore.CLIENT_VERSION,snapshot.getString("appVersion"))
    }
    @Test fun incorrectNewerCachedNameAndPartialManualNameAreRejected() {
        val hmac=FirmwareCore.hmacSha256(candidate)
        val snapshot=fixture().put("test",feed(hmac)).put("localMatches",JSONObject().put(hmac,released))
        assertTrue(store.choice(snapshot).build.isEmpty())
        assertFalse(store.verify(snapshot,FirmwareCore.ap(candidate)))
        assertFalse(store.verify(snapshot,released));assertTrue(store.verify(snapshot,candidate))
        assertTrue(store.choice(snapshot).published)
    }
    @Test fun manualVerificationSupportsWifiBuildsWithoutModemSoftware() {
        val full="X916BXXU1AZJ1/X916BOXM1AZJ1/"
        val old="X916BXXU1AZI1/X916BOXM1AZI1/"
        val snapshot=fixture().put("model","SM-X916B").put("csc","EUX")
            .put("official",feed(old)).put("test",feed("",listOf(FirmwareCore.hmacSha256(full))))
            .put("localMatches",JSONObject())
        assertTrue(store.verify(snapshot,full));assertEquals(full,store.choice(snapshot).build)
        assertFalse(store.verify(snapshot,full+"/extra"))
    }
    @Test fun reducedMotionSurvivesRapidNavigation() {
        store.prefs.edit().putBoolean("reduceMotion",true).putBoolean("glass",false).commit()
        seed(fixture(true));val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        val home=views(activity.window.decorView).first { it.contentDescription?.toString()=="Home" }
        for(name in listOf("Tools","Settings","Devices","Home"))nav(activity,name)
        assertSame(home,views(activity.window.decorView).first { it.contentDescription?.toString()=="Home" })
        assertTrue(home.isSelected);assertFalse(PulseMotion.enabled(activity))
        render(activity,"reduced-motion");controller.pause().stop().destroy()
    }
    @Test fun hiddenEntriesAreCollapsedAndCanBePaged() {
        val many=(0 until 80).map { FirmwareCore.md5("entry-$it") }
        seed(fixture().put("test",feed("",many)).put("localMatches",JSONObject()))
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get();nav(activity,"Tools")
        assertTrue(texts(activity.window.decorView).contains("80 hidden entries"));assertFalse(texts(activity.window.decorView).contains("Showing 30 of 80"))
        click(activity.window.decorView,"Hidden")
        assertTrue(texts(activity.window.decorView).contains("Showing 30 of 80"))
        click(activity.window.decorView,"Show 30 more")
        assertTrue(texts(activity.window.decorView).contains("Showing 60 of 80"))
        render(activity,"tools-paged");controller.pause().stop().destroy()
    }
    @Test fun releasedFeedFillsMissingReleaseNotes() {
        seed(fixture().put("notes",JSONObject()))
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get();nav(activity,"Activity")
        click(activity.window.decorView,"Releases")
        assertTrue(texts(activity.window.decorView).contains("DZI1"));assertTrue(texts(activity.window.decorView).contains("Release notes unavailable"))
        render(activity,"release-fallback");controller.pause().stop().destroy()
    }
    @Test fun cleanLaunchHasNoSamplePhoneOrResults() {
        store.prefs.edit().clear().commit()
        org.robolectric.shadows.ShadowBuild.setManufacturer("other")
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        val strings=texts(activity.window.decorView)
        assertTrue(strings.contains("Choose your Samsung phone"))
        assertFalse(strings.any { it.contains("SM-S926") || it=="DZJ3" })
        assertEquals(1,strings.count { it=="Choose a phone" })
        assertTrue(store.devices().isEmpty());assertNull(store.last())
        nav(activity,"Settings");assertTrue(texts(activity.window.decorView).contains("Use phone setting"))
        controller.pause().stop().destroy()
    }
    @Test fun samsungLaunchPrefillsOnlyItsOwnModel() {
        store.prefs.edit().clear().commit()
        org.robolectric.shadows.ShadowBuild.setManufacturer("samsung")
        org.robolectric.shadows.ShadowBuild.setModel("SM-A556B")
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        assertTrue(texts(activity.window.decorView).contains("SM-A556B"))
        assertFalse(texts(activity.window.decorView).contains("S-series support coming soon"))
        click(activity.window.decorView,"Choose a phone")
        val dialog=ShadowAlertDialog.getLatestAlertDialog();val fields=views(dialog.window!!.decorView).filterIsInstance<AutoCompleteTextView>()
        assertEquals("SM-A556B",fields[0].text.toString());assertEquals("",fields[1].text.toString())
        dialog.dismiss();render(activity,"detected-phone");controller.pause().stop().destroy()
    }
    @Test @Config(qualifiers="w393dp-h852dp-night-xhdpi") fun phoneThemeDefaultsToDarkAtNight() {
        store.prefs.edit().remove("theme").commit()
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        val title=views(activity.window.decorView).filterIsInstance<TextView>().first { it.text.toString()=="FirmPulse" }
        assertEquals(android.graphics.Color.parseColor("#F0F5FF"),title.currentTextColor)
        controller.pause().stop().destroy()
    }
    @Test fun phoneThemeDefaultsToLightAndManualChoicePersists() {
        store.prefs.edit().remove("theme").commit()
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        val title=views(activity.window.decorView).filterIsInstance<TextView>().first { it.text.toString()=="FirmPulse" }
        assertEquals(android.graphics.Color.parseColor("#112B48"),title.currentTextColor)
        nav(activity,"Settings");click(activity.window.decorView,"Theme")
        val dialog=ShadowAlertDialog.getLatestAlertDialog();val list=dialog.listView
        list.performItemClick(list.adapter.getView(0,null,list),0,list.adapter.getItemId(0))
        assertEquals("dark",store.prefs.getString("theme",""));controller.recreate()
        assertEquals("dark",store.prefs.getString("theme",""));controller.pause().stop().destroy()
    }
    @Test fun sSeriesNoticeAndOlderBetaAreClearlyShown() {
        val beta="S926USQU6ZZH2/S926UOYN6ZZH2/S926USQU6DZH2"
        val snapshot=fixture(true).put("test",feed("",listOf(candidate,beta))).put("localMatches",JSONObject())
        seed(snapshot);val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        val strings=texts(activity.window.decorView)
        assertTrue(strings.contains("S-series support coming soon"))
        assertTrue(strings.contains("Older build found"))
        assertTrue(strings.contains("This appears older than the latest available build. A newer test build hasn't been identified."))
        render(activity,"older-beta");controller.pause().stop().destroy()
    }
    @Test fun publishedDatesTakePriorityAndFailuresDoNotInventAge() {
        val older="S926USQU6DZH2/S926UOYN6DZH2/S926USQU6DZH2"
        val snapshot=fixture().put("test",feed("",listOf(older))).put("localMatches",JSONObject())
        assertTrue(store.olderTestReason(snapshot,older).contains("August 2026"))
        snapshot.getJSONObject("notes").getJSONArray("releases").put(JSONObject().put("build",FirmwareCore.ap(older)).put("date","2026-10-02"))
        assertEquals("",store.olderTestReason(snapshot,older))
        snapshot.getJSONObject("notes").getJSONArray("releases").getJSONObject(1).put("date","2026-09-01")
        assertTrue(store.olderTestReason(snapshot,older).contains("Published 2026-09-01"))
        snapshot.getJSONObject("official").put("status","HTTP_ERROR")
        assertEquals("",store.olderTestReason(snapshot,older))
    }
    @Test fun aboutHasOwnerLinksWithoutPreviewCopy() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get();nav(activity,"Settings")
        val strings=texts(activity.window.decorView)
        assertTrue(strings.contains("Haroon"));assertTrue(strings.contains("Star FirmPulse on GitHub"))
        assertFalse(strings.any { it.contains("preview",true) || it.contains("CheckFirm",true) || it.contains("Development-signed") })
        click(activity.window.decorView,"About FirmPulse");var dialog=ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(texts(dialog.window!!.decorView).any { it.contains("Developed by Haroon") });dialog.dismiss()
        assertTrue(strings.indexOf("Haroon")<strings.indexOf("APPEARANCE"))
        assertEquals(android.view.Gravity.CENTER,views(activity.window.decorView).filterIsInstance<TextView>().first { it.text.toString()=="Haroon" }.gravity)
        assertTrue(strings.indexOf("Star FirmPulse on GitHub")>strings.indexOf("Android 9+"))
        click(activity.window.decorView,"GitHub")
        assertEquals("https://github.com/haroon-ai1",Shadows.shadowOf(activity).nextStartedActivity.data.toString())
        click(activity.window.decorView,"Website")
        assertEquals("https://haroon-ai1.vercel.app/",Shadows.shadowOf(activity).nextStartedActivity.data.toString())
        click(activity.window.decorView,"LinkedIn")
        assertEquals("https://pk.linkedin.com/in/haroon-ai",Shadows.shadowOf(activity).nextStartedActivity.data.toString())
        click(activity.window.decorView,"Star FirmPulse on GitHub")
        assertEquals("https://github.com/haroon-ai1/FirmPulse",Shadows.shadowOf(activity).nextStartedActivity.data.toString())
        render(activity,"release-settings");controller.pause().stop().destroy()
    }

    private fun completeCheck(activity: MainActivity,snapshot: JSONObject) {
        MainActivity::class.java.getDeclaredMethod("checkFinished",JSONObject::class.java).apply { isAccessible=true }.invoke(activity,snapshot)
    }
    @Test fun firstCompletedCheckShowsStarPromptAfterFiveSecondsOnlyOnce() {
        seed(fixture(true));val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        completeCheck(activity,fixture(true))
        val looper=Shadows.shadowOf(android.os.Looper.getMainLooper())
        looper.idleFor(java.time.Duration.ofMillis(4900));assertFalse(store.prefs.getBoolean("starPromptShown",false))
        looper.idleFor(java.time.Duration.ofMillis(100));assertTrue(store.prefs.getBoolean("starPromptShown",false))
        val dialog=ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.isShowing)
        assertTrue(texts(dialog.window!!.decorView).contains("Enjoying FirmPulse?"))
        renderDialog(dialog,"star-prompt")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();looper.idle()
        assertEquals("https://github.com/haroon-ai1/FirmPulse",Shadows.shadowOf(activity).nextStartedActivity.data.toString())
        completeCheck(activity,fixture(true));looper.idleFor(java.time.Duration.ofSeconds(6))
        assertFalse(dialog.isShowing)
        controller.recreate();looper.idleFor(java.time.Duration.ofSeconds(6))
        assertTrue(store.prefs.getBoolean("starPromptShown",false));assertFalse(dialog.isShowing)
        controller.pause().stop().destroy()
    }
    @Test fun starPromptWaitsForForegroundAndExistingDialog() {
        seed(fixture(true));val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        completeCheck(activity,fixture(true));controller.pause()
        val looper=Shadows.shadowOf(android.os.Looper.getMainLooper());looper.idleFor(java.time.Duration.ofSeconds(5))
        assertFalse(store.prefs.getBoolean("starPromptShown",false))
        store.prefs.edit().putLong("starPromptDue",System.currentTimeMillis()-1).commit();controller.resume()
        nav(activity,"Settings");click(activity.window.decorView,"About FirmPulse")
        val about=ShadowAlertDialog.getLatestAlertDialog();looper.idle()
        assertFalse(store.prefs.getBoolean("starPromptShown",false));assertTrue(about.isShowing)
        about.dismiss();looper.idleFor(java.time.Duration.ofMillis(500))
        val dialog=ShadowAlertDialog.getLatestAlertDialog();assertTrue(store.prefs.getBoolean("starPromptShown",false))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();looper.idle()
        controller.pause().resume();looper.idleFor(java.time.Duration.ofSeconds(6));assertFalse(dialog.isShowing)
        controller.pause().stop().destroy()
    }
    @Test fun failedFirstCheckDoesNotAskForAStar() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        completeCheck(activity,fixture().put("test",feed("",state="HTTP_ERROR")).put("official",feed("",state="HTTP_ERROR")))
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(6))
        assertFalse(store.prefs.getBoolean("starCheckCompleted",false));assertFalse(store.prefs.getBoolean("starPromptShown",false))
        controller.pause().stop().destroy()
    }

    @Test fun homeShareButtonsExportBrandedPngWithReadPermission() {
        val beta="S926USQU6ZZH2/S926UOYN6ZZH2/S926USQU6DZH2"
        seed(fixture(true).put("test",feed("",listOf(candidate,beta))).put("localMatches",JSONObject()))
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        for((index,name) in listOf("Share test build card","Share beta build card","Share latest available card").withIndex()) {
            views(activity.window.decorView).first { it.contentDescription?.toString()==name }.performClick()
            val chooser=Shadows.shadowOf(activity).nextStartedActivity
            val send=chooser.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)!!
            assertEquals("image/png",send.type)
            assertTrue(send.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION!=0)
            val uri=send.getParcelableExtra<Uri>(android.content.Intent.EXTRA_STREAM)!!
            assertEquals("io.github.haroonjadoon.firmscope.share",uri.authority)
            assertEquals(uri,send.clipData!!.getItemAt(0).uri)
            val image=File(activity.cacheDir,"shares/"+uri.lastPathSegment)
            val bitmap=android.graphics.BitmapFactory.decodeFile(image.path)
            assertEquals(1080,bitmap.width);assertEquals(1440,bitmap.height);bitmap.recycle()
            val dir=File(System.getProperty("user.dir"),"build/ui-previews").apply { mkdirs() }
            image.copyTo(File(dir,"share-$index.png"),overwrite=true)
        }
        controller.pause().stop().destroy()
    }

}
