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
import java.nio.file.Paths;
import java.util.List;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackagePartName;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRelation;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.xmlbeans.XmlBeans;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDocDefaults;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPrDefault;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHpsMeasure;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.so.agi.jedit.compile.TdCache;

public final class CreateDocxAction {
    /**
     * Renders a DOCX based on an INTERLIS TransferDescription.
     * - Loads /template.docx from resources
     * - Strips template styles to avoid schema clashes
     * - Adds minimal default run props (Arial 11pt) in-memory
     * - Sets A4 portrait
     * - Writes model/topic/class tables via Ili2DocxRenderer
     */
    public static void createDocx(View view, Buffer buffer) {
        // 1) Ask for output file
        final JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save DOCX");
        chooser.setSelectedFile(new java.io.File("object_catalog.docx"));
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
                        PackageRelationshipCollection stylesRels =
                                docPart.getRelationshipsByType(XWPFRelation.STYLES.getRelation());
                        if (stylesRels != null) {
                            for (PackageRelationship rel : stylesRels) {
                                PackagePartName targetName = PackagingURIHelper.createPartName(rel.getTargetURI());
                                if (pkg.containPart(targetName)) pkg.removePart(targetName);
                                docPart.removeRelationship(rel.getId());
                            }
                        }

                        // --- Now safe to let XWPF read the package ---
                        try (XWPFDocument doc = new XWPFDocument(pkg)) {
                            trimLeadingEmptyParagraphs(doc);

                            // Ensure A4 portrait (twips)
                            CTSectPr sectPr = doc.getDocument().getBody().isSetSectPr()
                                    ? doc.getDocument().getBody().getSectPr()
                                    : doc.getDocument().getBody().addNewSectPr();
                            CTPageSz pageSz = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
                            pageSz.setW(BigInteger.valueOf(11906));
                            pageSz.setH(BigInteger.valueOf(16838));
                            pageSz.setOrient(STPageOrientation.PORTRAIT);

                            // Minimal defaults (Arial 11pt) as a fresh styles part (schema-safe)
                            XWPFStyles styles = doc.createStyles(); // ensures /word/styles.xml exists
                            CTStyles ct = (CTStyles) XmlBeans.getContextTypeLoader()
                                    .newInstance(CTStyles.type, null);
                            CTDocDefaults dd = ct.addNewDocDefaults();
                            CTRPrDefault rpd = dd.addNewRPrDefault();
                            CTRPr rpr = rpd.addNewRPr();
                            CTFonts rFonts = rpr.addNewRFonts();
                            rFonts.setAscii("Arial");
                            rFonts.setHAnsi("Arial");
                            rFonts.setCs("Arial");
                            CTHpsMeasure sz = rpr.addNewSz();      // 11pt = 22 half-points
                            sz.setVal(BigInteger.valueOf(22));
                            CTHpsMeasure szCs = rpr.addNewSzCs();
                            szCs.setVal(BigInteger.valueOf(22));
                            styles.setStyles(ct);

                            Ili2DocxRenderer.ensureAllStyles(doc);
                            
                            TransferDescription td = TdCache.peekLastValid(buffer);
                            
                            // --- Title ---
                            // --- Title: file name, using Word's "Title" paragraph style ---
                            String fileName = null;
                            if (buffer != null && buffer.getPath() != null) {
                                fileName = Paths.get(buffer.getPath()).getFileName().toString();
                            }
                            if (fileName == null) fileName = "Document";
                            XWPFParagraph titleP = doc.createParagraph();
                            titleP.setStyle("Title");
                            titleP.createRun().setText(fileName);

                            // --- Render model/topic/class content with real headings ---
                            Ili2DocxRenderer.renderTransferDescription(doc, td);
                            
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
    
    private static void trimLeadingEmptyParagraphs(XWPFDocument doc) {
        while (!doc.getBodyElements().isEmpty()
                && doc.getBodyElements().get(0).getElementType() == org.apache.poi.xwpf.usermodel.BodyElementType.PARAGRAPH) {
            XWPFParagraph p = (XWPFParagraph) doc.getBodyElements().get(0);
            String text = p.getText();
            boolean hasText = text != null && !text.trim().isEmpty();
            boolean hasRuns = !p.getRuns().isEmpty();
            if (hasText || hasRuns) break;
            doc.removeBodyElement(0);
        }
    }
}
