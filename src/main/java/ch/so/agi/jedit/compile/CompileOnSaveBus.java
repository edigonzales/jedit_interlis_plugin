package ch.so.agi.jedit.compile;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.EBComponent;
import org.gjt.sp.jedit.EBMessage;
import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.msg.BufferUpdate;

public class CompileOnSaveBus implements EBComponent {
    private static CompileOnSaveBus INSTANCE;

    private static final String PROP_COMPILE_ON_SAVE = "interlis.compileOnSave";

    public static void install() {
        if (INSTANCE == null) {
            INSTANCE = new CompileOnSaveBus();
            EditBus.addToBus(INSTANCE);
        }
    }

    public static void uninstall() {
        if (INSTANCE != null) {
            EditBus.removeFromBus(INSTANCE);
            INSTANCE = null;
        }
    }

    public static boolean compileOnSave() {
        return jEdit.getBooleanProperty(PROP_COMPILE_ON_SAVE, false);
    }

    private CompileOnSaveBus() {}
    
    @Override
    public void handleMessage(EBMessage msg) {
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
}
