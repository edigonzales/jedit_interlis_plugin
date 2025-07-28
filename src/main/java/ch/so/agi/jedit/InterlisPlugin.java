package ch.so.agi.jedit;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.gjt.sp.util.Log;

import console.ConsolePlugin;
import console.Shell;
import console.SystemShell;
import console.Console;

import ch.ehi.basics.logging.EhiLogger;
import ch.interlis.ili2c.Ili2cException;
import ch.interlis.ili2c.Ili2cFailure;
import ch.interlis.ili2c.Ili2cSettings;
import ch.interlis.ili2c.config.Configuration;
import ch.interlis.ili2c.config.FileEntry;
import ch.interlis.ili2c.config.FileEntryKind;
import ch.interlis.ili2c.metamodel.Ili2cMetaAttrs;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iox_j.logging.FileLogger;

public class InterlisPlugin extends EBPlugin {
    private static final String PROP = "interlis.compileOnSave";
    
    @Override
    public void start() {
        EditBus.addToBus(this);
        Log.log(Log.MESSAGE, this, "[InterlisPlugin] started");
        
        // Scheint sonst nicht zu funktionieren.
//        ActionSet actionSet = this.getPluginJAR().getActionSet();
//        EditAction[] ea = actionSet.getActions();
//        for (int i = 0; i < ea.length; ++i) {
//            String shortcut1 = jEdit.getProperty(ea[i].getName() + ".shortcut");
//            if (shortcut1 != null) {
//                jEdit.getInputHandler().addKeyBinding(shortcut1, ea[i]);
//            }
//            
//            String shortcut2 = jEdit.getProperty(ea[i].getName() + ".shortcut2");
//            if (shortcut2 != null)
//                jEdit.getInputHandler().addKeyBinding(shortcut2, ea[i]);
//        }
    }

    @Override
    public void stop() {
        EditBus.removeFromBus(this);
        Log.log(Log.MESSAGE, this, "[InterlisPlugin] stopped");
    }
    
    public static void toggleCompileOnSave() {
        boolean enabled = !jEdit.getBooleanProperty(PROP, false);
        jEdit.setBooleanProperty(PROP, enabled);
    }
    
    public static boolean isCompileOnSave() {
        return jEdit.getBooleanProperty(PROP, false);
    }
    
    @Override
    public void handleMessage(EBMessage msg) {
        if (!isCompileOnSave()) // user turned the feature off
            return;
        
        if (msg instanceof BufferUpdate) {
            BufferUpdate bu = (BufferUpdate) msg;

            if (bu.getWhat() == BufferUpdate.SAVED) {
                Buffer buf = bu.getBuffer();
                if (buf != null && buf.getName().toLowerCase().endsWith(".ili")) {
                    View view = bu.getView(); // can be null if saved in background
                    compileModelFile(view, buf);
                }
            }
        }
    }

    
    public static void compileModelFile(View view, Buffer buffer) {
        Log.log(Log.MESSAGE, InterlisPlugin.class, "[InterlisPlugin] ****** compiling current file");

        if (!buffer.getName().toLowerCase().endsWith(".ili")) {
            GUIUtilities.error(view, "not-an-ili-file", null);
            return;
        }
        
        Path logFile = null;
        try {
            logFile = Files.createTempFile("ili2c_", ".log");
            Log.log(Log.MESSAGE, InterlisPlugin.class, "Temp file created: " + logFile.toAbsolutePath());
        } catch (IOException e) {
            Log.log(Log.ERROR, InterlisPlugin.class, "Could not create log file: " + e.getMessage());
            GUIUtilities.error(view, "error-creating-log-file", new String[] { e.getMessage() });
            return;
        }

        FileLogger fileLogger = new FileLogger(logFile.toFile(), false);
        EhiLogger.getInstance().addListener(fileLogger);

        EhiLogger.logState("ili2c-"+TransferDescription.getVersion());
        
        Ili2cMetaAttrs ili2cMetaAttrs = new Ili2cMetaAttrs();
        
        String ilidirs = null;
        if (ilidirs == null) {
            ilidirs = Ili2cSettings.DEFAULT_ILIDIRS;
        }

        Ili2cSettings settings = new Ili2cSettings();
        ch.interlis.ili2c.Main.setDefaultIli2cPathMap(settings);
        settings.setIlidirs(ilidirs);

        Configuration config = new Configuration();
        FileEntry file = new FileEntry(buffer.getPath(), FileEntryKind.ILIMODELFILE);
        config.addFileEntry(file);
        config.setAutoCompleteModelList(true);  
        config.setGenerateWarnings(true);
        
        DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date today = new Date();
        String dateOut = dateFormatter.format(today);

        TransferDescription td = ch.interlis.ili2c.Main.runCompiler(config, settings, ili2cMetaAttrs);
        
        if (td == null) {
            EhiLogger.logError("...compiler run failed " + dateOut);
        } else {
            EhiLogger.logState("...compiler run done " + dateOut);
        }

        EhiLogger.getInstance().removeListener(fileLogger);
        fileLogger.close();

        showLogInConsole(view, logFile);

        try {
            Files.deleteIfExists(logFile);
        } catch (IOException e) {
            Log.log(Log.ERROR, InterlisPlugin.class, "Could not delete log file: " + e.getMessage());
        }
    }
    
    private static void showLogInConsole(View view, Path logFile) {
        // 1) ensure the Console dockable is visible
        view.getDockableWindowManager().showDockableWindow("console");
        
        // 2) grab the Console instance
        Console console = ConsolePlugin.getConsole(view);
        if (console == null) {
            GUIUtilities.error(view, "console-missing", null);
            return;
        }
        
        // 3) look up your INTERLIS shell by name
        Shell interlisShell = Shell.getShell("INTERLIS");
        if (interlisShell == null) {
            GUIUtilities.error(view, "shell-not-registered", new String[] { "INTERLIS" });
            return;
        }
        console.setShell(interlisShell);
        console.clear();
        
        // 4) get that shell’s state/output buffer.
        Console.ShellState state = console.getShellState(interlisShell);

        // 5) stream your logfile into it
        try (BufferedReader r = Files.newBufferedReader(logFile)) {
            String line;
            while ((line = r.readLine()) != null) {
                state.print(null, line);
            }
            // signal end‑of‑command (optional)
            state.commandDone();
        } catch (IOException e) {
            state.print(null, "Error reading logfile “" + logFile + "”: " + e.getMessage() + "\n");
        }
    }
}
