package ch.so.agi.jedit.compile;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.so.agi.jedit.console.ConsoleUtil;
import ch.so.agi.jedit.uml._static.UmlStatic;
import errorlist.*;
import sidekick.SideKickPlugin;

import org.gjt.sp.jedit.*;
import org.gjt.sp.util.Log;

import java.nio.file.Path;
import java.util.Map;
import java.util.WeakHashMap;

import javax.swing.SwingWorker;

public final class CompileService {

    private static final Map<View, DefaultErrorSource> ERRMAP = new WeakHashMap<>();
    
    private CompileService() {}
    
    public static void compile(View view, Buffer buffer) {  
        
        // A buffer is considered "dirty" when it has been modified but those changes have not yet been saved to disk.
        Log.log(Log.DEBUG, CompileService.class, "Is buffer dirty? " + buffer.isDirty());
        
        if (!buffer.isDirty()) {
            TransferDescription td = TdCache.peek(buffer);
            Path log = TdCache.peekLog(buffer);

            Log.log(Log.DEBUG, CompileService.class, "Buffer must not be dirty? " + buffer.isDirty());
            Log.log(Log.DEBUG, CompileService.class, "log: " + log);
            Log.log(Log.DEBUG, CompileService.class, "td: " + td);
            
            if (td != null && log != null) {
                Log.log(Log.DEBUG, CompileService.class, "log und td ungleich null");
                
                rebuildUiFromCachedTd(view, buffer, td, log);
                ConsoleUtil.showLog(view, log); 
                updateErrorList(view, buffer, log); // keep diagnostics visible
                UmlStatic.show(view, buffer);
                return;
            }
        }
        
        new SwingWorker<Ili2cUtil.Result,Void>() {
            
            @Override protected Ili2cUtil.Result doInBackground() {
                Log.log(Log.DEBUG, CompileService.class, "SwingWorker: doInBackground");
                return Ili2cUtil.run(buffer, view, true);
            }

            @Override protected void done() {
                Log.log(Log.DEBUG, CompileService.class, "SwingWorker: done");
                try {
                    Ili2cUtil.Result res = get(); // back on EDT
                    if (res.log() == null) {
                        Log.log(Log.DEBUG, CompileService.class, "SwingWorker: done -> log == null");
                        return;
                    }

                    ConsoleUtil.showLog(view, res.log());
                    updateErrorList(view, buffer, res.log());
                    cacheTd(buffer, res.td(), res.log());
                    SideKickPlugin.parse(view, true);
                    UmlStatic.show(view, buffer);          
                } catch (Exception e) {
                    Log.log(Log.ERROR, this, e);
                }
            }
        }.execute();
    }
    
    public static void unregisterAll() {
        ERRMAP.values().forEach(ErrorSource::unregisterErrorSource);
        ERRMAP.clear();
    }
    
    private static void updateErrorList(View view, Buffer buffer, Path log) {
        DefaultErrorSource es = ERRMAP.computeIfAbsent(view, v -> {
            DefaultErrorSource e = new DefaultErrorSource("INTERLIS", v);
            ErrorSource.registerErrorSource(e);
            return e;
        });
        es.removeFileErrors(buffer.getPath());
        Ili2cLogParser.parse(log, es);
    }
    
    private static void cacheTd(Buffer buffer, TransferDescription td, Path log) {
        if (td != null) {
            TdCache.put(buffer, td, log);
        }
    }
    
    private static void rebuildUiFromCachedTd(View v, Buffer buf, TransferDescription td, Path log) {
        Log.log(Log.DEBUG, CompileService.class, "rebuildUiFromCachedTd");
        TdCache.put(buf, td, log); // ensure cache revision matches
        SideKickPlugin.parse(v, true); // refresh outline immediately
    }
}
