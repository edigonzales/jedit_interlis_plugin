package ch.so.agi.jedit.compile;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.so.agi.jedit.compile.Ili2cUtil.Result;

import org.gjt.sp.jedit.*;

import java.nio.file.Path;
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
    
    public static Executor getExecutor() { return EXEC; }
    
    // Return a Future; caller may block or attach a callback.
    public static CompletableFuture<TransferDescription> get(Buffer buf) {
        long rev = buf.getLastModified();
        Entry e = MAP.get(buf);

        if (e != null && e.revision == rev) {
            System.err.println("******* e != null && e.revision == rev");
            System.err.println("**** e.index " + e.index);
            return e.future; // still valid           
        }

        // submit new parsing task
        CompletableFuture<TransferDescription> cf = new CompletableFuture<>();
        EXEC.execute(() -> {
            try {
                System.err.println("******* submit new parsing task");

                Result result = Ili2cUtil.run(buf, null, true);
                cf.complete(result.td()); // success (td may be null)
                
                synchronized (MAP) { // WeakHashMap not thread-safe
                    Entry entry = MAP.get(buf);
                    if (entry != null && entry.future == cf) {  // still the latest entry?
                        AstIndex idx = (result.td() != null) ? new AstIndex(buf, result.td()) : null;
                        System.err.println("**** idx: " + idx);
                        MAP.put(buf, new Entry(rev, cf, result.log(), idx));
                    }
                }
            } catch (Exception ex) {
                cf.completeExceptionally(ex); // propagate failure
            }
        });

        // Initial muss log file null gesetzt werden. Es wird im try-Block ersetzt, falls es vorhanden ist.
        MAP.put(buf, new Entry(rev, cf, null, null));
        return cf;
    }
    
    // Store a TD explicitly (used by CompilerService after compile).
    public static void put(Buffer buf, TransferDescription td, Path log) {
        long rev = buf.getLastModified();
        AstIndex idx = (td != null) ? new AstIndex(buf, td) : null;
        MAP.put(buf, new Entry(rev, CompletableFuture.completedFuture(td), log, idx));
    }
    
    // Check if buffer is dirty. Handle accordingly.
    public static TransferDescription peek(Buffer buf) {
        
        System.err.println("******* PEEK");

        Entry e = MAP.get(buf);
        if (e == null || e.revision != buf.getLastModified()) {
            System.err.println("******* PEEK 1");
            return null; // stale or missing            
        }

        Future<TransferDescription> f = e.future;
        if (!f.isDone()) {
            System.err.println("******* PEEK 2");
            return null; // still parsing            
        }

        try { // already finished
            System.err.println("******* PEEK 3");
            System.err.println(f.get().getName());
            System.err.println(e.log);
            return f.get(); // quick, no block
        } catch (Exception ex) {
            return null; // failed → treat as absent
        }
    }
    
    public static Path peekLog(Buffer buf) {
        Entry e = MAP.get(buf);
        return (e != null && e.revision == buf.getLastModified())
               ? e.log : null;
    }
    
    public static AstIndex peekIndex(Buffer buf) {
        Entry e = MAP.get(buf);
        return (e != null && e.revision == buf.getLastModified()) ? e.index : null;
    }
    
    // Invalidate when the buffer becomes dirty.
    public static void invalidate(Buffer buf) { MAP.remove(buf); }
    
    private static final class Entry {
        final long revision;
        final CompletableFuture<TransferDescription> future;
        final Path log; 
        final AstIndex index;
      
        Entry(long revision, CompletableFuture<TransferDescription> future, Path log, AstIndex index) {
            this.revision = revision;
            this.future = future;
            this.log = log;
            this.index = index;
        }
    }
}
