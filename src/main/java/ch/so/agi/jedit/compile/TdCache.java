package ch.so.agi.jedit.compile;

import ch.interlis.ili2c.metamodel.TransferDescription;
import org.gjt.sp.jedit.*;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.*;

public final class TdCache {

    // one entry per *open* Buffer, garbage-collected when the buffer closes 
    private static final Map<Buffer, Entry> MAP = new WeakHashMap<>();
    
    // single background worker – ili2c is not thread-safe
    private static final ExecutorService EXEC =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "ili2c-parser");
                t.setDaemon(true);
                return t;
            });
        
    private TdCache() {} // static helper only
    
    // Return a Future; caller may block or attach a callback.
    public static Future<TransferDescription> get(Buffer buf) {
        long rev = buf.getLastModified();
        Entry e  = MAP.get(buf);

        if (e != null && e.revision == rev) {
            return e.future; // still valid (may be done)            
        }

        // submit new parsing task
        Future<TransferDescription> fut = EXEC.submit(() -> Ili2cUtil.run(buf, null, false).td());

        MAP.put(buf, new Entry(rev, fut));
        return fut;
    }
    
    // Store a TD explicitly (used by CompilerService after compile).
    public static void put(Buffer buf, TransferDescription td) {
        long rev = buf.getLastModified();
        MAP.put(buf, new Entry(rev, CompletableFuture.completedFuture(td)));
    }
    
    // Invalidate when the buffer becomes dirty.
    public static void invalidate(Buffer buf) { MAP.remove(buf); }
    
    private static final class Entry {
        final long  revision; // Buffer.getLastModified()
        final Future<TransferDescription> future;
        Entry(long revision, Future<TransferDescription> future) {
            this.revision = revision;
            this.future   = future;
        }
    }
}
