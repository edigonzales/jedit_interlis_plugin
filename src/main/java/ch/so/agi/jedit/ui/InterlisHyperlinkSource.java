package ch.so.agi.jedit.ui;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;

import gatchan.jedit.hyperlinks.Hyperlink;
import gatchan.jedit.hyperlinks.HyperlinkSource;

import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.so.agi.jedit.compile.TdCache;

/** Ctrl/Cmd+click a model name to open its .ili file. */
public final class InterlisHyperlinkSource implements HyperlinkSource {

    @Override
    public Hyperlink getHyperlink(Buffer buffer, int offset) {        
        if (buffer == null || buffer.getLength() == 0) return null;

        // identify token under caret
        int start = offset, end = offset;
        while (start > 0) {
            char c = buffer.getText(start - 1, 1).charAt(0);
            if (Character.isLetterOrDigit(c) || c == '_') start--; else break;
        }
        while (end < buffer.getLength()) {
            char c = buffer.getText(end, 1).charAt(0);
            if (Character.isLetterOrDigit(c) || c == '_') end++; else break;
        }
        if (start >= end) return null;

        String token = buffer.getText(start, end - start);
        if (token.isEmpty()) return null;

        // last valid TD only (don’t recompile during hover)
        TransferDescription td = TdCache.peekLastValid(buffer);
        if (td == null) return null;

        String path = resolveModelPath(td, token);
        if (path == null || path.isEmpty()) return null;

        int startLine = buffer.getLineOfOffset(start);
        int endLine   = buffer.getLineOfOffset(end);

        return new IliHyperlink(start, end, startLine, endLine, path);
    }

    /** Search models in this file and their direct imports; return source path. */
    private static String resolveModelPath(TransferDescription td, String name) {
        for (Model m : td.getModelsFromLastFile()) {
            if (name.equals(m.getName())) return m.getFileName();
            Model[] imps = m.getImporting();
            if (imps != null) {
                for (Model imp : imps) {
                    if (imp != null && name.equals(imp.getName()))
                        return imp.getFileName();
                }
            }
        }
        return null;
    }

    /* --- hyperlink impl --- */
    private static final class IliHyperlink implements Hyperlink {
        private final int startOffset, endOffset;
        private final int startLine, endLine;
        private final String path;

        IliHyperlink(int startOffset, int endOffset, int startLine, int endLine, String path) {
            this.startOffset = startOffset;
            this.endOffset   = endOffset;
            this.startLine   = startLine;
            this.endLine     = endLine;
            this.path        = path;
        }

        @Override public int getStartOffset() { return startOffset; }
        @Override public int getEndOffset()   { return endOffset; }
        @Override public int getStartLine()   { return startLine; }
        @Override public int getEndLine()     { return endLine; }
        @Override public String getTooltip()  { return path; }

        @Override
        public void click(View view) {
            jEdit.openFile(view, path);
        }
    }
}
