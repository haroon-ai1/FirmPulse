package io.github.haroonjadoon.firmscope.core;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Optional public Samsung release documentation. Failures never invalidate the firmware feeds. */
public final class SamsungInfo {
    private SamsungInfo() {}
    public static final class Release {
        public final String build, android, date, patch, notes;
        Release(String build, String android, String date, String patch, String notes) {
            this.build=build; this.android=android; this.date=date; this.patch=patch; this.notes=notes;
        }
    }
    public static final class Document {
        public final String name, source, message;
        public final List<Release> releases;
        Document(String name, String source, String message, List<Release> releases) {
            this.name=name; this.source=source; this.message=message;
            this.releases=Collections.unmodifiableList(new ArrayList<>(releases));
        }
    }
    private static String first(String source, String expression) {
        Matcher m=Pattern.compile(expression, Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(source);
        return m.find()?m.group(1).trim():"";
    }
    public static String text(String html) {
        String value=html.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>", "")
                .replaceAll("(?is)<!--.*?-->", "")
                .replaceAll("(?i)<br\\s*/?>|</(?:div|p|li|h[1-6]|tr|section|article)>|<hr\\b[^>]*>", "\n")
                .replaceAll("<[^>]+>", "");
        value=value.replace("&nbsp;", " ").replace("&#160;", " ").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'");
        Matcher entities=Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);").matcher(value);
        StringBuffer decoded=new StringBuffer();
        while(entities.find()) {
            String replacement="";
            try { String raw=entities.group(1); int code=Integer.parseInt(raw.startsWith("x")?raw.substring(1):raw,raw.startsWith("x")?16:10); replacement=new String(Character.toChars(code)); }
            catch(IllegalArgumentException ignored) { replacement=entities.group(); }
            entities.appendReplacement(decoded,Matcher.quoteReplacement(replacement));
        }
        entities.appendTail(decoded);
        return decoded.toString().replaceAll("[\\t\\x0B\\f\\r ]+", " ").replaceAll(" *\n *", "\n").replaceAll("\n{3,}", "\n\n").trim();
    }
    public static Document parse(String html, String source) {
        if(html.length()>FirmwareCore.MAX_BYTES) throw new IllegalArgumentException("Release document is too large.");
        String name=text(first(html,"<h1\\b[^>]*>(.*?)</h1>")).replaceAll("\\s*\\([^)]*\\)\\s*$", "").trim();
        String plain=text(html).replace("빌드번호", "Build Number").replace("안드로이드 버전", "Android version")
                .replace("릴리즈 일자", "Release Date").replace("보안 패치 레벨", "Security patch level");
        Matcher start=Pattern.compile("(?i)Build Number\\s*:\\s*([A-Z0-9._/]+)").matcher(plain);
        List<Integer> begins=new ArrayList<>(), ends=new ArrayList<>(); List<String> builds=new ArrayList<>();
        while(start.find() && builds.size()<150) { begins.add(start.start()); ends.add(start.end()); builds.add(start.group(1)); }
        List<Release> releases=new ArrayList<>();
        for(int i=0;i<builds.size();i++) {
            String block=plain.substring(ends.get(i),i+1<begins.size()?begins.get(i+1):plain.length());
            String android=first(block,"Android version\\s*:\\s*([^\n]+)");
            String number=first(android,"Android\\s+([0-9.]+)"); if(!number.isEmpty()) android=number;
            String date=first(block,"Release Date\\s*:\\s*([0-9]{4}-[0-9]{2}-[0-9]{2})");
            String patch=first(block,"Security patch level\\s*:\\s*([0-9]{4}-[0-9]{2}-[0-9]{2})");
            String notes=block.replaceAll("(?im)^\\s*(Android version|Release Date|Security patch level)\\s*:.*$", "").trim();
            notes=notes.replaceAll("(?is)Copyright.*$", "").trim();
            if(notes.length()>14000) notes=notes.substring(0,14000)+"…";
            releases.add(new Release(builds.get(i),android,date,patch,notes));
        }
        return new Document(name,source,releases.isEmpty()?"Samsung has not provided readable release notes for this combination.":"",releases);
    }
    public static String documentLink(String wrapper, String source) throws IOException {
        Matcher inputs=Pattern.compile("(?is)<(?:input|option)\\b[^>]*>").matcher(wrapper);
        String link="", english="";
        while(inputs.find()) {
            String tag=inputs.group();
            String value=first(tag,"\\bvalue\\s*=\\s*['\"]([^'\"]+)['\"]");
            if(value.endsWith("/eng.html")) english=value;
            if(tag.contains("dflt_page") && value.endsWith(".html")) link=value;
        }
        if(!english.isEmpty()) link=english;
        if(link.isEmpty()) return source;
        URL target=new URL(new URL(source),link);
        validateUrl(target,"doc.samsungmobile.com");
        if(!target.getPath().matches("/[A-Z0-9-]+/[0-9]+/[a-zA-Z-]+\\.html")) throw new IOException("Unexpected release document link.");
        return target.toString();
    }
    private static void validateUrl(URL url,String host) throws IOException {
        if(!url.getProtocol().equals("https") || !url.getHost().equalsIgnoreCase(host) || url.getUserInfo()!=null || (url.getPort()!=-1 && url.getPort()!=443))
            throw new IOException("Unexpected Samsung document address.");
    }
    public static String read(String address,String host) throws IOException {
        URL url=new URL(address); validateUrl(url,host);
        for(int redirects=0;redirects<4;redirects++) {
            HttpURLConnection connection=(HttpURLConnection)url.openConnection();
            connection.setConnectTimeout(10000); connection.setReadTimeout(12000);
            connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
            connection.setRequestProperty("User-Agent","FirmPulse/0.3 Android");
            try {
                int code=connection.getResponseCode();
                if(code>=300 && code<=399) { String location=connection.getHeaderField("Location"); if(location==null) throw new IOException("Missing document redirect."); url=new URL(url,location);validateUrl(url,host);continue; }
                if(code!=200) throw new IOException("Samsung returned HTTP "+code+".");
                try(InputStream input=connection.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
                    byte[] chunk=new byte[8192];int count;
                    while((count=input.read(chunk))!=-1) { if(Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Cancelled."); if(bytes.size()+count>FirmwareCore.MAX_BYTES) throw new IOException("Document is too large.");bytes.write(chunk,0,count); }
                    return new String(bytes.toByteArray(),StandardCharsets.UTF_8);
                }
            } finally { connection.disconnect(); }
        }
        throw new IOException("Too many document redirects.");
    }
    public static Document fetch(FirmwareCore.Device device) {
        String source="https://doc.samsungmobile.com/"+device.model+"/"+device.csc+"/doc.html";
        try {
            String wrapper=read(source,"doc.samsungmobile.com");
            String link=documentLink(wrapper,source);
            return parse(link.equals(source)?wrapper:read(link,"doc.samsungmobile.com"),link);
        } catch(Exception failure) { return new Document("",source,"Release notes unavailable. "+failure.getMessage(),Collections.emptyList()); }
    }
    public static String cadence(String html,String deviceName) {
        if(deviceName.isEmpty()) return "";
        String plain=text(html);
        String[] titles={"Current Models for Monthly Security Updates","Current Models for Quarterly Security Updates","Current Models for Biannual Security Updates","Current Wearable Models for Quarterly Security Updates","Current Personal Computer Models"};
        String[] results={"Monthly","Quarterly","Twice a year","Quarterly",""};
        for(int i=0;i<titles.length;i++) {
            int begin=plain.indexOf(titles[i]); if(begin<0) continue;
            int end=plain.length();
            for(String title:titles) { int next=plain.indexOf(title,begin+titles[i].length()); if(next>=0) end=Math.min(end,next); }
            String section=plain.substring(begin+titles[i].length(),end);
            for(String item:section.split("[,\n]")) if(item.trim().equals(deviceName)) return results[i];
        }
        return "";
    }
}
