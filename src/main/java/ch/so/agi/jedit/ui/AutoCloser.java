package ch.so.agi.jedit.ui;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.EBComponent;
import org.gjt.sp.jedit.EBMessage;
import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.buffer.BufferListener;
import org.gjt.sp.jedit.buffer.JEditBuffer;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.gjt.sp.util.Log;

import javax.swing.SwingUtilities;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Auto-inserts boilerplate when '=' is typed after INTERLIS headers.
 * Tokenizer + hand-rolled parser (no regex, no AST).
 * Supports:
 *  - CLASS / STRUCTURE (ABSTRACT/EXTENDED/FINAL, optional EXTENDS)
 *  - TOPIC (ABSTRACT/FINAL, optional EXTENDS; no EXTENDED)
 *  - VIEW TOPIC (adds "DEPENDS ON", caret after it)
 *  - MODEL (inserts banner + meta, rewrites header, adds blank line + END)
 */
public final class AutoCloser implements EBComponent, BufferListener {

    private static final int LOOKBACK_CHARS = 1200;
    private static final DateTimeFormatter ISO_DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    /** Call once from InterlisPlugin.start() */
    public static void install() {
        AutoCloser ac = new AutoCloser();
        EditBus.addToBus(ac);
        // attach to already-open buffers
        for (Buffer b = jEdit.getFirstBuffer(); b != null; b = b.getNext())
            b.addBufferListener(ac);
        Log.log(Log.MESSAGE, AutoCloser.class,
                "Interlis AutoCloser active (CLASS/STRUCTURE/TOPIC/VIEW TOPIC/MODEL)");
    }

    /* ------------------------------------------------------------------ */
    /* EBComponent: register for newly loaded buffers                      */
    /* ------------------------------------------------------------------ */
    @Override
    public void handleMessage(EBMessage msg) {
        if (msg instanceof BufferUpdate) {
            BufferUpdate bu = (BufferUpdate) msg;
            if (bu.getWhat() == BufferUpdate.LOADED) {
                bu.getBuffer().addBufferListener(this);
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* BufferListener: react once the '=' has been inserted                */
    /* ------------------------------------------------------------------ */
    @Override
    public void contentInserted(JEditBuffer b,
                                int lineWithEq,
                                int offset,
                                int numLines,
                                int length) {
        // Only react to a single '=' insertion
        if (length != 1) return;
        if (b.getText(offset, 1).charAt(0) != '=') return;

        final Buffer buf = (Buffer) b;

        // Slice the tail BEFORE '='
        final int tailStart = Math.max(0, offset - LOOKBACK_CHARS);
        final String tail = b.getText(tailStart, offset - tailStart);

        // Tokenize & parse
        final List<Token> toks = Lexer.lex(tail);
        final ParseResult res = Parser.parseHeaderEndingAtTail(toks);
        if (res == null) return;

        // Indentation taken from the header line (line of the keyword)
        final int keywordAbs = tailStart + res.keywordPosInTail;
        final String indent = leadingIndent(buf, keywordAbs);

        // MODEL has a dedicated template/rewrite
        if (res.kind == Kind.MODEL) {
            applyModelTemplate(buf, lineWithEq, offset, tailStart, res, indent);
            return;
        }

        // VIEW TOPIC: add "DEPENDS ON", caret after it; then blank line + END
        if (res.kind == Kind.VIEW_TOPIC) {
            final String insert = "\n" + indent + "DEPENDS ON "
                                + "\n" + indent
                                + "\n" + indent + "END " + res.name + ";";

            SwingUtilities.invokeLater(() -> {
                buf.insert(offset + 1, insert);
                // set caret after views refresh
                SwingUtilities.invokeLater(() -> {
                    final int depLine = Math.min(buf.getLineCount() - 1, lineWithEq + 1);
                    final int depLineStart = buf.getLineStartOffset(depLine);
                    final int caretPos = depLineStart + indent.length() + "DEPENDS ON ".length();
                    moveCaretTo(buf, caretPos);
                });
            });
            return;
        }

        // CLASS / STRUCTURE / TOPIC: blank indented line, then END; caret on that blank line.
        final String insert = "\n" + indent
                            + "\n" + indent + "END " + res.name + ";";

        SwingUtilities.invokeLater(() -> {
            buf.insert(offset + 1, insert);
            SwingUtilities.invokeLater(() -> {
                final int blankLine = Math.min(buf.getLineCount() - 1, lineWithEq + 1);
                final int caretPos = buf.getLineStartOffset(blankLine) + indent.length();
                moveCaretTo(buf, caretPos);
            });
        });
    }

    /* =============================== MODEL =============================== */

    /**
     * Inserts the banner above the MODEL header, rewrites the header tail, and
     * places the caret on the indented blank line after VERSION.
     */
    private static void applyModelTemplate(Buffer buf,
                                           int headerLine,
                                           int eqOffset,
                                           int tailStart,
                                           ParseResult res,
                                           String indent) {
        final String today = LocalDate.now(ZURICH).format(ISO_DAY);

        // 1) Banner + meta (not indented, as per sample)
        final String banner =
                "/** !!------------------------------------------------------------------------------\n" +
                " * !! Version    | wer | Änderung\n" +
                " * !!------------------------------------------------------------------------------\n" +
                " * !! " + today + " | abr  | Initalversion\n" +
                " * !!==============================================================================\n" +
                " */\n" +
                "!!@ technicalContact=mailto:acme@example.com\n" +
                "!!@ furtherInformation=https://example.com/path/to/information\n" +
                "!!@ title=\"a title\"\n" +
                "!!@ shortDescription=\"a short description\"\n" + 
                "!!@ tags=\"foo,bar,fubar\"\n";

        final int headerLineStart = buf.getLineStartOffset(headerLine);

        // 2) Find absolute offsets around the model name
        final int nameAbsStart = tailStart + res.namePosInTail;
        final int nameAbsEnd   = nameAbsStart + res.nameLen;
        // Remove everything after the name up to and including '='
        final int removeLen    = Math.max(0, (eqOffset - nameAbsEnd) + 1);

        // 3) Header tail rewrite + END
        final String mid = " (de)\n"
                + indent + "  AT \"https://example.com\"\n"
                + indent + "  VERSION \"" + today + "\"\n"
                + indent + "  =\n"
                + indent + "\n"
                + indent + "END " + res.name + ";";
        
        final int bannerLen   = banner.length();
        final int removeStart = nameAbsEnd + bannerLen;
        final int insertPos   = removeStart;

        SwingUtilities.invokeLater(() -> {
            // Insert banner above the header
            buf.insert(headerLineStart, banner);

            // Rewrite the header tail after the model name
            if (removeLen > 0) {
                buf.remove(removeStart, removeLen);
            }
            buf.insert(insertPos, mid);

            // Caret → start of the indented blank line after VERSION
            SwingUtilities.invokeLater(() -> {
                final String afterVersionHead =
                        " (de)\n" + indent + "  AT \"https://example.com\"\n" +
                        indent + "  VERSION \"" + today + "\"\n" +
                        indent + "  =\n";
                final int caretPos = Math.min(buf.getLength(),
                        insertPos + afterVersionHead.length() + indent.length());
                moveCaretTo(buf, caretPos);
            });
        });
    }

    /* ============================ caret helper ============================ */

    private static void moveCaretTo(Buffer buf, int absolutePos) {
        for (View v : jEdit.getViews()) {
            if (v.getBuffer() == buf) {
                v.getTextArea().setCaretPosition(Math.min(absolutePos, buf.getLength()));
                break;
            }
        }
    }

    /* ================================ LEXER =============================== */

    private enum T { CLASS, STRUCTURE, TOPIC, VIEW, MODEL, EXTENDS, LPAREN, RPAREN, COMMA, IDENT }

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

                // line comment //
                if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                    i += 2; while (i < n && s.charAt(i) != '\n') i++; continue;
                }
                // block comment /* ... */
                if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                    i += 2; while (i + 1 < n && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) i++;
                    if (i + 1 < n) i += 2; continue;
                }

                // punctuation
                if (c == '(') { out.add(new Token(T.LPAREN, i)); i++; continue; }
                if (c == ')') { out.add(new Token(T.RPAREN, i)); i++; continue; }
                if (c == ',') { out.add(new Token(T.COMMA,  i)); i++; continue; }

                // identifier or keyword (case-sensitive)
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
                    else if ("MODEL".equals(w))     out.add(new Token(T.MODEL,     i));
                    else if ("EXTENDS".equals(w))   out.add(new Token(T.EXTENDS,   i));
                    else                            out.add(new Token(T.IDENT, w,  i));
                    i = j; continue;
                }

                // skip anything else
                i++;
            }
            return out;
        }
    }

    /* =============================== PARSER =============================== */

    private enum Kind { CLASS, STRUCTURE, TOPIC, VIEW_TOPIC, MODEL }

    private static final class ParseResult {
        final Kind kind;
        final String name;
        final int keywordPosInTail;
        final int namePosInTail; // for MODEL (and others filled for convenience)
        final int nameLen;
        ParseResult(Kind k, String name, int kwPos, int namePos, int nameLen) {
            this.kind = k; this.name = name; this.keywordPosInTail = kwPos;
            this.namePosInTail = namePos; this.nameLen = nameLen;
        }
    }

    private static final class Parser {

        private static final Set<String> FLAGS_CLASS     = set("ABSTRACT","EXTENDED","FINAL");
        private static final Set<String> FLAGS_STRUCTURE = FLAGS_CLASS;
        private static final Set<String> FLAGS_TOPIC     = set("ABSTRACT","FINAL"); // no EXTENDED

        static ParseResult parseHeaderEndingAtTail(List<Token> toks) {
            if (toks.isEmpty()) return null;

            // VIEW TOPIC first (specific)
            ParseResult r = tryViewTopicSuffix(toks);
            if (r != null) return r;

            // TOPIC
            r = tryHeaderSuffix(toks, T.TOPIC, FLAGS_TOPIC, Kind.TOPIC);
            if (r != null) return r;

            // CLASS / STRUCTURE
            r = tryHeaderSuffix(toks, T.CLASS, FLAGS_CLASS, Kind.CLASS);
            if (r != null) return r;

            r = tryHeaderSuffix(toks, T.STRUCTURE, FLAGS_STRUCTURE, Kind.STRUCTURE);
            if (r != null) return r;

            // MODEL (simple)
            r = tryModelSuffix(toks);
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
                int namePos = toks.get(p).pos;
                int nameLen = name.length();
                p++;
                if (p == n) return new ParseResult(Kind.VIEW_TOPIC, name, tView.pos, namePos, nameLen);
            }
            return null;
        }

        /** KEYWORD [ '(' flags ')' ] IDENT [ EXTENDS IDENT ]   (consume to end) */
        /** KEYWORD IDENT [ '(' flags ')' ] [ EXTENDS IDENT ]  (must end at tail end) */
        private static ParseResult tryHeaderSuffix(List<Token> toks, T keyword,
                                                   Set<String> allowedFlags, Kind kind) {
            final int n = toks.size();
            for (int i = n - 1; i >= 0; i--) {
                Token kw = toks.get(i);
                if (kw.t != keyword) continue;
                int p = i + 1;

                // required IDENT (name)
                if (p >= n || toks.get(p).t != T.IDENT) continue;
                String name   = toks.get(p).text;
                int namePos   = toks.get(p).pos;
                int nameLen   = name.length();
                p++;

                // optional flags ONLY AFTER name (no flags allowed before name)
                int p2 = parseOptionalFlags(toks, p, allowedFlags);
                if (p2 == -1) continue;   // bad flags syntax
                p = p2;

                // optional EXTENDS IDENT
                if (p < n && toks.get(p).t == T.EXTENDS) {
                    p++;
                    if (p >= n || toks.get(p).t != T.IDENT) continue;
                    p++;
                }

                if (p == n) {
                    return new ParseResult(kind, name, kw.pos, namePos, nameLen);
                }
            }
            return null;
        }
        
        /** If next token is '(', parse flag list "(FLAG[,FLAG]*)" with allowed flags.
         *  Returns new index, same index if no '(', or -1 on syntax error. */
        private static int parseOptionalFlags(List<Token> toks, int p, Set<String> allowed) {
            final int n = toks.size();
            if (p >= n || toks.get(p).t != T.LPAREN) return p; // none

            int q = p + 1;
            if (q >= n || !isAllowedFlag(toks.get(q), allowed)) return -1;
            q++;
            while (q < n && toks.get(q).t == T.COMMA) {
                q++;
                if (q >= n || !isAllowedFlag(toks.get(q), allowed)) return -1;
                q++;
            }
            if (q >= n || toks.get(q).t != T.RPAREN) return -1;

            return q + 1; // position after ')'
        }

        /** MODEL IDENT (must end at tail end) */
        private static ParseResult tryModelSuffix(List<Token> toks) {
            int n = toks.size();
            for (int i = n - 1; i >= 0; i--) {
                Token kw = toks.get(i);
                if (kw.t != T.MODEL) continue;
                int p = i + 1;
                if (p >= n || toks.get(p).t != T.IDENT) continue;
                String name = toks.get(p).text;
                int namePos = toks.get(p).pos;
                int nameLen = name.length();
                p++;
                if (p == n) return new ParseResult(Kind.MODEL, name, kw.pos, namePos, nameLen);
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

    /* ============================ INDENT UTIL ============================ */

    /** Returns the leading whitespace (spaces/tabs) of the line containing absPos. */
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

    /* ==================== Unused BufferListener callbacks ==================== */

    @Override public void contentRemoved(JEditBuffer b,int sL,int o,int nL,int len){}
    @Override public void foldHandlerChanged(JEditBuffer b){}
    @Override public void foldLevelChanged(JEditBuffer b,int start,int end){}
    @Override public void preContentInserted(JEditBuffer b,int sL,int o,int nL,int len){}
    @Override public void preContentRemoved(JEditBuffer b,int sL,int o,int nL,int len){}
    @Override public void transactionComplete(JEditBuffer b){}
    @Override public void bufferLoaded(JEditBuffer b){}
}
