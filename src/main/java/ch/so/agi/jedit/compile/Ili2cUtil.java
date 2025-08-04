package ch.so.agi.jedit.compile;

import ch.ehi.basics.logging.EhiLogger;
import ch.interlis.ili2c.Ili2cSettings;
import ch.interlis.ili2c.config.*;
import ch.interlis.ili2c.metamodel.Ili2cMetaAttrs;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iox_j.logging.FileLogger;

import org.gjt.sp.jedit.*;
import org.gjt.sp.util.Log;

import java.io.IOException;
import java.nio.file.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class Ili2cUtil {
    private static final String P_REPOS = "interlis.repos";

    private Ili2cUtil() {}
    
    public static TransferDescription parse(Buffer buffer) {
        Path logFile = null;
        try {
            logFile = Files.createTempFile("ili2c_", ".log");
            Log.log(Log.MESSAGE, Ili2cUtil.class, "Temp file created: " + logFile.toAbsolutePath());
        } catch (IOException e) {
            Log.log(Log.ERROR, Ili2cUtil.class, "Could not create log file: " + e.getMessage());
            GUIUtilities.error(jEdit.getActiveView(), "error-creating-log-file", new String[] { e.getMessage() });
            return null;
        }

        FileLogger fileLogger = new FileLogger(logFile.toFile(), false);
        EhiLogger.getInstance().addListener(fileLogger);

        EhiLogger.logState("ili2c-" + TransferDescription.getVersion());

        Ili2cMetaAttrs ili2cMetaAttrs = new Ili2cMetaAttrs();

        String ilidirs = jEdit.getProperty(P_REPOS);
        if (ilidirs == null || ilidirs.isEmpty()) {
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
        
        try {
            td.setMetaValue("log", Files.readString(logFile));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            deleteQuietly(logFile);            
        }

        return td;        
    }
    
    public static Path runIli2c(View view, Buffer buffer) {

        Path logFile = null;
        try {
            logFile = Files.createTempFile("ili2c_", ".log");
            Log.log(Log.MESSAGE, Ili2cUtil.class, "Temp file created: " + logFile.toAbsolutePath());
        } catch (IOException e) {
            Log.log(Log.ERROR, Ili2cUtil.class, "Could not create log file: " + e.getMessage());
            GUIUtilities.error(view, "error-creating-log-file", new String[] { e.getMessage() });
            return null;
        }

        FileLogger fileLogger = new FileLogger(logFile.toFile(), false);
        EhiLogger.getInstance().addListener(fileLogger);

        EhiLogger.logState("ili2c-" + TransferDescription.getVersion());

        Ili2cMetaAttrs ili2cMetaAttrs = new Ili2cMetaAttrs();

        String ilidirs = jEdit.getProperty(P_REPOS);
        if (ilidirs == null || ilidirs.isEmpty()) {
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
        
        return logFile;
    }
    
    public static void deleteQuietly(Path p) {
        try { 
            Files.deleteIfExists(p); 
        } catch (IOException ignored) {}
    }
}
