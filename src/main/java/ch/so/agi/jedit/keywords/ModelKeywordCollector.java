package ch.so.agi.jedit.keywords;

import ch.interlis.ili2c.Ili2cSettings;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.so.agi.jedit.ModelDiscoveryWindow;
import ch.so.agi.jedit.compile.TdCache;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.GUIUtilities;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.json.JSONObject;

import javax.swing.SwingUtilities;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.stream.Collectors;

/**
 * Collects simple model metadata (names + documentation) and writes XML to a new buffer.
 *
 * <metadata>
 *   <model name="..." >
 *     <doc>...</doc>
 *     <classes>  <!-- model-level classes/structures -->
 *       <class name="..." kind="class|structure"><doc>...</doc></class>
 *       ...
 *     </classes>
 *     <topics>
 *       <topic name="...">
 *         <doc>...</doc>
 *         <classes>
 *           <class name="..." kind="class|structure"><doc>...</doc></class>
 *           ...
 *         </classes>
 *       </topic>
 *       ...
 *     </topics>
 *   </model>
 *   ...
 * </metadata>
 */
public final class ModelKeywordCollector {
    private static final String P_OPENAI_BASE_URL = "interlis.openai.base-url";
    private static final String P_OPENAI_MODEL = "interlis.openai.model";
    private static final String P_OPENAI_API_KEY = "interlis.openai.api-key";

    private ModelKeywordCollector() {}

    /** Entry point used by the jEdit action. */
    public static void collectToNewBuffer(View view) {
        Buffer src = view.getBuffer();

        // We use the last valid (saved/compiled) TD; if none, ask the user to compile.
        TransferDescription td = TdCache.peekLastValid(src);
        if (td == null) {
            GUIUtilities.error(view, "interlis-collect-no-td", null);
            return;
        }

        final String xml = buildXml(td);
        
        String prompt = readPromptFile();
        if (prompt == null) {
            GUIUtilities.error(view, "interlis-collect-no-prompt", null);
            return;
        }
        
//        prompt = prompt
//                .replace("\\", "\\\\")  // escape backslashes
//                .replace("\"", "\\\"")  // escape quotes
//                .replace("\r", "\\r")   // escape CR
//                .replace("\n", "\\n");  // escape LF
        
        System.err.println(prompt);
        
        prompt += xml;
        
        String apiUrl = jEdit.getProperty(P_OPENAI_BASE_URL);
        if (apiUrl == null) {
            GUIUtilities.error(view, "interlis-collect-no-api-url", null);
            return;
        }
        
        String apiKey = jEdit.getProperty(P_OPENAI_API_KEY);
        if (apiKey == null) {
            GUIUtilities.error(view, "interlis-collect-no-api-key", null);
            return;
        }
        
        String modelName = jEdit.getProperty(P_OPENAI_MODEL);
        if (modelName == null) {
            GUIUtilities.error(view, "interlis-collect-no-model", null);
            return;
        }

//        String response = null;
//        try {
//            response = callOpenAI(prompt, apiUrl, apiKey, modelName);
//        } catch (IOException | InterruptedException e) {
//            e.printStackTrace();
//            GUIUtilities.error(view, "interlis-collect-openai-error", null);
//            return;
//        }

        final String promptCopy = prompt;
        ModelDiscoveryWindow.showWhenReady(view, prompt, () -> callOpenAI(promptCopy, apiUrl, apiKey, modelName));
        
//        SwingUtilities.invokeLater(() -> {
//            Buffer out = jEdit.newFile(view);
//            out.setMode(jEdit.getMode("xml"));
//            out.insert(0, xml);
//        });
    }

    /* ============================== XML builder ============================== */

    private static String buildXml(TransferDescription td) {
        StringBuilder sb = new StringBuilder(8192);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<metadata>\n");

        // Only models that come from the current file:
        for (Model model : td.getModelsFromLastFile()) {
            appendModel(sb, model);
        }

        sb.append("</metadata>\n");
        return sb.toString();
    }

    private static void appendModel(StringBuilder sb, Model model) {
        String name = safe(model.getName());
        sb.append("  <model name=\"").append(name).append("\">\n");

        appendMetaDoc(sb, 2, model, "title");
        appendMetaDoc(sb, 2, model, "shortDescription");
        appendDoc(sb, 2, model.getDocumentation());

        // model-level classes/structures
        boolean openedClasses = false;
        for (Iterator<?> it = model.iterator(); it.hasNext();) {
            Object o = it.next();
            if (o instanceof Table) {
                if (!openedClasses) {
                    sb.append("    <classes>\n");
                    openedClasses = true;
                }
                appendTable(sb, 3, (Table) o);
            }
        }
        if (openedClasses) sb.append("    </classes>\n");

        // topics (with their classes/structures)
        boolean openedTopics = false;
        for (Iterator<?> it = model.iterator(); it.hasNext();) {
            Object o = it.next();
            if (o instanceof Topic) {
                if (!openedTopics) {
                    sb.append("    <topics>\n");
                    openedTopics = true;
                }
                appendTopic(sb, (Topic) o);
            }
        }
        if (openedTopics) sb.append("    </topics>\n");

        sb.append("  </model>\n");
    }

    private static void appendTopic(StringBuilder sb, Topic t) {
        String name = safe(t.getName());
        sb.append("      <topic name=\"").append(name).append("\">\n");
        appendDoc(sb, 3, t.getDocumentation());

        boolean openedClasses = false;
        for (Iterator<?> it = t.iterator(); it.hasNext();) {
            Object o = it.next();
            if (o instanceof Table) {
                if (!openedClasses) {
                    sb.append("        <classes>\n");
                    openedClasses = true;
                }
                appendTable(sb, 4, (Table) o);
            }
        }
        if (openedClasses) sb.append("        </classes>\n");

        sb.append("      </topic>\n");
    }

    private static void appendTable(StringBuilder sb, int indent, Table tbl) {
        String kind = tbl.isIdentifiable() ? "class" : "structure";
        String name = safe(tbl.getName());
        indent(sb, indent).append("<class name=\"").append(name)
                .append("\" kind=\"").append(kind).append("\">").append('\n');
        appendDoc(sb, indent + 1, tbl.getDocumentation());
        indent(sb, indent).append("</class>").append('\n');
    }

    private static void appendMetaDoc(StringBuilder sb, int indent, Element element, String metaAttrKey) {
        String md = trimOrNull(element.getMetaValue(metaAttrKey));
        if (md == null || md.isEmpty()) return;
        indent(sb, indent).append("<"+metaAttrKey+" xml:space=\"preserve\">")
            .append(safe(md))
            .append("</"+metaAttrKey+">\n");
    }
    
    private static void appendDoc(StringBuilder sb, int indent, String doc) {
        String d = trimOrNull(doc);
        if (d == null || d.isEmpty()) return;
        // Preserve line breaks, escape XML specials.
        indent(sb, indent).append("<doc xml:space=\"preserve\">")
                .append(safe(d))
                .append("</doc>\n");
    }
    
    /* ============================== helpers ============================== */

    public static String callOpenAI(String prompt, String apiUrl, String apiKey, String modelName) {
        String escapedPrompt = prompt
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r") 
                .replace("\n", "\\n");
        
        HttpClient client = HttpClient.newHttpClient();
        
        String requestBody = "{\n" +
                "    \"model\": \""+modelName+"\",\n" +
                "    \"input\": \""+escapedPrompt+"\"" +
                //"    \"service_tier\": \"flex\"" + 
                "}";
        
        System.err.println("**** requestBody: " + requestBody);
        System.err.println("**** apiUrl: " + apiUrl);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        
        HttpResponse<String> response = null;
        try {
            response = client.send(request, 
                    HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return null;
        }
        
        if (response.statusCode() >= 400) {
            return response.body();
        }
        
        JSONObject root = new JSONObject(response.body());
        Object text = root.query("/output/1/content/0/text");
        
        if (text == null) {
            return response.body();
        }
        
        System.err.println("*************** " + text);
        
        return text.toString();
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String safe(String s) {
        if (s == null) return "";
        // minimal XML escape for element content / attribute values
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': out.append("&amp;"); break;
                case '<': out.append("&lt;");  break;
                case '>': out.append("&gt;");  break;
                case '"': out.append("&quot;");break;
                case '\'':out.append("&apos;");break;
                default:  out.append(c);
            }
        }
        return out.toString();
    }

    private static StringBuilder indent(StringBuilder sb, int level) {
        for (int i = 0; i < level; i++) sb.append("  ");
        return sb;
    }
    
    private static String readPromptFile()  {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream in = cl.getResourceAsStream("prompt.txt")) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
