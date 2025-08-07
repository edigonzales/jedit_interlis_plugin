package ch.so.agi.jedit.ui;

import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.buffer.BufferListener;
import org.gjt.sp.jedit.buffer.JEditBuffer;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.gjt.sp.util.Log;

import javax.swing.SwingUtilities;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Inserts “END &lt;name&gt;;” automatically after MODEL/TOPIC/CLASS lines. */
public final class AutoCloser implements EBComponent, BufferListener {

    private static final Pattern HEADER = Pattern.compile("\\b(MODEL|TOPIC|CLASS)\\s+(\\w+)\\s*$");

    /* ------------------------------------------------------------------ */
    /** Call once from InterlisPlugin.start() */
    public static void install() {
        AutoCloser ac = new AutoCloser();
        EditBus.addToBus(ac);

        // attach to all buffers already open
        for (Buffer b = jEdit.getFirstBuffer(); b != null; b = b.getNext())
            b.addBufferListener(ac);

        Log.log(Log.MESSAGE, AutoCloser.class, "Interlis AutoCloser active");
    }

    /* ------------------------------------------------------------------
     * EBComponent: register for newly loaded buffers
     * ------------------------------------------------------------------ */
    @Override
    public void handleMessage(EBMessage msg) {
        if (msg instanceof BufferUpdate) {
            BufferUpdate bu = (BufferUpdate) msg;
            if (bu.getWhat() == BufferUpdate.LOADED)
                bu.getBuffer().addBufferListener(this);
        }
    }

    /* ------------------------------------------------------------------
     * BufferListener: react once the '=' has been inserted
     * ------------------------------------------------------------------ */
    @Override
    public void contentInserted(JEditBuffer b, int lineWithEq, int offset, int numLines, int length) {
        if (length != 1) // exactly one char
            return;
        if (b.getText(offset, 1).charAt(0) != '=') // not '='
            return;

        /*
         * ---------------------------------------------------------- * Find the nearest
         * non-empty line at or above current line *
         * ----------------------------------------------------------
         */
        int line = lineWithEq;
        String candidate = null;

        while (line >= 0) {
            int ls = b.getLineStartOffset(line);
            int le = b.getLineEndOffset(line);
            String text = b.getText(ls, le - ls).trim();

            boolean sameLine = (line == lineWithEq);
            if (sameLine) {
                // only the text BEFORE '=' matters on that line
                text = b.getText(ls, offset - ls).trim();
            }

            if (!text.isEmpty()) {              // found potential header
                candidate = text;
                break;
            }
            line--;                             // keep searching higher
        }

        if (candidate == null) return;          // nothing found

        Matcher m = HEADER.matcher(candidate);
        if (!m.matches())
            return; // not a header

        String name = m.group(2);
        String insert = "\nEND " + name + ";";

        SwingUtilities.invokeLater(() -> b.insert(offset + 1, insert)); // AFTER the '='
    }
    
    /* ------------------------------------------------------------------
     * Unused BufferListener callbacks
     * ------------------------------------------------------------------ */
    @Override public void contentRemoved(JEditBuffer b,int sL,int o,int nL,int len){}
    @Override public void foldHandlerChanged(JEditBuffer b){}
    @Override public void foldLevelChanged(JEditBuffer b,int line, int arg1){}
    @Override public void preContentInserted(JEditBuffer b,int sL,int o,int nL,int len){}
    @Override public void preContentRemoved(JEditBuffer b,int sL,int o,int nL,int len){}
    @Override public void transactionComplete(JEditBuffer b){}
    @Override public void bufferLoaded(JEditBuffer b){}
}
