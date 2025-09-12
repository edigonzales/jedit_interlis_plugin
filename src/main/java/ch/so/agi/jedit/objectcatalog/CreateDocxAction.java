package ch.so.agi.jedit.objectcatalog;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.Log;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackagePartName;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFRelation;
import org.apache.poi.xwpf.usermodel.XWPFStyles;

import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDocDefaults;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPrDefault;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHpsMeasure;

public final class CreateDocxAction {
    public static void createDocx(final View view, Buffer buffer) {
        // 1) Ask for output file
        final JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save DOCX");
        chooser.setSelectedFile(new java.io.File("document.docx"));
        if (chooser.showSaveDialog(view) != JFileChooser.APPROVE_OPTION) return;

        final Path outPath = chooser.getSelectedFile().toPath();

        // 2) Work off the EDT
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                try (InputStream in = CreateDocxAction.class.getResourceAsStream("/template.docx")) {
                    if (in == null) throw new IOException("Resource /template.docx not found on classpath");

                    // --- Open OPC and strip styles BEFORE XWPF parses the package ---
                    try (OPCPackage pkg = OPCPackage.open(in)) {
                        // main document part
                        List<PackagePart> docs = pkg.getPartsByContentType(XWPFRelation.DOCUMENT.getContentType());
                        if (docs.isEmpty()) throw new IOException("template.docx has no /word/document.xml part");
                        PackagePart docPart = docs.get(0);

                        // remove any /word/styles.xml relationships and the part itself
                        PackageRelationshipCollection stylesRels = docPart.getRelationshipsByType(XWPFRelation.STYLES.getRelation());
                        if (stylesRels != null) {
                            for (PackageRelationship rel : stylesRels) {
                                PackagePartName targetName = PackagingURIHelper.createPartName(rel.getTargetURI());
                                if (pkg.containPart(targetName)) pkg.removePart(targetName);
                                docPart.removeRelationship(rel.getId());
                            }
                        }

                        // --- Now safe to let XWPF read the package ---
                        try (XWPFDocument doc = new XWPFDocument(pkg)) {
                            // Content (uses our defaults; no setFont* calls)
                            XWPFParagraph p = doc.createParagraph();
                            XWPFRun run = p.createRun();
                            run.setText("Hello from InterlisPlugin (template-based).");
                            run.addBreak();
                            run.setText("Styles were removed from the template, defaults added in-memory.");

                            // Save
                            Path parent = outPath.getParent();
                            if (parent != null) Files.createDirectories(parent);
                            try (FileOutputStream fos = new FileOutputStream(outPath.toFile())) {
                                doc.write(fos);
                            }
                        }
                    }
                } catch (Throwable t) {
                    Log.log(Log.ERROR, CreateDocxAction.class, "Failed to create DOCX", t);
                    throw t;
                }
                return null;
            }

            @Override protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(
                            view,
                            "DOCX created:\n" + outPath.toAbsolutePath(),
                            jEdit.getProperty("interlis.create-docx.label", "Create DOCX"),
                            JOptionPane.INFORMATION_MESSAGE
                    );
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(
                            view,
                            "Failed to create DOCX:\n" + cause.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        }.execute();
    }

    private CreateDocxAction() {}
}
