package ch.so.agi.jedit.compile;

import ch.so.agi.jedit.console.ConsoleUtil;
import errorlist.*;

import org.gjt.sp.jedit.*;
import java.nio.file.Path;
import java.util.Map;
import java.util.WeakHashMap;

public final class CompileService {

    private static final Map<View, DefaultErrorSource> MAP = new WeakHashMap<>();
    
    public static void compile(View view, Buffer buffer) {        
        Ili2cUtil.Result res = Ili2cUtil.run(buffer, view, true);
        if (res.log() == null) return;
        
        // 1) log to console
        ConsoleUtil.showLog(view, log);
        
        // 2) feed ErrorList
        DefaultErrorSource es = MAP.computeIfAbsent(view, v -> {
            DefaultErrorSource e = new DefaultErrorSource("INTERLIS", v);
            ErrorSource.registerErrorSource(e);
            return e;
        });
        es.removeFileErrors(buffer.getPath());
        Ili2cLogParser.parse(log, es);

        Ili2cUtil.deleteQuietly(log); // clean tmp file
    }
    
    // called once in InterlisPlugin.stop()
    public static void unregisterAll() {
        MAP.values().forEach(ErrorSource::unregisterErrorSource);
        MAP.clear();
    }
}
