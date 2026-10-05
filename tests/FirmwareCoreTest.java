import io.github.haroonjadoon.firmscope.core.FirmwareCore;
import io.github.haroonjadoon.firmscope.core.SamsungInfo;
import io.github.haroonjadoon.firmscope.core.FeedChanges;
import java.util.*;

/** Dependency-free checks for the protocol boundary; run scripts/test-core.sh. */
public final class FirmwareCoreTest {
    static int checks;
    static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    static void rejects(String xml, String message) throws Exception {
        boolean rejected = false;
        try { FirmwareCore.parse(xml); } catch (Exception expected) { rejected = true; }
        check(rejected, message);
    }
    public static void main(String[] args) throws Exception {
        FirmwareCore.Device device = new FirmwareCore.Device(" sm-s928b ", "ins");
        check(device.url(false).equals("https://fota-cloud-dn.ospserver.net/firmware/INS/SM-S928B/version.xml"), "Released URL");
        check(device.url(true).endsWith("/INS/SM-S928B/version.test.xml"), "Test URL");
        check(new FirmwareCore.Device("SC-03L", "D01").csc.equals("D01"), "Numeric CSC and Japanese model");
        boolean invalid = false;
        try { new FirmwareCore.Device("SM-S928B/other", "INS"); } catch (IllegalArgumentException expected) { invalid = true; }
        check(invalid, "Reject injected path");
        invalid = false;
        try { new FirmwareCore.Device("SM-S928B", "INS/other"); } catch (IllegalArgumentException expected) { invalid = true; }
        check(invalid, "Reject invalid CSC");
        String xml = "<versioninfo><firmware><model>SM-S928B</model><version>" +
            "<latest o='16' future='anything'>S928BXXU4CYI1/S928BOXM4CYI1/S928BXXU4CYI1</latest>" +
            "<upgrade><value>first</value><value rcount='2'>second</value><value>first</value><value> </value></upgrade>" +
            "</version></firmware><unexpected>ignored</unexpected></versioninfo>";
        FirmwareCore.Feed result = FirmwareCore.parse(xml);
        check(result.latest.startsWith("S928B"), "Parse current build");
        check(result.androidVersion.equals("16"), "Read optional Android attribute");
        check(result.previous.equals(Arrays.asList("first", "second")), "Deduplicate and preserve feed order");
        result = FirmwareCore.parse("<versioninfo><firmware><version><latest/></version></firmware></versioninfo>");
        check(result.latest.isEmpty() && result.previous.isEmpty(), "Empty latest and missing upgrade are valid");
        result = FirmwareCore.parse("<versioninfo xmlns='urn:firmware'><firmware><version><latest> abc </latest></version></firmware></versioninfo>");
        check(result.latest.equals("abc"), "Namespace and whitespace");
        result = FirmwareCore.parse("\uFEFF<versioninfo><firmware><version><latest>null</latest><upgrade><value>hash</value></upgrade></version></firmware></versioninfo>");
        check(result.latest.isEmpty() && result.previous.size() == 1, "BOM and literal null");
        rejects("<html><body>Access denied</body></html>", "Reject non-firmware HTML");
        rejects("<versioninfo><firmware/></versioninfo>", "Reject missing version section");
        rejects("<versioninfo><firmware><version>", "Reject truncated XML");
        rejects("<!DOCTYPE versioninfo [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]><versioninfo><firmware><version><latest>&secret;</latest></version></firmware></versioninfo>", "Reject external entities");
        rejects("<!DOCTYPE versioninfo [<!ENTITY a 'spam'>]><versioninfo><firmware><version><latest>&a;</latest></version></firmware></versioninfo>", "Reject internal entity declarations");
        check(FirmwareCore.isHash("d41d8cd98f00b204e9800998ecf8427e"), "Identify a hash");
        check(!FirmwareCore.isBetaStyle("d41d8cd98f00b204e9800998ecf8427e"), "Do not interpret hash as build");
        check(FirmwareCore.isBetaStyle("S928BXXU4ZYI1/S928BOXM4ZYI1/S928BXXU4ZYI1"), "Beta-style code");
        check(!FirmwareCore.isBetaStyle("S928BXXU4CYI1/S928BOXM4CYI1/S928BXXU4CYI1"), "Released-style code");
        check(!FirmwareCore.isBetaStyle("Z"), "Short input is safe");
        check(FirmwareCore.md5("abc").equals("900150983cd24fb0d6963f7d28e17f72"), "Known MD5 vector");
        String full = "S928BXXU4ZYI1/S928BOXM4ZYI1/S928BXXU4ZYI1";
        check(FirmwareCore.matchesCandidate(FirmwareCore.md5(full), full), "Verify full candidate");
        check(!FirmwareCore.matchesCandidate(FirmwareCore.md5(full), FirmwareCore.ap(full)), "Partial candidate must not match");
        check(!FirmwareCore.matchesCandidate("not-a-hash", full), "Reject non-hash target");
        String released = "S926USQS6DZI1/S926UOYN6DZI1/S926USQS6DZI1";
        String visibleOld = "S926USQU6DZH1/S926UOYN6DZH1/S926USQU6DZH1";
        String visibleNew = "S926USQS6DZJ2/S926UOYN6DZJ2/S926USQS6DZJ2";
        String beta = "S926USQU6ZZJ3/S926UOYN6ZZJ3/S926USQU6ZZJ3";
        FirmwareCore.Feed emptyLatest = new FirmwareCore.Feed("", "", Arrays.asList(visibleOld, visibleNew, beta));
        FirmwareCore.TestSummary summary = FirmwareCore.summarizeTest(emptyLatest, released, Collections.emptyMap());
        check(summary.visibleClue.equals(visibleNew), "Empty latest still produces a visible test clue");
        check(summary.betaClue.equals(beta) && summary.betaCount == 1, "Separate beta-style clues");
        check(FirmwareCore.compareBuildCodes(released, visibleOld) > 0, "Security U/S marker must not outrank a newer code month");
        check(FirmwareCore.shortBuild(released).equals("DZI1"), "Short build from screenshot's released firmware");
        check(FirmwareCore.codeMonth(released).equals("September 2026"), "Decode code month without claiming a release date");
        check(FirmwareCore.buildSuffix("abc").isEmpty(), "Do not decode short or hidden identifiers");
        summary = FirmwareCore.summarizeTest(new FirmwareCore.Feed("", "", Arrays.asList(full)), released, Collections.emptyMap());
        check(summary.visibleClue.isEmpty(), "Ignore clues from another model family");

        // Reproduce the screenshot's 273-entry, empty-latest structure with SYNTHETIC hashes.
        String recovered = "S926USQU6DZJ3/S926UOYN6DZJ3/S926USQU6DZJ3";
        List<String> hidden = new ArrayList<>();
        for (int i = 0; i < 271; i++) hidden.add(FirmwareCore.md5("synthetic-identifier-" + i));
        hidden.add(FirmwareCore.md5(recovered)); hidden.add(FirmwareCore.md5(beta));
        FirmwareCore.Feed hiddenFeed = new FirmwareCore.Feed("", "", hidden);
        FirmwareCore.Discovery discovery = FirmwareCore.discover(hiddenFeed, released, 50000, false, null);
        check(discovery.matches.containsValue(recovered), "Recover an aligned full candidate from an empty-latest hash entry");
        check(discovery.matches.containsValue(beta), "Recover beta-style candidate separately");
        check(discovery.tried <= 50000, "Automatic candidate search remains bounded");
        summary = FirmwareCore.summarizeTest(hiddenFeed, released, discovery.matches);
        check(summary.hiddenCount == 273, "Preserve 273 hidden feed identifiers");
        check(summary.recoveredClue.equals(recovered) && summary.bestClue().equals(recovered), "Display a hash-verified test clue");
        check(summary.betaClue.equals(beta), "Recovered beta does not replace the ordinary clue");
        Map<String, String> stale = new HashMap<>();
        stale.put(FirmwareCore.md5(recovered), "S926USQU6DZJ4/S926UOYN6DZJ4/S926USQU6DZJ4");
        summary = FirmwareCore.summarizeTest(hiddenFeed, released, stale);
        check(summary.recoveredClue.isEmpty(), "Never accept a cached candidate without rechecking its hash");
        discovery = FirmwareCore.discover(hiddenFeed, released, 4, true, null);
        check(discovery.tried == 4 && discovery.limitReached, "Hard candidate budget");
        String published = FirmwareCore.md5(recovered);
        discovery = FirmwareCore.discover(new FirmwareCore.Feed(published, "", hidden), released, 50000, false, null);
        check(discovery.matches.size() == 1 && discovery.matches.containsKey(published), "A published latest hash restricts recovery to that identifier");
        discovery = FirmwareCore.discover(hiddenFeed, "invalid", 50000, false, null);
        check(discovery.tried == 0, "Do not guess a family prefix without a valid seed");
        Thread.currentThread().interrupt();
        boolean cancelled = false;
        try { FirmwareCore.discover(hiddenFeed, released, 50000, false, null); }
        catch (java.io.InterruptedIOException expected) { cancelled = true; }
        finally { Thread.interrupted(); }
        check(cancelled, "Cancel local search promptly");
        String separateRevisions = "S926USQU6DZJ3/S926UOYN6DZJ2/S926USQS6DZJ1";
        FirmwareCore.Feed independentComponents = new FirmwareCore.Feed(FirmwareCore.md5(separateRevisions), "", Collections.emptyList());
        discovery = FirmwareCore.discover(independentComponents, released, 500000, true, null);
        check(discovery.matches.containsValue(separateRevisions), "Extended search handles independently rebuilt CSC and CP revisions");
        check(new FirmwareCore.Device("s926u1", "xaa").model.equals("SM-S926U1"), "Accept a manually typed U1 code without SM prefix");
        check(new FirmwareCore.Device("SM-S926B/DS", "EUX").model.equals("SM-S926B"), "Normalize the public dual-SIM model annotation");
        check(FirmwareCore.withVariant("SM-S926U", "U1").equals("SM-S926U1"), "Change U to U1 without duplicating the ending");
        check(FirmwareCore.withVariant("S926U1", "B").equals("SM-S926B"), "Change a full U1 model to B");
        check(FirmwareCore.withVariant("SM-S926", "N").equals("SM-S926N"), "Append a selected ending to a base model");
        boolean badVariant=false;
        try { FirmwareCore.withVariant("SM-A15", "U"); } catch(IllegalArgumentException expected) { badVariant=true; }
        check(badVariant, "Never silently rewrite an unfamiliar model structure");
        check(!FirmwareCore.matchesCandidate(FirmwareCore.md5(""), ""), "A missing cached name never becomes an empty-name match");
        FirmwareCore.Feed releasedFeed=new FirmwareCore.Feed(released,"16",Arrays.asList(visibleOld));
        FirmwareCore.Feed duplicateFeed=new FirmwareCore.Feed("","",Arrays.asList(released,visibleOld,FirmwareCore.md5(released)));
        Map<String,String> duplicates=Collections.singletonMap(FirmwareCore.md5(released),released);
        summary=FirmwareCore.summarizeTest(duplicateFeed,releasedFeed,duplicates);
        check(summary.bestClue().isEmpty() && summary.releasedOverlapCount==3, "Exclude current, previous and hash-matched released builds from the main test result");
        check(FirmwareCore.selectTest(duplicateFeed,releasedFeed,duplicates).build.isEmpty(), "Never promote a released duplicate into a test headline");
        check(FirmwareCore.selectTest(new FirmwareCore.Feed(released,"16",Collections.emptyList()),releasedFeed,Collections.emptyMap()).build.isEmpty(), "Even an explicit test latest can overlap a released build");
        check(FirmwareCore.selectTest(new FirmwareCore.Feed(FirmwareCore.md5(released),"",Collections.emptyList()),releasedFeed,duplicates).build.isEmpty(), "A matched latest identifier for released firmware stays excluded");
        FirmwareCore.TestChoice selected=FirmwareCore.selectTest(new FirmwareCore.Feed(recovered,"",Collections.emptyList()),releasedFeed,Collections.emptyMap());
        check(selected.build.equals(recovered) && selected.published, "Keep a distinct explicit latest test build");
        selected=FirmwareCore.selectTest(new FirmwareCore.Feed("","",Arrays.asList(recovered)),releasedFeed,Collections.emptyMap());
        check(selected.build.equals(recovered) && !selected.published, "A fallback clue must not acquire explicit latest status");
        selected=FirmwareCore.selectTest(new FirmwareCore.Feed("","",Arrays.asList(beta)),releasedFeed,Collections.emptyMap());
        check(selected.build.equals(beta) && selected.label.equals("Beta-style build"), "Surface a beta-only clue with qualified status");
        selected=FirmwareCore.selectTest(new FirmwareCore.Feed("","",Arrays.asList(FirmwareCore.md5("unknown"))),releasedFeed,Collections.emptyMap());
        check(selected.build.isEmpty() && selected.label.equals("Test build not identified"), "An unresolved hash never borrows the released build name");
        FirmwareCore.Feed sameApReleased=new FirmwareCore.Feed("S926USQS6DZI1/DIFFERENT/CP","",Collections.emptyList());
        check(FirmwareCore.isReleasedBuild(released,sameApReleased), "Same released AP code is not a new test build just because CSC/CP differ");
        FirmwareCore.Feed docsReleased=new FirmwareCore.Feed(released,"",Arrays.asList(FirmwareCore.ap(recovered)));
        check(FirmwareCore.selectTest(new FirmwareCore.Feed("","",Arrays.asList(recovered)),docsReleased,Collections.emptyMap()).build.isEmpty(), "A documented released AP cannot become a future test clue");
        FeedChanges changes=new FeedChanges(new FirmwareCore.Feed("a","16",Arrays.asList("b","c")),new FirmwareCore.Feed("a","16",Arrays.asList("c","b")));
        check(!changes.changed(), "A reordered feed alone does not trigger alerts");
        changes=new FeedChanges(new FirmwareCore.Feed("a","16",Arrays.asList("b")),new FirmwareCore.Feed("d","16",Arrays.asList("a")));
        check(changes.latestChanged && changes.added.equals(Arrays.asList("d")) && changes.removed.equals(Arrays.asList("b")), "Track actual added and removed identifiers separately from latest changes");
        changes=new FeedChanges(new FirmwareCore.Feed("a","15",Collections.emptyList()),new FirmwareCore.Feed("a","16",Collections.emptyList()));
        check(changes.androidChanged && changes.changed(), "Detect published Android metadata changes");
        String page="<h1><b>Galaxy S24+(SM-S926U)</b></h1><div>Build Number : <b>S926USQS6DZI1</b></div><div>Android version : B(Android 16)</div><div>Release Date : 2026-10-01</div><div>Security patch level : 2026-09-05</div><p>Security &amp; stability<br>Improved.</p><div>Build Number : S926USQS6DZH3</div><div>Android version : Android 16</div><div>Release Date : 2026-09-02</div><div>Security patch level : 2026-08-05</div><p>Earlier changes.</p>";
        SamsungInfo.Document doc=SamsungInfo.parse(page,"https://doc.samsungmobile.com/example/eng.html");
        check(doc.name.equals("Galaxy S24+") && doc.releases.size()==2, "Parse the published device name and independent release blocks");
        check(doc.releases.get(0).android.equals("16") && doc.releases.get(0).date.equals("2026-10-01") && doc.releases.get(0).patch.equals("2026-09-05"), "Keep published date, Android and patch separate from encoded month");
        check(doc.releases.get(0).notes.contains("Security & stability") && !doc.releases.get(0).notes.contains("Earlier changes"), "Associate release notes with the correct build and decode HTML text");
        check(SamsungInfo.parse("<html>Access denied</html>","source").releases.isEmpty(), "Missing release metadata does not fabricate a release");
        String wrapper="<input name='dflt_page' value='../../SM-S926U/036204251121/eng.html'>";
        check(SamsungInfo.documentLink(wrapper,"https://doc.samsungmobile.com/SM-S926U/XAA/doc.html").equals("https://doc.samsungmobile.com/SM-S926U/036204251121/eng.html"), "Resolve Samsung's English release-document wrapper");
        boolean externalBlocked=false;
        try { SamsungInfo.documentLink("<input name='dflt_page' value='https://untrusted.example/eng.html'>","https://doc.samsungmobile.com/SM-S926U/XAA/doc.html"); } catch(java.io.IOException expected) { externalBlocked=true; }
        check(externalBlocked,"Reject external document redirects");
        String support="<strong>Current Models for Monthly Security Updates</strong><li>Galaxy S24, Galaxy S24+, Galaxy S24 Ultra</li><strong>Current Models for Quarterly Security Updates</strong><li>Galaxy S21 FE 5G</li>";
        check(SamsungInfo.cadence(support,"Galaxy S24+").equals("Monthly"), "Read an exact device match in Samsung's support list");
        check(SamsungInfo.cadence(support,"Galaxy S21").isEmpty(), "Do not mistake FE or Ultra support for a base model");
        check(SamsungInfo.cadence(support,"Galaxy S21 FE 5G").equals("Quarterly"), "Separate monthly and quarterly sections");
        check(SamsungInfo.text("<script>private()</script>A&#x2B;&nbsp;&amp; B").equals("A+ & B"), "Remove script text and decode numeric entities");
        String opaque="d77ea9576c3dce257b4b6ed7d5c9f1f6a495022e592d5b5b35a7450dfa052a13";
        check(FirmwareCore.isHash(opaque) && !FirmwareCore.isMd5Hash(opaque), "The screenshot's 64-character opaque identifier is hidden, not a visible build");
        check(FirmwareCore.shortBuild(opaque).isEmpty() && !FirmwareCore.matchesCandidate(opaque,released), "An unmatched newer identifier never acquires a guessed name");
        selected=FirmwareCore.selectTest(new FirmwareCore.Feed("","",Arrays.asList(visibleOld)),new FirmwareCore.Feed(released,"",Collections.emptyList()),Collections.emptyMap());
        check(selected.build.equals(visibleOld) && !selected.published, "An older distinct test-list candidate is not silently discarded as a rollback");
        check(!FirmwareCore.olderBuildReason(visibleOld,released).isEmpty(), "Older encoded month is identified");
        check(FirmwareCore.olderBuildReason(recovered,released).isEmpty(), "Newer month never receives an old-build warning");
        check(FirmwareCore.olderBuildReason("S926USQU6ZZH2/S926UOYN6ZZH2/CP",released).contains("August"), "Older beta month is compared separately from branch order");
        check(FirmwareCore.olderBuildReason("S926USQU6DZI0/CSC/CP",released).contains("Lower revision"), "Lower same-month revision in the same branch");
        check(FirmwareCore.olderBuildReason("S926USQU6ZZI0/CSC/CP",released).isEmpty(), "Different same-month beta branch has no invented revision order");
        check(FirmwareCore.olderBuildReason("S928BXXU6DZH1/CSC/CP",released).isEmpty(), "Different phone families cannot be age-compared");
        check(FirmwareCore.olderBuildReason(FirmwareCore.md5(released),released).isEmpty(), "Hidden identifiers have no guessed build age");
        check(FirmwareCore.olderBuildReason("invalid",released).isEmpty(), "Unknown build codes have no guessed date");
        String korean="<h1>Galaxy S24 Ultra (SM-S928N)</h1><div>빌드번호 : S928NKSS6DZH2</div><div>안드로이드 버전 : B(Android 16)</div><div>릴리즈 일자 : 2026-08-26</div><div>보안 패치 레벨 : 2026-08-05</div><p>Notes.</p>";
        doc=SamsungInfo.parse(korean,"https://doc.samsungmobile.com/SM-S928N/028918240216/kor.html");
        check(doc.name.equals("Galaxy S24 Ultra") && doc.releases.size()==1,"Korean-only release documents provide the model name and build");
        check(doc.releases.get(0).android.equals("16") && doc.releases.get(0).date.equals("2026-08-26") && doc.releases.get(0).patch.equals("2026-08-05"),"Parse Korean release labels without translating or guessing notes");
        check(SamsungInfo.documentLink("<input id='dflt_page' value='../../SM-S928N/028918240216/kor.html'>","https://doc.samsungmobile.com/SM-S928N/KOO/doc.html").endsWith("/kor.html"),"Follow the default document when no English language is available");
        check(SamsungInfo.documentLink("<input id='dflt_page' value='../../SM-S928N/028918240216/kor.html'><option value='../../SM-S928N/028918240216/eng.html'>English</option>","https://doc.samsungmobile.com/SM-S928N/KOO/doc.html").endsWith("/eng.html"),"Prefer English when it is explicitly offered");
        String nSeed="S928NKSS6DZH2/S928NOKR6DZH2/S928NKSS6DZE1";
        String nMixed="S928NKSU6DZI2/S928NOKR6DZH1/S928NKSS6DZE1";
        discovery=FirmwareCore.discoverExpanded(new FirmwareCore.Feed(FirmwareCore.md5(nMixed),"",Collections.emptyList()),Arrays.asList(nSeed),250000,null);
        check(discovery.matches.containsValue(nMixed),"Automatic mixed-component search recovers separately dated AP, CSC and modem components");
        check(discovery.tried<=250000,"The expanded automatic search respects its shared total budget");
        String crossBoot="S928NKSU7EZI1/S928NOKR6DZI1/S928NKSS6DZH1";
        discovery=FirmwareCore.discoverExpanded(new FirmwareCore.Feed(FirmwareCore.md5(crossBoot),"",Collections.emptyList()),Arrays.asList(nSeed),250000,null);
        check(discovery.matches.containsValue(crossBoot),"Independent components can retain an earlier bootloader and software branch");

        discovery=FirmwareCore.discoverExpanded(new FirmwareCore.Feed(FirmwareCore.md5("unreachable"),"",Collections.emptyList()),Arrays.asList(nSeed),37,null);
        check(discovery.tried==37 && discovery.limitReached,"Expanded search enforces small caller budgets across phases");
        String december="S928NKSS6DYL1/S928NOKR6DYL1/S928NKSS6DYL1";
        // Same branch D across the supported encoded-year transition.
        String january="S928NKSU6DZA1/S928NOKR6DZA1/S928NKSS6DYL1";
        discovery=FirmwareCore.discoverExpanded(new FirmwareCore.Feed(FirmwareCore.md5(january),"",Collections.emptyList()),Arrays.asList(december),250000,null);
        check(discovery.matches.containsValue(january),"December-to-January component searches can cross the encoded year boundary");
        check(FirmwareCore.hmacSha256("abc").equals("3e01f697d6143b28cca8859aa506f9641ecf146ea3a97c290d92d9ac9675ddaf"), "HMAC vector independently calculated with Python hashlib/hmac");
        String hmac="374fccda0914856fe58cbabe6ed76b3f029b7ae2d929b6fea1d0ded838d88e37";
        check(FirmwareCore.matchesCandidate(hmac.toUpperCase(Locale.ROOT),recovered), "Verify a full name against the newer format, including uppercase hex");
        check(!FirmwareCore.matchesCandidate(hmac,FirmwareCore.ap(recovered)), "Newer hashes require the full three-part string");
        check(!FirmwareCore.matchesCandidate(hmac,"é"), "Non-ASCII text is not silently replaced when checking newer hashes");
        check(!FirmwareCore.matchesCandidate(hmac,""), "An empty cached name does not match a newer identifier");
        List<String> mixed=Arrays.asList(FirmwareCore.md5(beta),hmac);
        discovery=FirmwareCore.discover(new FirmwareCore.Feed("","",mixed),released,50000,false,null);
        check(discovery.matches.get(hmac).equals(recovered) && discovery.matches.get(FirmwareCore.md5(beta)).equals(beta), "One bounded search supports a mixed MD5/HMAC feed");
        check(discovery.tried<=50000,"Mixed methods share a candidate budget rather than doubling attempts");
        discovery=FirmwareCore.discoverExpanded(new FirmwareCore.Feed("22b6b1fd2d018adf9907dd7067ce6f725465d32b5cb387131e6c415e26abab25","",Collections.emptyList()),Arrays.asList(nSeed),250000,null);
        check(discovery.matches.containsValue(nMixed),"The newer matcher supports independently dated main, CSC and modem components");
        discovery=FirmwareCore.discover(new FirmwareCore.Feed(hmac,"",mixed),released,50000,false,null);
        check(discovery.matches.size()==1 && discovery.matches.containsKey(hmac), "Explicit HMAC latest restricts recovery to that identifier");
        selected=FirmwareCore.selectTest(new FirmwareCore.Feed(hmac,"",mixed),releasedFeed,Collections.singletonMap(hmac,recovered));
        check(selected.build.equals(recovered) && selected.published,"A verified newer latest hash can supply the latest test result");
        selected=FirmwareCore.selectTest(new FirmwareCore.Feed("","",mixed),releasedFeed,Collections.singletonMap(hmac,recovered));
        check(selected.build.equals(recovered) && !selected.published,"A recovered newer hash in an empty-latest list remains an unconfirmed candidate");
        String releasedHmac=FirmwareCore.hmacSha256(released);
        selected=FirmwareCore.selectTest(new FirmwareCore.Feed(releasedHmac,"",Collections.emptyList()),releasedFeed,Collections.singletonMap(releasedHmac,released));
        check(selected.build.isEmpty(),"Released duplicates are excluded even when a newer hash occupies latest");
        Map<String,String> direct=FirmwareCore.matchKnown(new FirmwareCore.Feed("","",Arrays.asList(hmac,releasedHmac,FirmwareCore.md5(released))),Arrays.asList(recovered,released));
        check(direct.size()==3 && direct.get(hmac).equals(recovered),"Direct known-name matching supports both formats and released history");
        check(FirmwareCore.matchKnown(new FirmwareCore.Feed("","",mixed),Arrays.asList(FirmwareCore.ap(recovered),"invalid")).isEmpty(),"Partial known-name guesses cannot be accepted");
        Thread.currentThread().interrupt();cancelled=false;
        try { FirmwareCore.discover(new FirmwareCore.Feed(hmac,"",mixed),released,50000,false,null); }
        catch(java.io.InterruptedIOException expected) { cancelled=true; }
        finally { Thread.interrupted(); }
        check(cancelled,"Newer-format search observes cancellation");
        if(args.length>0) {
            String[] pairs={"SM-S926N-KOO","SM-S928B-INS","SM-F766U-TMB"};
            int[] counts={242,293,159};
            for(int i=0;i<pairs.length;i++) {
                FirmwareCore.Feed live=FirmwareCore.parse(java.nio.file.Files.readString(java.nio.file.Path.of(args[0],pairs[i]+"-test.xml")));
                check(live.latest.isEmpty() && live.previous.size()==counts[i],"Captured Samsung response parses with empty latest and missing value attributes: "+pairs[i]);
                check(live.previous.stream().filter(FirmwareCore::isHmacHash).count()==1,"Captured mixed-format response preserves its long identifier: "+pairs[i]);
            }
            FirmwareCore.Feed liveTest=FirmwareCore.parse(java.nio.file.Files.readString(java.nio.file.Path.of(args[0],"SM-S926N-KOO-test.xml")));
            FirmwareCore.Feed liveRelease=FirmwareCore.parse(java.nio.file.Files.readString(java.nio.file.Path.of(args[0],"SM-S926N-KOO-released.xml")));
            List<String> releases=new ArrayList<>(liveRelease.previous);releases.add(liveRelease.latest);
            Map<String,String> liveKnown=FirmwareCore.matchKnown(liveTest,releases);
            check(liveKnown.size()>=40,"Identify released history directly in the captured Samsung test list");
            check(FirmwareCore.selectTest(liveTest,liveRelease,liveKnown).build.isEmpty(),"Captured released matches never become a confirmed newest test build");
            check(!liveKnown.containsKey(opaque),"The cross-model long identifier stays unresolved without an exact matching name");
        }
        System.out.println("PASS: " + checks + " firmware core checks");
    }
}
