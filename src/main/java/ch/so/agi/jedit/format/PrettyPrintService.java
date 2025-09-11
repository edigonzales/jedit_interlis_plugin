package ch.so.agi.jedit.format;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import org.gjt.sp.jedit.*;
import org.gjt.sp.util.Log;

import ch.interlis.ili2c.Ili2cException;
import ch.interlis.ili2c.Ili2cSettings;
import ch.interlis.ili2c.config.Configuration;
import ch.interlis.ili2c.generator.Interlis2Generator;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ilirepository.IliManager;
import ch.so.agi.jedit.compile.TdCache;

public class PrettyPrintService {
    private static final String P_REPOS = "interlis.repos";

    private PrettyPrintService() {}
    
    public static void prettyPrint(View view, Buffer buffer) {
        if (buffer.isDirty()) {
            GUIUtilities.error(view, "buffer-not-saved", null);
            return;
        }
        
        Path outputFile;
        OutputStreamWriter out;
        try {
            outputFile = Files.createTempFile("", ".ili");            
            out = new OutputStreamWriter(new FileOutputStream(outputFile.toAbsolutePath().toString()), "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
            GUIUtilities.error(view, "temp-file-not-created", null);
            return;
        }
        
        try {
            TransferDescription desc = new TransferDescription();
            TransferDescription td = TdCache.get(buffer).get();
            for (Model model : td.getModelsFromLastFile()) {
                desc.add(model);
            }
            
            Interlis2Generator gen = new Interlis2Generator();
            gen.generate(out, desc, false); // emitPredefined = config.isIncPredefModel() ?
            
            String content = Files.readString(outputFile);

            EditPane pane = view.getEditPane();
            int caret = pane.getTextArea().getCaretPosition();

            // replace entire buffer content in one compound edit
            buffer.beginCompoundEdit();
            buffer.remove(0, buffer.getLength());
            buffer.insert(0, content);
            buffer.endCompoundEdit();

            pane.getTextArea().setCaretPosition(Math.min(caret, buffer.getLength()));
            
            Log.log(Log.DEBUG, PrettyPrintService.class, "Pretty print done (" + buffer.getName() + ")");
            
            return;
        } catch (IOException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
            GUIUtilities.error(view, "error-on-prettyprint", null);
            return;
        } 
    }
}
