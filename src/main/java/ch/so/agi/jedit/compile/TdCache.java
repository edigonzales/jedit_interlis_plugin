package ch.so.agi.jedit.compile;

import ch.interlis.ili2c.metamodel.Domain;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.Function;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.Unit;
import ch.interlis.ili2c.metamodel.Viewable;
import ch.so.agi.jedit.compile.Ili2cUtil.Result;

import org.gjt.sp.jedit.*;
import org.gjt.sp.util.Log;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.*;

public final class TdCache {

    // one entry per *open* Buffer, garbage-collected when the buffer closes 
    private static final Map<Buffer, Entry> MAP = new WeakHashMap<>();
    private static final Map<Buffer, TransferDescription> MAP_LAST_VALID = new WeakHashMap<>();
    
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
        Log.log(Log.DEBUG, TdCache.class, "get()");
        Log.log(Log.DEBUG, TdCache.class, "get() --> MAP.size(): " + MAP.size());

        long rev = buf.getLastModified(); // timestamp of the file on disk at the time it was last read or written 
        Entry e = MAP.get(buf);

        if (e != null && e.revision == rev) {
            Log.log(Log.DEBUG, TdCache.class, "e != null && e.revision == rev");
            Log.log(Log.DEBUG, TdCache.class, "entry ist immer noch gültig (siehe Bedingung oben)");

            return e.future; // still valid           
        }

        // submit new parsing task
        CompletableFuture<TransferDescription> cf = new CompletableFuture<>();
        EXEC.execute(() -> {
            try {
                Log.log(Log.DEBUG, TdCache.class, "submit new parsing task");
                Log.log(Log.DEBUG, TdCache.class, "Hier wird der Compiler angeworfen.");
                Result result = Ili2cUtil.run(buf, null, true);
                Log.log(Log.DEBUG, TdCache.class, "Direkt nach Compiler-Aufruf.");

                cf.complete(result.td()); // success (td may be null)
                
                synchronized (MAP) { // WeakHashMap not thread-safe
                    Entry entry = MAP.get(buf);
                    if (entry != null && entry.future == cf) {  // still the latest entry?
                        MAP.put(buf, new Entry(rev, cf, result.log()));
                        if (cf.get() != null) {
                            Log.log(Log.DEBUG, TdCache.class, "Modell ist valide und TransferDescription wird in zusätzlicher, spezieller Map gespeichert");
                            MAP_LAST_VALID.put(buf, cf.get());    
                        }
                    }
                }
            } catch (Exception ex) {
                cf.completeExceptionally(ex); // propagate failure
            }
        });

        // Initial muss log file null gesetzt werden. Es wird im try-Block ersetzt, wenn es vorhanden sein wird.
        MAP.put(buf, new Entry(rev, cf, null));
        return cf;
    }
    
    // Store a TD explicitly (used by CompilerService after compile).
    public static void put(Buffer buf, TransferDescription td, Path log) {
        long rev = buf.getLastModified();
        MAP.put(buf, new Entry(rev, CompletableFuture.completedFuture(td), log));
        if (td != null) {
            MAP_LAST_VALID.put(buf, td);
        }
    }
    
    // Check if buffer is dirty. Handle accordingly.
    public static TransferDescription peek(Buffer buf) {
        Log.log(Log.DEBUG, TdCache.class, "peek()");
        Log.log(Log.DEBUG, TdCache.class, "peek() -> MAP.size(): " + MAP.size());

        Entry e = MAP.get(buf);
        if (e == null || e.revision != buf.getLastModified()) {
            Log.log(Log.DEBUG, TdCache.class, "e == null || e.revision != buf.getLastModified()");
            Log.log(Log.DEBUG, TdCache.class, "e: " + e);
            return null; // stale or missing            
        }

        Log.log(Log.DEBUG, TdCache.class, "Es gibt einen Eintrag für diesen Buffer...");
        Log.log(Log.DEBUG, TdCache.class, "e.revision: " + e.revision);
        
        Future<TransferDescription> f = e.future;
        if (!f.isDone()) {
            Log.log(Log.DEBUG, TdCache.class, "!f.isDone()");
            return null; // still parsing            
        }

        try { // already finished
            Log.log(Log.DEBUG, TdCache.class, "parsing already finished");
            Log.log(Log.DEBUG, TdCache.class, "e.log: " + e.log);
            return f.get(); // quick, no block
        } catch (Exception ex) {
            return null; // failed → treat as absent
        }
    }
    
    public static TransferDescription peekLastValid(Buffer buf) {        
        TransferDescription td = MAP_LAST_VALID.get(buf);
        if (td != null) {
            return td;
        }
        
        // Wenn td null ist, wurde noch gar nie kompiliert oder
        // aber es gab nie ein gültiges Modell. Trotzdem 
        // versuchen wir es noch einmalig zu komplieren.
        CompletableFuture<TransferDescription> cf = get(buf);
        try {
            if (cf.get() != null) {
                return cf.get();
            }            
        } catch (Exception e) {
            cf.completeExceptionally(e);
        }
        
        return null;
    }
        
    public static Path peekLog(Buffer buf) {
        Entry e = MAP.get(buf);
        return (e != null && e.revision == buf.getLastModified())
               ? e.log : null;
    }
        
    private static Model findModelByName(TransferDescription td, String modelName) {
        if (td == null || modelName == null) return null;
        // Search models in this file first
        for (Model m : td.getModelsFromLastFile()) {
            Model found = findInModelAndImports(m, modelName, new HashSet<Model>());
            if (found != null) return found;
        }
        return null; // not found anywhere we can see
    }
    
    private static Model findInModelAndImports(Model m, String name, Set<Model> seen) {
        if (m == null || !seen.add(m)) return null; // avoid cycles
        if (name.equals(m.getName())) return m;

        Model[] imps = m.getImporting();
        if (imps != null) {
            for (Model imp : imps) {
                Model found = findInModelAndImports(imp, name, seen);
                if (found != null) return found;
            }
        }
        return null;
    }

    // Top-level member names of a given model (topics, domains, units, functions, viewables).
    public static List<String> getMembersOfModel(Buffer buf, String modelName) {
        TransferDescription td = peekLastValid(buf);
        if (td == null) return Collections.emptyList();

        Model target = findModelByName(td, modelName);
        if (target == null) return Collections.emptyList();

        ArrayList<String> out = new ArrayList<>();
        collectTopLevelNames(target, out);   // your existing helper
        Collections.sort(out);
        return out;
    }
    
    // Collect only top-level, user-meaningful symbols.
    private static void collectTopLevelNames(Model model, List<String> out) {
        for (Iterator<?> it = model.iterator(); it.hasNext(); ) {
            Object o = it.next();
            if (!(o instanceof Element)) continue;
            Element e = (Element) o;
            String name = e.getName();
            if (name == null) continue;

            if (e instanceof Topic
             || e instanceof Viewable
             || e instanceof Domain
             || e instanceof Unit
             || e instanceof Function) {
                out.add(name);
            }
        }
    }
    
    public static void invalidate(Buffer buf) { 
        MAP_LAST_VALID.remove(buf);
        MAP.remove(buf); 
    }
    
    private static final class Entry {
        final long revision;
        final CompletableFuture<TransferDescription> future;
        final Path log; 
      
        Entry(long revision, CompletableFuture<TransferDescription> future, Path log) {
            this.revision = revision;
            this.future = future;
            this.log = log;
        }
    }
}
