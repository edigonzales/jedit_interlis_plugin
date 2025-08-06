package ch.so.agi.jedit.compile;

import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.so.agi.jedit.console.ConsoleUtil;
import errorlist.*;

import org.gjt.sp.jedit.*;
import java.util.Map;
import java.util.WeakHashMap;

public final class CompileService {

    private static final Map<View, DefaultErrorSource> ERRMAP = new WeakHashMap<>();
    
    private CompileService() {}
    
    public static void compile(View view, Buffer buffer) {        
        Ili2cUtil.Result res = Ili2cUtil.run(buffer, view, true); // keep log
        if (res.log() == null) { // fatal 
            return;   
        }      
        
        ConsoleUtil.showLog(view, res.log());
        
        // 2) feed ErrorList
        DefaultErrorSource es = ERRMAP.computeIfAbsent(view, v -> {
            DefaultErrorSource e = new DefaultErrorSource("INTERLIS", v);
            ErrorSource.registerErrorSource(e);
            return e;
        });
        es.removeFileErrors(buffer.getPath());
        Ili2cLogParser.parse(res.log(), es);
        
        
        // 3 — cache TD + ~~model map for hyperlinks~~
        TransferDescription td = res.td();
        if (td != null) {
            TdCache.put(buffer, td);

            // TODO
//            // OPTIONAL: provide data for Ctrl-click hyperlinks
//            for (Model m : td.getModels())
//                InterlisHyperlinkSource.putModel(
//                        m.getName(),
//                        m.getSourceFile().getAbsolutePath());
        }  
    }
    
    public static void unregisterAll() {
        ERRMAP.values().forEach(ErrorSource::unregisterErrorSource);
        ERRMAP.clear();
    }
}
