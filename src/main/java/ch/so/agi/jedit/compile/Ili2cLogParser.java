package ch.so.agi.jedit.compile;

import errorlist.*;
import org.gjt.sp.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Ili2cLogParser {
    
    /**  Example line
     *      Error: /path/to/file.ili:113:expecting "END", found 'adsf'
     *
     *  Groups:
     *    1 = "Error" | "Warning"
     *    2 = file path
     *    3 = line number  (base‑1 in log)
     *    4 = message
     */
    private static final Pattern LINE =
        Pattern.compile("^(Error|Warning):\\s+([^:]+):(\\d+):\\s*(.*)$");

    private Ili2cLogParser() { /* no instance */ }
    
    /**
     * Parse <code>logFile</code> and pump diagnostics into the given ErrorSource.
     *
     * @return number of errors + warnings added
     */
    
    public static int parse(Path logFile, DefaultErrorSource es)
    {
        int count = 0;

        // Collect uniques in a LinkedHashMap (preserves read‑order)
        Map<String, DefaultErrorSource.DefaultError> byKey = new LinkedHashMap<>();

        try (BufferedReader in = Files.newBufferedReader(logFile)) {
            String line;
            while ((line = in.readLine()) != null) {

                Matcher m = LINE.matcher(line);
                if (!m.matches())
                    continue; // skip Info: lines etc.

                if (line.contains("compiler run"))
                    continue; // skip summary line

                boolean isWarn = "Warning".equals(m.group(1));
                String  file   = m.group(2).trim();
                int     lineNr = Integer.parseInt(m.group(3)) - 1; // 0‑based
                String  msg    = m.group(4).trim();

                String key = file + ':' + lineNr; // ignore start/end
                DefaultErrorSource.DefaultError de = byKey.get(key);

                if (de == null) {// first message on this line
                    de = new DefaultErrorSource.DefaultError(
                            es,
                            isWarn ? ErrorSource.WARNING : ErrorSource.ERROR,
                            file, lineNr,
                            0, 0, // highlight whole line
                            msg);
                    byKey.put(key, de);
                } else { // second msg on same line
                    de.addExtraMessage(msg);
                }
                ++count;
            }
        }
        catch (IOException ex) {
            Log.log(Log.ERROR, Ili2cLogParser.class,
                    "Cannot read ili2c log: " + logFile, ex);
        }

        // Add them to ErrorSource in **reverse** order 
        List<DefaultErrorSource.DefaultError> all =
            new ArrayList<>(byKey.values());
        Collections.reverse(all);
        for (DefaultErrorSource.DefaultError de : all) {
            es.addError(de);
        }

        return count; // number of raw diagnostics parsed
    }
}
