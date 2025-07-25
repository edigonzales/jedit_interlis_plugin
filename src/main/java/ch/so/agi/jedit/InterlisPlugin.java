package ch.so.agi.jedit;

import org.gjt.sp.jedit.*;
import org.gjt.sp.util.Log;

public class InterlisPlugin extends EditPlugin {
    @Override
    public void start() {
        System.err.println("[InterlisPlugin] foo.");
        Log.log(Log.MESSAGE, this, "[InterlisPlugin] started");
    }

    @Override
    public void stop() {
        System.err.println("[InterlisPlugin] bar.");
        Log.log(Log.MESSAGE, this, "[InterlisPlugin] stopped");
    }
    
    public static void compileModelFile() {
        Log.log(Log.MESSAGE, null, "[InterlisPlugin] ****** compiling current file");

    }
}
