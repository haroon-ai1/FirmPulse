package io.github.haroonjadoon.firmscope.core;
import java.util.*;
/** Order-independent changes, keeping the source's explicit latest field separate. */
public final class FeedChanges {
    public final List<String> added, removed;
    public final boolean latestChanged, androidChanged;
    public FeedChanges(FirmwareCore.Feed before,FirmwareCore.Feed after) {
        Set<String> old=new LinkedHashSet<>(before.previous), fresh=new LinkedHashSet<>(after.previous);
        if(!before.latest.isEmpty()) old.add(before.latest);
        if(!after.latest.isEmpty()) fresh.add(after.latest);
        Set<String> a=new LinkedHashSet<>(fresh);a.removeAll(old);added=new ArrayList<>(a);
        Set<String> r=new LinkedHashSet<>(old);r.removeAll(fresh);removed=new ArrayList<>(r);
        latestChanged=!before.latest.equals(after.latest); androidChanged=!before.androidVersion.equals(after.androidVersion);
    }
    public boolean changed() { return latestChanged||androidChanged||!added.isEmpty()||!removed.isEmpty(); }
}
