package ch.so.agi.jedit.uml;

import java.io.IOException;
import java.net.URI;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.View;
import org.gjt.sp.util.Log;

public class UmlStatic {

    private UmlStatic() {}
    
    public static void show(View view, Buffer buffer) {
        Log.log(Log.MESSAGE, UmlStatic.class, "***** fubar");
        
        try {
            java.awt.Desktop.getDesktop().browse(URI.create("https://tagi.ch"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
