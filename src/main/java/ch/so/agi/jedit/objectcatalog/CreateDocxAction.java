package ch.so.agi.jedit.objectcatalog;

import org.apache.poi.xwpf.usermodel.*;
import org.gjt.sp.jedit.Buffer;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import java.io.*;
import java.math.BigInteger;
import javax.swing.*;
import java.nio.file.*;

public final class CreateDocxAction {
    public static void createDocx(final org.gjt.sp.jedit.View view, Buffer buffer) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save DOCX");
        chooser.setSelectedFile(new java.io.File("document.docx"));
        if (chooser.showSaveDialog(view) != JFileChooser.APPROVE_OPTION) return;

        final Path outPath = chooser.getSelectedFile().toPath();

        new javax.swing.SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                try (XWPFDocument doc = new XWPFDocument()) {

                    // A4 portrait
                    CTSectPr sectPr = doc.getDocument().getBody().isSetSectPr()
                            ? doc.getDocument().getBody().getSectPr()
                            : doc.getDocument().getBody().addNewSectPr();
                    CTPageSz pageSz = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
                    pageSz.setW(BigInteger.valueOf(11906));
                    pageSz.setH(BigInteger.valueOf(16838));
                    pageSz.setOrient(STPageOrientation.PORTRAIT);

                    // --- Attach physical /word/styles.xml from resources ---
                    try (InputStream in = CreateDocxAction.class.getResourceAsStream("/styles.xml")) {
                        if (in == null) throw new IOException("Resource /styles.xml not found");
                        StylesDocument stylesDoc = StylesDocument.Factory.parse(in);
                        XWPFStyles styles = doc.createStyles(); // ensures styles part exists
                        styles.setStyles(stylesDoc.getStyles());
                    }

                    // Content (no setFont* calls needed; defaults come from styles.xml)
                    XWPFParagraph p = doc.createParagraph();
                    XWPFRun run = p.createRun();
                    run.setText("Hello from InterlisPlugin (jEdit 5.7).");
                    run.addBreak();
                    run.setText("Defaults come from /word/styles.xml (Arial 11 pt).");

                    Path parent = outPath.getParent();
                    if (parent != null) Files.createDirectories(parent);
                    try (FileOutputStream fos = new FileOutputStream(outPath.toFile())) {
                        doc.write(fos);
                    }
                }
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(view,
                        "DOCX created:\n" + outPath.toAbsolutePath(),
                        "Create DOCX", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(view,
                        "Failed to create DOCX:\n" + cause.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
    private CreateDocxAction() {}
}
