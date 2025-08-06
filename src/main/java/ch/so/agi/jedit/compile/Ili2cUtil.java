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
    
    public static final class Result {
        private final TransferDescription td;
        private final Path log;
        public Result(TransferDescription td, Path log) {
            this.td = td;
            this.log = log;
        }
        
        public TransferDescription td() { 
            return td; 
        }
        
        public Path log() { 
            return log; 
        }
    }
    
    public static Result run(Buffer buf, View view, boolean keepLog) {
        try {
            Path log = Files.createTempFile("ili2c_", ".log");
            FileLogger flog = new FileLogger(log.toFile(), false);
            EhiLogger.getInstance().addListener(flog);

            EhiLogger.logState("ili2c-" + TransferDescription.getVersion());

            String repo = jEdit.getProperty(P_REPOS, Ili2cSettings.DEFAULT_ILIDIRS);

            Ili2cSettings set = new Ili2cSettings();
            ch.interlis.ili2c.Main.setDefaultIli2cPathMap(set);
            set.setIlidirs(repo);

            Configuration cfg = new Configuration();
            cfg.addFileEntry(new FileEntry(buf.getPath(), FileEntryKind.ILIMODELFILE));
            cfg.setAutoCompleteModelList(true);
            cfg.setGenerateWarnings(true);

            DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date today = new Date();
            String dateOut = dateFormatter.format(today);

            TransferDescription td = ch.interlis.ili2c.Main.runCompiler(cfg, set, null);

            if (td == null) {
                EhiLogger.logError("...compiler run failed " + dateOut);
            } else {
                EhiLogger.logState("...compiler run done " + dateOut);
            }

            EhiLogger.getInstance().removeListener(flog);
            flog.close();

            if (!keepLog) {
                Files.deleteIfExists(log);                
            }

            return new Result(td, log);

        } catch (IOException e) {
            e.printStackTrace();
            GUIUtilities.error(view, "error-creating-log-file", new String[] { e.getMessage() });
            return new Result(null, null);
        }
    }
    
    public static void deleteQuietly(Path p) {
        try { 
            Files.deleteIfExists(p); 
        } catch (IOException ignored) {}
    }
}
