package io.github.haroonjadoon.firmscope.core;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

/** Independently written client for Samsung's public firmware metadata feeds. */
public final class FirmwareCore {
    private FirmwareCore() {}
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final String CLIENT_VERSION = "1.0.0";
    // Public version.test protocol constant documented by ducthoe in CheckFirm PR #8.
    // This is used for local build-identifier comparison, not server authentication.
    private static final byte[] TEST_HASH_KEY = "fcjimts25@%".getBytes(StandardCharsets.US_ASCII);

    public static final class Device {
        public final String model, csc;
        public Device(String model, String csc) {
            this.model = normalizeModel(model);
            this.csc = csc.trim().toUpperCase(Locale.ROOT);
            if (!this.model.matches("[A-Z]{2,3}-[A-Z0-9]{3,12}"))
                throw new IllegalArgumentException("Use the exact model number, for example SM-S928B.");
            if (!this.csc.matches("[A-Z0-9]{3}"))
                throw new IllegalArgumentException("CSC must contain three letters or digits, for example INS or PAK.");
        }
        public String key() { return model + ":" + csc; }
        public String url(boolean test) {
            return "https://fota-cloud-dn.ospserver.net/firmware/" + csc + "/" + model +
                    (test ? "/version.test.xml" : "/version.xml");
        }
    }

    public static String normalizeModel(String model) {
        String normalized = model.trim().toUpperCase(Locale.ROOT);
        if (normalized.endsWith("/DS")) normalized = normalized.substring(0, normalized.length() - 3);
        if (normalized.matches("[A-Z][0-9]{3}[A-Z0-9]{0,5}")) normalized = "SM-" + normalized;
        return normalized;
    }
    public static String withVariant(String model, String variant) {
        if (!variant.matches("B|U|U1|N|W|0|E|F|M|C|Q")) throw new IllegalArgumentException("Select a model ending.");
        String normalized = normalizeModel(model);
        if (!normalized.matches("SM-[A-Z][0-9]{3}(B|U1|U|N|W|0|E|F|M|C|Q)?"))
            throw new IllegalArgumentException("Enter a model such as SM-S926 first, or type the full model yourself.");
        return normalized.substring(0, 7) + variant;
    }

    public enum Status { OK, NOT_FOUND, HTTP_ERROR, NETWORK_ERROR, PARSE_ERROR }
    public static final class Feed {
        public final String latest, androidVersion;
        public final List<String> previous;
        public Feed(String latest, String androidVersion, List<String> previous) {
            this.latest = clean(latest);
            this.androidVersion = clean(androidVersion);
            this.previous = Collections.unmodifiableList(new ArrayList<>(previous));
        }
    }
    public static final class Result {
        public final Status status;
        public final Feed feed;
        public final String source, message;
        public final long checkedAt;
        public final int httpCode;
        public Result(Status status, Feed feed, String source, String message, int httpCode) {
            this.status = status; this.feed = feed; this.source = source; this.message = message;
            this.httpCode = httpCode; this.checkedAt = System.currentTimeMillis();
        }
    }

    public static String clean(String text) {
        String value = text == null ? "" : text.trim();
        return value.equalsIgnoreCase("null") ? "" : value;
    }
    /** Identifier shape, not a promise that every identifier uses the same hash algorithm. */
    public static boolean isHash(String value) { return value.matches("(?i)(?:[0-9a-f]{32}|[0-9a-f]{64})"); }
    public static boolean isMd5Hash(String value) { return value.matches("(?i)[0-9a-f]{32}"); }
    public static boolean isHmacHash(String value) { return value.matches("(?i)[0-9a-f]{64}"); }
    public static String ap(String value) { return value.split("/", -1)[0]; }
    public static boolean isBetaStyle(String value) {
        if (isHash(value)) return false;
        String build = ap(value).split("[._]", 2)[0];
        return build.matches("[A-Z0-9]{8,}") && build.charAt(build.length() - 4) == 'Z';
    }
    public static String md5(String value) {
        try {
            return hex(MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String hex(byte[] bytes) {
        char[] result = new char[bytes.length * 2];
        char[] digits = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            result[i * 2] = digits[(bytes[i] & 255) >>> 4];
            result[i * 2 + 1] = digits[bytes[i] & 15];
        }
        return new String(result);
    }
    private static Mac newTestMac() {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(TEST_HASH_KEY, "HmacSHA256"));
            return mac;
        } catch (java.security.GeneralSecurityException impossible) { throw new IllegalStateException(impossible); }
    }
    public static String hmacSha256(String value) {
        if (!StandardCharsets.US_ASCII.newEncoder().canEncode(value))
            throw new IllegalArgumentException("Firmware strings must use ASCII characters.");
        return hex(newTestMac().doFinal(value.getBytes(StandardCharsets.US_ASCII)));
    }
    public static boolean matchesCandidate(String hash, String candidate) {
        String value = candidate.trim();
        if (value.isEmpty() || !isHash(hash)) return false;
        if (isMd5Hash(hash)) return md5(value).equalsIgnoreCase(hash);
        return StandardCharsets.US_ASCII.newEncoder().canEncode(value) && hmacSha256(value).equalsIgnoreCase(hash);
    }
    /** Compare supplied full names directly before spending a candidate-search budget. */
    public static Map<String, String> matchKnown(Feed test, Collection<String> candidates) {
        Set<String> md5Targets = new HashSet<>(), hmacTargets = new HashSet<>();
        for (String value : test.previous) {
            if (isMd5Hash(value)) md5Targets.add(value.toLowerCase(Locale.ROOT));
            if (isHmacHash(value)) hmacTargets.add(value.toLowerCase(Locale.ROOT));
        }
        if (isMd5Hash(test.latest)) md5Targets.add(test.latest.toLowerCase(Locale.ROOT));
        if (isHmacHash(test.latest)) hmacTargets.add(test.latest.toLowerCase(Locale.ROOT));
        Map<String, String> result = new LinkedHashMap<>();
        Mac mac = hmacTargets.isEmpty() ? null : newTestMac();
        for (String value : new LinkedHashSet<>(candidates)) {
            String full = value.trim();
            if (full.split("/", -1).length != 3 || !StandardCharsets.US_ASCII.newEncoder().canEncode(full)) continue;
            if (!md5Targets.isEmpty()) {
                String hash = md5(full);
                if (md5Targets.contains(hash)) result.put(hash, full);
            }
            if (mac != null) {
                String hash = hex(mac.doFinal(full.getBytes(StandardCharsets.US_ASCII)));
                if (hmacTargets.contains(hash)) result.put(hash, full);
            }
        }
        return result;
    }

    public static String buildSuffix(String value) {
        String build = ap(clean(value)).split("[._]", 2)[0];
        if (isHash(build) || !build.matches("[A-Z0-9]{8,}")) return "";
        String suffix = build.substring(build.length() - 6);
        return suffix.matches("[A-Z][0-9A-Z][A-Z][K-Z][A-L][0-9A-Z]") ? suffix : "";
    }
    public static String shortBuild(String value) {
        String suffix = buildSuffix(value);
        return suffix.isEmpty() ? "" : suffix.substring(2);
    }
    public static String codeMonth(String value) {
        String suffix = buildSuffix(value);
        if (suffix.isEmpty()) return "";
        String[] months = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};
        return months[suffix.charAt(4) - 'A'] + " " + (2011 + suffix.charAt(3) - 'K');
    }
    /** Conservative age clue: compare dates first, revisions only within the same branch. */
    public static String olderBuildReason(String candidate, String released) {
        String a=buildSuffix(candidate), b=buildSuffix(released);
        if (a.isEmpty() || b.isEmpty() || !sameFamily(candidate,released)) return "";
        int month=(a.charAt(3)-b.charAt(3))*12+a.charAt(4)-b.charAt(4);
        if (month<0) return "Build month: "+codeMonth(candidate)+"; latest available: "+codeMonth(released)+".";
        if (month==0 && a.charAt(1)==b.charAt(1) && a.charAt(2)==b.charAt(2) && a.charAt(5)<b.charAt(5))
            return "Lower revision in the same build month and software branch.";
        return "";
    }
    /** A ranking of comparable build codes, not a claim about Samsung's unpublished latest field. */
    public static int compareBuildCodes(String left, String right) {
        String a = buildSuffix(left), b = buildSuffix(right);
        if (a.isEmpty() || b.isEmpty()) return a.isEmpty() ? (b.isEmpty() ? 0 : -1) : 1;
        // Security/update marker U/S is deliberately excluded from chronological ordering.
        for (int position : new int[]{1, 2, 3, 4, 5}) {
            int cmp = Character.compare(a.charAt(position), b.charAt(position));
            if (cmp != 0) return cmp;
        }
        return 0;
    }
    private static boolean sameFamily(String candidate, String official) {
        String a = buildSuffix(candidate), b = buildSuffix(official);
        if (a.isEmpty()) return false;
        if (b.isEmpty()) return true;
        String left = ap(candidate).split("[._]", 2)[0];
        String right = ap(official).split("[._]", 2)[0];
        return left.substring(0, left.length() - 6).equals(right.substring(0, right.length() - 6));
    }
    public static boolean isReleasedBuild(String candidate, Feed official) {
        if (candidate.isEmpty() || isHash(candidate)) return false;
        List<String> released = new ArrayList<>(official.previous);
        if (!official.latest.isEmpty()) released.add(official.latest);
        for (String value : released) {
            if (isHash(value) ? matchesCandidate(value, candidate) : ap(value).equals(ap(candidate))) return true;
        }
        return false;
    }
    public static final class TestSummary {
        public final String visibleClue, betaClue, recoveredClue, recoveryHash;
        public final int visibleCount, betaCount, hiddenCount, releasedOverlapCount, historicalCount;
        public TestSummary(String visibleClue, String betaClue, String recoveredClue, String recoveryHash,
                           int visibleCount, int betaCount, int hiddenCount, int releasedOverlapCount, int historicalCount) {
            this.visibleClue = visibleClue; this.betaClue = betaClue;
            this.recoveredClue = recoveredClue; this.recoveryHash = recoveryHash;
            this.visibleCount = visibleCount; this.betaCount = betaCount; this.hiddenCount = hiddenCount;
            this.releasedOverlapCount = releasedOverlapCount; this.historicalCount = historicalCount;
        }
        public String bestClue() {
            return compareBuildCodes(recoveredClue, visibleClue) > 0 ? recoveredClue : visibleClue;
        }
    }
    public static final class TestChoice {
        public final String build, label, explanation;
        public final boolean published;
        TestChoice(String build, String label, String explanation, boolean published) {
            this.build = build; this.label = label; this.explanation = explanation; this.published = published;
        }
    }
    public static TestChoice selectTest(Feed test, Feed official, Map<String, String> recovered) {
        String latest = test.latest;
        String identified = isHash(latest) ? recovered.getOrDefault(latest.toLowerCase(Locale.ROOT), "") : latest;
        if (isHash(latest) && !matchesCandidate(latest, identified)) identified = "";
        if (!identified.isEmpty() && sameFamily(identified, official.latest) && !isReleasedBuild(identified, official)) {
            return new TestChoice(identified, isBetaStyle(identified) ? "Beta-style build" : "In testing",
                    isHash(latest) ? "This build matches Samsung's latest test identifier." : "Samsung lists this build in its latest test field.", true);
        }
        TestSummary summary = summarizeTest(test, official, recovered);
        String clue = summary.bestClue();
        if (!clue.isEmpty()) return new TestChoice(clue, "Test candidate", "Found in the test list. Samsung has not identified it as the latest test build.", false);
        if (!summary.betaClue.isEmpty()) return new TestChoice(summary.betaClue, "Beta-style build", "Found in the test list. Public beta availability and latest status are unconfirmed.", false);
        boolean entries = !latest.isEmpty() || !test.previous.isEmpty();
        return new TestChoice("", entries ? "Test build not identified" : "No test data listed",
                summary.releasedOverlapCount > 0 ? "The identified entries include released software. A separate test build has not been identified." :
                entries ? "Samsung's test list is available, but a current build name has not been identified." : "Samsung returned no test identifiers for this model and region.", false);
    }
    public static TestSummary summarizeTest(Feed test, String official, Map<String, String> recovered) {
        return summarizeTest(test, new Feed(official, "", Collections.emptyList()), recovered);
    }
    public static TestSummary summarizeTest(Feed test, Feed officialFeed, Map<String, String> recovered) {
        String official = officialFeed.latest;
        String visible = "", beta = "", recoveredBest = "", bestHash = "";
        int visibleCount = 0, betaCount = 0, hiddenCount = 0, releasedCount = 0, historicalCount = 0;
        Set<String> identifiers = new LinkedHashSet<>(test.previous);
        if (!test.latest.isEmpty()) identifiers.add(test.latest);
        for (String value : identifiers) {
            if (isHash(value)) {
                hiddenCount++;
                String candidate = recovered.get(value.toLowerCase(Locale.ROOT));
                if (candidate != null && matchesCandidate(value, candidate) && sameFamily(candidate, official)) {
                    if (isReleasedBuild(candidate, officialFeed)) { releasedCount++; continue; }
                    if (isBetaStyle(candidate)) {
                        betaCount++;
                        if (compareBuildCodes(candidate, beta) > 0) beta = candidate;
                    } else if (compareBuildCodes(candidate, recoveredBest) > 0) {
                        recoveredBest = candidate; bestHash = value;
                    }
                    if (!isBetaStyle(candidate) && !buildSuffix(official).isEmpty() && compareBuildCodes(candidate, official) <= 0) historicalCount++;
                }
            } else if (sameFamily(value, official)) {
                if (isReleasedBuild(value, officialFeed)) { releasedCount++; continue; }
                if (isBetaStyle(value)) {
                    betaCount++;
                    if (compareBuildCodes(value, beta) > 0) beta = value;
                } else {
                    visibleCount++;
                    if (!buildSuffix(official).isEmpty() && compareBuildCodes(value, official) <= 0) historicalCount++;
                    if (compareBuildCodes(value, visible) > 0) visible = value;
                }
            }
        }
        return new TestSummary(visible, beta, recoveredBest, bestHash, visibleCount, betaCount, hiddenCount, releasedCount, historicalCount);
    }

    public interface MatchProgress { void update(int tried, int found); }
    public static final class Discovery {
        public final Map<String, String> matches;
        public final int tried;
        public final boolean limitReached;
        Discovery(Map<String, String> matches, int tried, boolean limitReached) {
            this.matches = Collections.unmodifiableMap(new LinkedHashMap<>(matches));
            this.tried = tried; this.limitReached = limitReached;
        }
    }
    private static final class Matcher {
        final Set<String> targets;
        final Map<String, String> matches = new LinkedHashMap<>();
        final MessageDigest digest;
        final Mac mac;
        final boolean useMd5;
        final int budget;
        final MatchProgress progress;
        int tried;
        Matcher(Set<String> targets, int budget, MatchProgress progress) {
            this.targets = targets; this.budget = budget; this.progress = progress;
            try { this.digest = MessageDigest.getInstance("MD5"); }
            catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
            this.useMd5 = targets.stream().anyMatch(FirmwareCore::isMd5Hash);
            this.mac = targets.stream().anyMatch(FirmwareCore::isHmacHash) ? newTestMac() : null;
        }
        boolean candidate(String ap, String csc, String cp) throws InterruptedIOException {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Local search cancelled.");
            if (tried >= budget) return false;
            String full = ap + "/" + csc + "/" + cp;
            byte[] bytes = full.getBytes(StandardCharsets.US_ASCII);
            if (useMd5) {
                String hash = hex(digest.digest(bytes));
                if (targets.contains(hash)) matches.put(hash, full);
            }
            if (mac != null) {
                String hash = hex(mac.doFinal(bytes));
                if (targets.contains(hash)) matches.put(hash, full);
            }
            tried++;
            if (progress != null && tried % 10000 == 0) progress.update(tried, matches.size());
            return true;
        }
        Discovery result() { return new Discovery(matches, tried, tried >= budget); }
    }
    /** Bounded, offline candidate matching. No network request or community-database access. */
    public static Discovery discover(Feed test, String official, int budget, boolean thorough, MatchProgress progress)
            throws InterruptedIOException {
        if (budget < 1 || budget > 1500000) throw new IllegalArgumentException("Candidate budget must be 1–1500000.");
        Set<String> targets = new HashSet<>();
        if (isHash(test.latest)) targets.add(test.latest.toLowerCase(Locale.ROOT));
        else if (test.latest.isEmpty()) for (String value : test.previous) if (isHash(value)) targets.add(value.toLowerCase(Locale.ROOT));
        Matcher matcher = new Matcher(targets, budget, progress);
        String[] components = official.split("/", -1);
        if (targets.isEmpty() || components.length != 3) return matcher.result();
        String seed = buildSuffix(components[0]);
        if (seed.isEmpty() || components[1].length() < 6 ||
                !components[1].substring(components[1].length() - 5).matches("[0-9A-Z][A-Z][K-Z][A-L][0-9A-Z]")) return matcher.result();
        String apPrefix = components[0].substring(0, components[0].length() - 6);
        String cscPrefix = components[1].substring(0, components[1].length() - 5);
        String cpSeed = buildSuffix(components[2]);
        if (!components[2].isEmpty() && cpSeed.isEmpty()) return matcher.result();
        String cpPrefix = components[2].isEmpty() ? "" : components[2].substring(0, components[2].length() - 6);
        char cpMarker = cpSeed.isEmpty() ? seed.charAt(0) : cpSeed.charAt(0);
        Set<Character> branches = new LinkedHashSet<>(Arrays.asList(seed.charAt(2), 'Z'));
        if (seed.charAt(2) < 'Y') branches.add((char) (seed.charAt(2) + 1));
        Set<Character> bootloaders = new LinkedHashSet<>();
        bootloaders.add(seed.charAt(1));
        char nextBoot = seed.charAt(1) == '9' ? 'A' : (char) (seed.charAt(1) + 1);
        if (nextBoot <= 'Z') bootloaders.add(nextBoot);
        Set<Character> markers = new LinkedHashSet<>(Arrays.asList(seed.charAt(0), 'U', 'S'));
        String revisions = "123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        List<String> suffixes = new ArrayList<>();
        for (char boot : bootloaders) for (char branch : branches) for (char month = 'A'; month <= 'L'; month++)
            for (char revision : revisions.toCharArray()) for (char marker : markers)
                suffixes.add("" + marker + boot + branch + seed.charAt(3) + month + revision);
        suffixes.sort((a, b) -> {
            boolean aOriginal = a.charAt(2) == seed.charAt(2) && a.charAt(1) == seed.charAt(1);
            boolean bOriginal = b.charAt(2) == seed.charAt(2) && b.charAt(1) == seed.charAt(1);
            if (aOriginal != bOriginal) return aOriginal ? -1 : 1;
            if (a.charAt(1) != b.charAt(1)) return Character.compare(a.charAt(1), b.charAt(1));
            if (a.charAt(2) != b.charAt(2)) return Character.compare(a.charAt(2), b.charAt(2));
            int monthA=monthPriority(a.charAt(4),seed.charAt(4)), monthB=monthPriority(b.charAt(4),seed.charAt(4));
            if(monthA!=monthB) return Integer.compare(monthA,monthB);
            return -Character.compare(a.charAt(5),b.charAt(5));
        });
        // First cover common aligned AP/CSC/CP builds, plus unchanged components.
        for (String suffix : suffixes) {
            String apBuild = apPrefix + suffix;
            String alignedCsc = cscPrefix + suffix.substring(1);
            String alignedCp = components[2].isEmpty() ? "" : cpPrefix + cpMarker + suffix.substring(1);
            String sameMarkerCp = components[2].isEmpty() ? "" : cpPrefix + suffix;
            if (!matcher.candidate(apBuild, alignedCsc, alignedCp) ||
                !matcher.candidate(apBuild, alignedCsc, sameMarkerCp) ||
                !matcher.candidate(apBuild, components[1], components[2]) ||
                !matcher.candidate(apBuild, alignedCsc, components[2]) ||
                !matcher.candidate(apBuild, components[1], alignedCp)) return matcher.result();
        }
        if (thorough) {
            // Extend to separately rebuilt CSC/CP revisions within the candidate's month.
            // This is intentionally bounded; an unmatched identifier remains hidden.
            for (String suffix : suffixes) {
                String apBuild = apPrefix + suffix;
                for (char cscRevision : revisions.toCharArray()) for (char cpRevision : revisions.toCharArray()) {
                    String cscBuild = cscPrefix + suffix.substring(1, 5) + cscRevision;
                    String cpBuild = components[2].isEmpty() ? "" : cpPrefix + cpMarker + suffix.substring(1, 5) + cpRevision;
                    if (!matcher.candidate(apBuild, cscBuild, cpBuild)) return matcher.result();
                    if (!components[2].isEmpty() && cpMarker != 'U' && !matcher.candidate(apBuild,cscBuild,cpPrefix+'U'+suffix.substring(1,5)+cpRevision)) return matcher.result();
                    if (!components[2].isEmpty() && cpMarker != 'S' && !matcher.candidate(apBuild,cscBuild,cpPrefix+'S'+suffix.substring(1,5)+cpRevision)) return matcher.result();
                }
            }
        }
        return matcher.result();
    }
    /** Sherlock-style independent component matching, using several public/local seed strings. */
    public static Discovery discoverExpanded(Feed test, List<String> seedValues, int budget, MatchProgress progress)
            throws InterruptedIOException {
        if (budget < 1 || budget > 1500000) throw new IllegalArgumentException("Candidate budget must be 1–1500000.");
        Set<String> targets=new HashSet<>();
        if(isHash(test.latest)) targets.add(test.latest.toLowerCase(Locale.ROOT));
        else if(test.latest.isEmpty()) for(String value:test.previous) if(isHash(value)) targets.add(value.toLowerCase(Locale.ROOT));
        Matcher search=new Matcher(targets,budget,progress);
        List<String> seeds=new ArrayList<>();
        for(String value:new LinkedHashSet<>(seedValues)) {
            String[] parts=value.split("/",-1);
            if(parts.length==3 && !buildSuffix(parts[0]).isEmpty() && parts[1].matches("[A-Z0-9]{8,}") &&
                    (parts[2].isEmpty() || !buildSuffix(parts[2]).isEmpty())) seeds.add(value);
            if(seeds.size()==16)break;
        }
        if(seeds.isEmpty()||targets.isEmpty())return search.result();
        // Common aligned combinations first; share the total budget across seeds.
        for(String seed:seeds.subList(0,Math.min(4,seeds.size()))) {
            int allowance=Math.min(Math.min(50000,Math.max(1,budget/8)),budget-search.tried);if(allowance==0)return search.result();
            final int offset=search.tried;
            Discovery quick=discover(test,seed,allowance,false,(tried,found)-> { if(progress!=null)progress.update(offset+tried,search.matches.size()+found); });
            search.tried+=quick.tried;search.matches.putAll(quick.matches);
            if(search.matches.keySet().containsAll(targets))return search.result();
        }
        String[] base=seeds.get(0).split("/",-1);String seed=buildSuffix(base[0]);
        String apPrefix=base[0].substring(0,base[0].length()-6);
        String cscPrefix=base[1].substring(0,base[1].length()-5);
        String cpPrefix=base[2].isEmpty()?"":base[2].substring(0,base[2].length()-6);
        LinkedHashSet<String> knownCsc=new LinkedHashSet<>(),knownCp=new LinkedHashSet<>();
        for(String value:seeds) { String[] parts=value.split("/",-1);if(!sameFamily(parts[0],base[0]))continue;knownCsc.add(parts[1]);knownCp.add(parts[2]); }
        LinkedHashSet<String> apSuffixes=new LinkedHashSet<>();
        // Round-robin revisions avoids exhausting the budget on one implausibly high revision.
        String revisions="123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        List<Character> months=new ArrayList<>();for(char month='A';month<='L';month++)months.add(month);
        months.sort((a,b)->Integer.compare(monthPriority(a,seed.charAt(4)),monthPriority(b,seed.charAt(4))));
        LinkedHashSet<Character> boots=new LinkedHashSet<>();boots.add(seed.charAt(1));
        char next=seed.charAt(1)=='9'?'A':(char)(seed.charAt(1)+1);if(next<='Z')boots.add(next);
        LinkedHashSet<Character> branches=new LinkedHashSet<>(Arrays.asList(seed.charAt(2),'Z'));
        if(seed.charAt(2)<'Y')branches.add((char)(seed.charAt(2)+1));
        for(char revision:new char[]{'1','2','3'})for(char month:months.subList(0,Math.min(3,months.size())))
            for(char boot:boots)for(char branch:branches)for(char marker:new char[]{'U','S'})
                apSuffixes.add(""+marker+boot+branch+seed.charAt(3)+month+revision);
        if(seed.charAt(4)=='L' && seed.charAt(3)<'Z')for(char revision:revisions.toCharArray())
            for(char boot:boots)for(char branch:branches)for(char marker:new char[]{'U','S'})
                apSuffixes.add(""+marker+boot+branch+(char)(seed.charAt(3)+1)+'A'+revision);
        for(char revision:revisions.toCharArray())for(char month:months)for(char boot:boots)for(char branch:branches)
            for(char marker:new LinkedHashSet<>(Arrays.asList(seed.charAt(0),'U','S')))
                apSuffixes.add(""+marker+boot+branch+seed.charAt(3)+month+revision);
        for(String suffix:apSuffixes) {
            String apBuild=apPrefix+suffix;
            LinkedHashSet<String> cscs=new LinkedHashSet<>(knownCsc),cps=new LinkedHashSet<>(knownCp);
            cscs.add(cscPrefix+suffix.substring(1));
            if(!cpPrefix.isEmpty())cps.add(cpPrefix+suffix);
            // Like a manual/script search, CSC and modem may retain earlier months/revisions.
            int maximum=Math.max(1,Math.min(9,revisions.indexOf(suffix.charAt(5))+1));
            for(int age=0;age<=3;age++) {
                char month=(char)(suffix.charAt(4)-age),year=suffix.charAt(3);
                if(month<'A') { month=(char)(month+12);year--; }
                if(year<'K')continue;
                for(int r=0;r<maximum;r++) {
                    String tail=""+suffix.charAt(1)+suffix.charAt(2)+year+month+revisions.charAt(r);
                    cscs.add(cscPrefix+tail);
                    if(!cpPrefix.isEmpty())for(char marker:new char[]{'U','S'})cps.add(cpPrefix+marker+tail);
                    String oldCsc=base[1].substring(base[1].length()-5);
                    cscs.add(cscPrefix+oldCsc.substring(0,2)+year+month+revisions.charAt(r));
                    if(!cpPrefix.isEmpty()) {
                        String oldCp=buildSuffix(base[2]);
                        for(char marker:new char[]{'U','S'})cps.add(cpPrefix+marker+oldCp.substring(1,3)+year+month+revisions.charAt(r));
                    }
                }
            }
            for(String csc:cscs)for(String cp:cps) {
                if(!search.candidate(apBuild,csc,cp))return search.result();
                if(search.matches.keySet().containsAll(targets))return search.result();
            }
        }
        return search.result();
    }
    private static int monthPriority(char month,char seedMonth) {
        if(month==seedMonth) return 1;
        if(month==seedMonth+1) return 0;
        return 2+Math.abs(month-seedMonth)*2+(month>seedMonth?1:0);
    }

    public static Feed parse(String xml) throws Exception {
        if (xml.length() > MAX_BYTES) throw new IOException("Firmware response is too large.");
        // Only UTF-8 metadata is accepted. Reject declarations before any XML entity processing.
        String upper = xml.toUpperCase(Locale.ROOT);
        if (upper.contains("<!DOCTYPE") || upper.contains("<!ENTITY"))
            throw new SAXException("DTD and entity declarations are not accepted.");
        if (xml.startsWith("\uFEFF")) xml = xml.substring(1);
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        XMLReader reader = factory.newSAXParser().getXMLReader();
        for (String feature : new String[]{"http://xml.org/sax/features/external-general-entities",
                "http://xml.org/sax/features/external-parameter-entities"}) {
            try { reader.setFeature(feature, false); } catch (SAXException unsupported) { /* DTDs rejected above. */ }
        }
        reader.setEntityResolver((publicId, systemId) -> { throw new SAXException("External entities are disabled."); });
        final List<String> path = new ArrayList<>();
        final LinkedHashSet<String> previous = new LinkedHashSet<>();
        final String[] latest = {""}, android = {""};
        final boolean[] versionSeen = {false};
        DefaultHandler handler = new DefaultHandler() {
            StringBuilder text = new StringBuilder();
            String capture = "";
            @Override public void startElement(String uri, String local, String qualified, Attributes attrs) throws SAXException {
                String name = local.isEmpty() ? qualified : local;
                path.add(name);
                if (path.size() == 1 && !name.equals("versioninfo")) throw new SAXException("Unexpected firmware document.");
                String route = String.join("/", path);
                if (route.equals("versioninfo/firmware/version")) versionSeen[0] = true;
                if (route.equals("versioninfo/firmware/version/latest") ||
                        route.equals("versioninfo/firmware/version/upgrade/value")) {
                    capture = route; text.setLength(0);
                    if (name.equals("latest")) android[0] = clean(attrs.getValue("o"));
                }
            }
            @Override public void characters(char[] chars, int start, int length) {
                if (!capture.isEmpty()) text.append(chars, start, length);
            }
            @Override public void endElement(String uri, String local, String qualified) {
                String route = String.join("/", path);
                if (route.equals(capture)) {
                    String value = clean(text.toString());
                    if (route.endsWith("/latest")) latest[0] = value;
                    else if (!value.isEmpty()) previous.add(value);
                    capture = "";
                }
                path.remove(path.size() - 1);
            }
            @Override public void error(SAXParseException e) throws SAXException { throw e; }
            @Override public void fatalError(SAXParseException e) throws SAXException { throw e; }
        };
        reader.setContentHandler(handler); reader.setErrorHandler(handler);
        reader.parse(new InputSource(new StringReader(xml)));
        if (!versionSeen[0]) throw new SAXException("Firmware version section is missing.");
        return new Feed(latest[0], android[0], new ArrayList<>(previous));
    }

    public static Result fetch(Device device, boolean test) {
        String source = device.url(test);
        HttpURLConnection connection = null;
        int code = 0;
        try {
            connection = (HttpURLConnection) new URL(source).openConnection();
            connection.setConnectTimeout(12000); connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/xml,text/xml,*/*");
            connection.setRequestProperty("User-Agent", "FirmPulse/" + CLIENT_VERSION + " Android");
            code = connection.getResponseCode();
            if (code == 404) return new Result(Status.NOT_FOUND, null, source,
                    "Samsung has no feed at this model/CSC address. Check both codes.", code);
            if (code != 200) return new Result(Status.HTTP_ERROR, null, source,
                    "Samsung returned HTTP " + code + ". Try again later or try another network.", code);
            byte[] bytes;
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[8192]; int count;
                while ((count = input.read(chunk)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Search cancelled.");
                    if (buffer.size() + count > MAX_BYTES) throw new IOException("Firmware response is too large.");
                    buffer.write(chunk, 0, count);
                }
                bytes = buffer.toByteArray();
            }
            try {
                return new Result(Status.OK, parse(new String(bytes, StandardCharsets.UTF_8)), source, "", code);
            } catch (Exception malformed) {
                return new Result(Status.PARSE_ERROR, null, source,
                        "The response could not be read as firmware XML. " + malformed.getMessage(), code);
            }
        } catch (Exception failed) {
            return new Result(Status.NETWORK_ERROR, null, source,
                    failed instanceof SocketTimeoutException ? "The request timed out. Try again." :
                    "Could not reach Samsung: " + failed.getClass().getSimpleName() + ". Check your connection.", code);
        } finally { if (connection != null) connection.disconnect(); }
    }
}
