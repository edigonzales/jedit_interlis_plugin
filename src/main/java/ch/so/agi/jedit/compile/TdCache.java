package ch.so.agi.jedit.compile;

import ch.interlis.ili2c.metamodel.TransferDescription;
import org.gjt.sp.jedit.*;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.*;

public final class TdCache {

    // one entry per *open* Buffer, garbage-collected when the buffer closes 
    private static final Map<Buffer, Entry> MAP = new WeakHashMap<>();
    private static final ExecutorService BG = Executors.newSingleThreadExecutor();
    
    // caller gets a Future – real parsing happens off the EDT 
    public static Future<TransferDescription> get(Buffer buf) {
        Entry e = MAP.get(buf);

        long rev = buf.getLastModified();

        if (e != null && e.getRevision() == rev)
            return e.getFuture(); // still valid

        /* re-parse in background */
        Future<TransferDescription> f = BG.submit(() -> Ili2cUtil.parse(buf));

        MAP.put(buf, new Entry(rev, f));
        return f;
    }
    
    // optional: explicit invalidation when you know the buffer changed
    public static void invalidate(Buffer buf) { MAP.remove(buf); }
    
    private static class Entry {
        private long revision;
        
        private Future<TransferDescription> future;
        
        public Entry(long revision, Future<TransferDescription> future) {
            this.revision = revision;
            this.future = future;
        }
        
        public long getRevision() {
            return revision;
        }

//        public void setRevision(long revision) {
//            this.revision = revision;
//        }
//
        public Future<TransferDescription> getFuture() {
            return future;
        }

//        public void setFuture(Future<TransferDescription> future) {
//            this.future = future;
//        }
    }
}
