package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ContinueWatchingTest {
    private ContinueWatching.Item item(String id, long updated) {
        ContinueWatching.Item item = new ContinueWatching.Item();
        item.title = id; item.type = "movie"; item.pkg = "com.nuvio.tv";
        item.intent = "nuvio://detail/movie/" + id; item.artwork = ""; item.updated = updated;
        return item;
    }
    @Test public void newestEntryAfterTheTwelfthProviderRowIsNotLost() {
        List<ContinueWatching.Item> list = new ArrayList<>();
        for (int i = 0; i < 100; i++) ContinueWatching.offer(list,item("tt" + i,i),12);
        assertEquals(12,list.size()); assertEquals(99,list.get(0).updated); assertEquals(88,list.get(11).updated);
    }
    @Test public void newestDuplicateWinsRegardlessOfProviderOrder() {
        List<ContinueWatching.Item> list = new ArrayList<>();
        ContinueWatching.offer(list,item("tt1",4),12);
        ContinueWatching.offer(list,item("tt1",9),12);
        ContinueWatching.offer(list,item("tt1",2),12);
        assertEquals(1,list.size()); assertEquals(9,list.get(0).updated);
    }
    @Test public void supersedingAnEntryDoesNotEvictAnUnrelatedEntry() {
        List<ContinueWatching.Item> list = new ArrayList<>();
        ContinueWatching.offer(list,item("tt1",4),2);
        ContinueWatching.offer(list,item("tt2",8),2);
        ContinueWatching.offer(list,item("tt1",9),2);
        assertEquals(2,list.size()); assertEquals("tt1",list.get(0).title); assertEquals("tt2",list.get(1).title);
    }
    @Test public void onlyTheKnownNuvioContinueChannelMarkerIsAccepted() {
        assertTrue(HomePolicy.continueChannel("com.nuvio.tv","com.nuvio.tv"));
        assertTrue(HomePolicy.continueChannel("com.nuvio.app","com.nuvio.app"));
        assertFalse(HomePolicy.continueChannel("com.nuvio.tv","com.nuvio.tv.trending"));
        assertFalse(HomePolicy.continueChannel("com.nuvio.tv",""));
        assertFalse(HomePolicy.continueChannel("other.app","other.app"));
    }
    @Test public void canonicalTitleKeyMatchesAcrossCatalogAndPublishedRow() {
        assertEquals(HomePolicy.posterKey("movie","tt1","","Title"),ContinueWatching.key(item("tt1",1)));
        assertNotEquals(HomePolicy.posterKey("movie","tt1","","Title"),HomePolicy.posterKey("series","tt1","","Title"));
    }
    @Test public void previewWeightOrdersRowsWhenTheProviderPublishesNoTimestamp() {
        List<ContinueWatching.Item> list = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            ContinueWatching.Item row = item("tt"+i,0); row.publishedWeight = i;
            ContinueWatching.offer(list,row,12);
        }
        assertEquals(49,list.get(0).publishedWeight); assertEquals(38,list.get(11).publishedWeight);
    }
}
