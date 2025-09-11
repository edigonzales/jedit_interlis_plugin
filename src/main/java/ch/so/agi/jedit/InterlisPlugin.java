package ch.so.agi.jedit;

import javax.swing.SwingWorker;

import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.gjt.sp.util.Log;

import ch.so.agi.jedit.compile.CompileService;
import ch.so.agi.jedit.ui.AutoCloser;
import ch.so.agi.jedit.uml._static.LivePreview;

public class InterlisPlugin extends EBPlugin {
    private static final String PROP_COMPILE_ON_SAVE = "interlis.compileOnSave";
        
    @Override
    public void start() {
        EditBus.addToBus(this);
        AutoCloser.install();
        SwingWorker<Object, Void> sw = new SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() throws Exception {
                ModelDiscoveryService.initialize();                
                return null;
            }
        };
        sw.execute();
        Log.log(Log.MESSAGE, this, "[InterlisPlugin] started");
    }

    @Override
    public void stop() {
        EditBus.removeFromBus(this);
        CompileService.unregisterAll();
        LivePreview.get().stop();
        Log.log(Log.MESSAGE, this, "[InterlisPlugin] stopped");
    }
     
    @Override public void handleMessage(EBMessage msg) {
        if (!compileOnSave()) {
            return;            
        }

        if (msg instanceof BufferUpdate) {
            BufferUpdate bu = (BufferUpdate) msg;

            if (bu.getWhat() == BufferUpdate.SAVED) {
                Buffer b = bu.getBuffer();
                if (b != null && b.getName().toLowerCase().endsWith(".ili")) {
                    CompileService.compile(bu.getView(), b);   
                }                    
            }
        }        
    }

    public static void toggleCompileOnSave() {
        jEdit.setBooleanProperty(PROP_COMPILE_ON_SAVE, !compileOnSave());
    }
    
    public static boolean compileOnSave() {
        return jEdit.getBooleanProperty(PROP_COMPILE_ON_SAVE, false);
    }
}
