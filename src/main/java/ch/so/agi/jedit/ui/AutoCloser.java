package ch.so.agi.jedit.ui;

import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.buffer.BufferListener;
import org.gjt.sp.jedit.buffer.JEditBuffer;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.gjt.sp.util.Log;

import javax.swing.SwingUtilities;
import java.util.*;

/**
 * Auto-inserts closing text when '=' is typed after valid INTERLIS headers.
 * Supports: CLASS, STRUCTURE (same rules), TOPIC (ABSTRACT/FINAL, optional EXTENDS),
 * and VIEW TOPIC (adds a "DEPENDS ON" line). Case-sensitive. No regex, no AST.
 */
public final class AutoCloser implements EBComponent, BufferListener {

    private static final int LOOKBACK_CHARS = 1200;

    public static void install() {
        AutoCloser ac = new AutoCloser();
        EditBus.addToBus(ac);
        for (Buffer b = jEdit.getFirstBuffer(); b != null; b = b.getNext())
            b.addBufferListener(ac);
        Log.log(Log.MESSAGE, AutoCloser.class, "Interlis AutoCloser active (CLASS/STRUCTURE/TOPIC/VIEW TOPIC)");
    }

    @Override
    public void handleMessage(EBMessage msg) {
        if (msg instanceof BufferUpdate) {
            BufferUpdate bu = (BufferUpdate) msg;
            if (bu.getWhat() == BufferUpdate.LOADED)
                bu.getBuffer().addBufferListener(this);
        }
    }

    @Override
    public void contentInserted(JEditBuffer b, int lineWithEq, int offset, int numLines, int length) {
        if (length != 1) return;
        if (b.getText(offset, 1).charAt(0) != '=') return;

        int tailStart = Math.max(0, offset - LOOKBACK_CHARS);
        String tail = b.getText(tailStart, offset - tailStart);

        List<Token> toks = Lexer.lex(tail);
        ParseResult res = Parser.parseHeaderEndingAtTail(toks);
        if (res == null) return;

        int keywordAbs = tailStart + res.keywordPosInTail;
        String indent   = leadingIndent((Buffer) b, keywordAbs);

        final Buffer buf = (Buffer) b;

        final boolean isViewTopic = (res.kind == Kind.VIEW_TOPIC);
        final String insert;
        final int caretPos;

        // Build text + compute caret position
        if (isViewTopic) {
            // Add a trailing space after "DEPENDS ON " and place caret there
            insert   = "\n" + indent + "DEPENDS ON "   // caret goes here
                     + "\n" + indent                   // one indented blank line
                     + "\n" + indent + "END " + res.name + ";";
            // Caret: start of "DEPENDS ON " line + its length
            final int depLineStart = buf.getLineStartOffset(lineWithEq + 1);
            caretPos = depLineStart + indent.length() + "DEPENDS ON ".length();
        } else {
            // Normal headers: indented blank line, then END
            insert   = "\n" + indent
                     + "\n" + indent + "END " + res.name + ";";
            // Caret at the start of the indented blank line
            final int blankLineStart = buf.getLineStartOffset(lineWithEq + 1);
            caretPos = blankLineStart + indent.length();
        }

        SwingUtilities.invokeLater(() -> {
            // Insert text after '='
            buf.insert(offset + 1, insert);

            // Nudge caret after views refresh
            SwingUtilities.invokeLater(() -> {
                for (View v : jEdit.getViews()) {
                    if (v.getBuffer() == buf) {
                        v.getTextArea().setCaretPosition(caretPos);
                        break;
                    }
                }
            });
        });



    }

    @Override public void contentRemoved(JEditBuffer b,int sL,int o,int nL,int len){}
    @Override public void foldHandlerChanged(JEditBuffer b){}
    @Override public void foldLevelChanged(JEditBuffer b,int line,int arg1){}
    @Override public void preContentInserted(JEditBuffer b,int sL,int o,int nL,int len){}
    @Override public void preContentRemoved(JEditBuffer b,int sL,int o,int nL,int len){}
    @Override public void transactionComplete(JEditBuffer b){}
    @Override public void bufferLoaded(JEditBuffer b){}

    // ------------------------------- LEXER ----------------------------------

    private enum T { CLASS, STRUCTURE, TOPIC, VIEW, EXTENDS, LPAREN, RPAREN, COMMA, IDENT }

    private static final class Token {
        final T t;
        final String text; // only for IDENT
        final int pos;     // start offset in the tail string
        Token(T t, String text, int pos) { this.t = t; this.text = text; this.pos = pos; }
        Token(T t, int pos)               { this(t, null, pos); }
    }

    private static final class Lexer {
        static List<Token> lex(String s) {
            ArrayList<Token> out = new ArrayList<>();
            int i = 0, n = s.length();
            while (i < n) {
                char c = s.charAt(i);

                // whitespace
                if (Character.isWhitespace(c)) { i++; continue; }

                // // line comment
                if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                    i += 2;
                    while (i < n && s.charAt(i) != '\n') i++;
                    continue;
                }
                // /* block comment */
                if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                    i += 2;
                    while (i + 1 < n && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) i++;
                    if (i + 1 < n) i += 2;
                    continue;
                }

                // punctuation
                if (c == '(') { out.add(new Token(T.LPAREN, i)); i++; continue; }
                if (c == ')') { out.add(new Token(T.RPAREN, i)); i++; continue; }
                if (c == ',') { out.add(new Token(T.COMMA,  i)); i++; continue; }

                // identifier / keyword (case-sensitive)
                if (Character.isLetter(c) || c == '_') {
                    int j = i + 1;
                    while (j < n) {
                        char d = s.charAt(j);
                        if (Character.isLetterOrDigit(d) || d == '_') j++; else break;
                    }
                    String w = s.substring(i, j);
                    if      ("CLASS".equals(w))     out.add(new Token(T.CLASS,     i));
                    else if ("STRUCTURE".equals(w)) out.add(new Token(T.STRUCTURE, i));
                    else if ("TOPIC".equals(w))     out.add(new Token(T.TOPIC,     i));
                    else if ("VIEW".equals(w))      out.add(new Token(T.VIEW,      i));
                    else if ("EXTENDS".equals(w))   out.add(new Token(T.EXTENDS,   i));
                    else                            out.add(new Token(T.IDENT, w,  i));
                    i = j;
                    continue;
                }

                // skip anything else
                i++;
            }
            return out;
        }
    }

    // ------------------------------- PARSER ---------------------------------

    private enum Kind { CLASS, STRUCTURE, TOPIC, VIEW_TOPIC }

    private static final class ParseResult {
        final Kind kind;
        final String name;
        final int keywordPosInTail;
        ParseResult(Kind k, String name, int pos) { this.kind = k; this.name = name; this.keywordPosInTail = pos; }
    }

    private static final class Parser {

        private static final Set<String> FLAGS_CLASS = set("ABSTRACT","EXTENDED","FINAL");
        private static final Set<String> FLAGS_STRUCTURE = FLAGS_CLASS;
        private static final Set<String> FLAGS_TOPIC = set("ABSTRACT","FINAL"); // no EXTENDED for TOPIC

        static ParseResult parseHeaderEndingAtTail(List<Token> toks) {
            if (toks.isEmpty()) return null;

            // Try VIEW TOPIC first (most specific)
            ParseResult r = tryViewTopicSuffix(toks);
            if (r != null) return r;

            // Then TOPIC
            r = tryHeaderSuffix(toks, T.TOPIC, FLAGS_TOPIC, Kind.TOPIC);
            if (r != null) return r;

            // Then CLASS / STRUCTURE
            r = tryHeaderSuffix(toks, T.CLASS, FLAGS_CLASS, Kind.CLASS);
            if (r != null) return r;

            r = tryHeaderSuffix(toks, T.STRUCTURE, FLAGS_STRUCTURE, Kind.STRUCTURE);
            if (r != null) return r;

            return null;
        }

        /** VIEW TOPIC IDENT   (must end exactly at tail end) */
        private static ParseResult tryViewTopicSuffix(List<Token> toks) {            
            int n = toks.size();
            for (int i = n - 1; i >= 1; i--) {
                Token tView = toks.get(i - 1);
                Token tTopic = toks.get(i);
                if (tView.t != T.VIEW || tTopic.t != T.TOPIC) continue;
                int p = i + 1;
                if (p >= n || toks.get(p).t != T.IDENT) continue;
                String name = toks.get(p).text;
                p++;
                if (p == n) return new ParseResult(Kind.VIEW_TOPIC, name, tView.pos);
            }
            return null;
        }

        /** KEYWORD [ '(' flags ')' ] IDENT [ EXTENDS IDENT ]   (consume to end) */
        private static ParseResult tryHeaderSuffix(List<Token> toks, T keyword, Set<String> allowedFlags, Kind kind) {
            int n = toks.size();
            for (int i = n - 1; i >= 0; i--) {
                Token kw = toks.get(i);
                if (kw.t != keyword) continue;
                int p = i + 1;

                // optional flags
                if (p < n && toks.get(p).t == T.LPAREN) {
                    p++;
                    if (p >= n || !isAllowedFlag(toks.get(p), allowedFlags)) continue;
                    p++;
                    while (p < n && toks.get(p).t == T.COMMA) {
                        p++;
                        if (p >= n || !isAllowedFlag(toks.get(p), allowedFlags)) { p = -1; break; }
                        p++;
                    }
                    if (p < 0 || p >= n || toks.get(p).t != T.RPAREN) continue;
                    p++;
                }

                // name
                if (p >= n || toks.get(p).t != T.IDENT) continue;
                String name = toks.get(p).text;
                p++;

                // optional EXTENDS <ident>
                if (p < n && toks.get(p).t == T.EXTENDS) {
                    p++;
                    if (p >= n || toks.get(p).t != T.IDENT) continue;
                    p++;
                }

                if (p == n) return new ParseResult(kind, name, kw.pos);
            }
            return null;
        }

        private static boolean isAllowedFlag(Token tk, Set<String> allowed) {
            return tk.t == T.IDENT && allowed.contains(tk.text);
        }

        private static Set<String> set(String... s) {
            return new HashSet<>(Arrays.asList(s));
        }
    }

    // --------------------------- INDENT UTILS --------------------------------

    private static String leadingIndent(Buffer b, int absPos) {
        int line = b.getLineOfOffset(absPos);
        int lineStart = b.getLineStartOffset(line);
        int p = lineStart;
        int limit = Math.min(absPos, b.getLength());
        while (p < limit) {
            char ch = b.getText(p, 1).charAt(0);
            if (ch == ' ' || ch == '\t') { p++; continue; }
            break;
        }
        return b.getText(lineStart, p - lineStart);
    }
}
