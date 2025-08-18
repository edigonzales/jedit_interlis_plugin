package ch.so.agi.jedit.ui;

import org.gjt.sp.jedit.AbstractOptionPane;
import org.gjt.sp.jedit.jEdit;

import javax.swing.*;
import java.awt.*;

@SuppressWarnings("serial")
public class InterlisOptionPane extends AbstractOptionPane {
    private static final String P_REPOS     = "interlis.repos";
    private static final String P_PROXYHOST = "interlis.proxyHost";
    private static final String P_PROXYPORT = "interlis.proxyPort";
    private static final String P_OPENAI_BASE_URL = "interlis.openai.base-url";
    private static final String P_OPENAI_API_KEY = "interlis.openai.api-key";

    private JTextField reposFld;
    private JTextField hostFld;
    private JTextField portFld;

    private JTextField openaiBaseUrlFld;
    private JTextField openaiApiKeyFld;

    public InterlisOptionPane() {
        super("interlis");
    }

    @Override
    protected void _init() {
        reposFld = new JTextField(
                jEdit.getProperty(P_REPOS,
                    jEdit.getProperty(P_REPOS + ".default")));

            hostFld  = new JTextField(
                jEdit.getProperty(P_PROXYHOST,
                    jEdit.getProperty(P_PROXYHOST + ".default")));

            portFld  = new JTextField(
                jEdit.getProperty(P_PROXYPORT,
                    jEdit.getProperty(P_PROXYPORT + ".default")));
            
            openaiBaseUrlFld  = new JTextField(
                    jEdit.getProperty(P_OPENAI_BASE_URL,
                        jEdit.getProperty(P_OPENAI_BASE_URL + ".default")));

            openaiApiKeyFld  = new JTextField(
                    jEdit.getProperty(P_OPENAI_API_KEY,
                        jEdit.getProperty(P_OPENAI_API_KEY + ".default")));

            addComponent("Model repositories (semicolon‑separated):", reposFld);
            addComponent("HTTP proxy host:", hostFld);
            addComponent("HTTP proxy port:", portFld);
            addComponent("OpenAI Base URL:", openaiBaseUrlFld);
            addComponent("OpenAI API Key:", openaiApiKeyFld);
    }
    
    @Override
    protected void _save() {
        jEdit.setProperty(P_REPOS, reposFld.getText().trim());
        jEdit.setProperty(P_PROXYHOST, hostFld.getText().trim());
        jEdit.setProperty(P_PROXYPORT, portFld.getText().trim());
        jEdit.setProperty(P_OPENAI_BASE_URL, openaiBaseUrlFld.getText().trim());
        jEdit.setProperty(P_OPENAI_API_KEY, openaiApiKeyFld.getText().trim());
    }
}
