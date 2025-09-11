package ch.so.agi.jedit.uml._static;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.GUIUtilities;
import org.gjt.sp.jedit.View;
import org.gjt.sp.util.Log;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.so.agi.jedit.compile.TdCache;

public class UmlStatic {

    private UmlStatic() {}
    
    public static void show(View view, Buffer buffer) {
        Log.log(Log.MESSAGE, UmlStatic.class, "Create static UML class diagram.");
        
        TransferDescription td = TdCache.peekLastValid(buffer);
        String mermaidString = Ili2Mermaid.render(td);
        
//        Log.log(Log.MESSAGE, UmlStatic.class, mermaidString);
        
        String htmlString = "";
        if (mermaidString == null) {
            htmlString = "<pre class=\"mermaid\">could not create uml</pre>";
        } else {
            htmlString = "<pre class=\"mermaid\">\n"+mermaidString.replace("<<", "&#60;&#60;").replace(">>", "&#62;&#62;")+"</pre>";
        }
        
        try {
            String tempDir = System.getProperty("java.io.tmpdir");        
            Path htmlFile = Paths.get(tempDir, buffer.getName() + ".html");

            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            try (InputStream in = cl.getResourceAsStream("mermaid_template.html")) {
                String templateString = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                String finalHtmlString = templateString.replace("${mermaidString}", htmlString);
                
                Files.write(htmlFile, finalHtmlString.getBytes());                
            } 
            
            LivePreview.get().show(htmlFile); 
//            java.awt.Desktop.getDesktop().browse(URI.create(Paths.get("file://", htmlFile.toString()).toString()));
        } catch (IOException e) {
            e.printStackTrace();
            GUIUtilities.error(view, "error-creating-static-uml-file", new String[] { e.getMessage() });
        }
    }

}
