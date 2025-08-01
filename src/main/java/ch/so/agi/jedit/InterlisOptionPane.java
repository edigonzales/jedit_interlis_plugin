package ch.so.agi.jedit;

import org.gjt.sp.jedit.AbstractOptionPane;
import org.gjt.sp.jedit.jEdit;

import javax.swing.*;
import java.awt.*;

@SuppressWarnings("serial")
public class InterlisOptionPane extends AbstractOptionPane {
    private static final String P_REPOS     = "interlis.repos";
    private static final String P_PROXYHOST = "interlis.proxyHost";
    private static final String P_PROXYPORT = "interlis.proxyPort";

    private JTextField reposFld;
    private JTextField hostFld;
    private JTextField portFld;

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
            portFld.setColumns(6);

            addComponent("Model repositories (semicolon‑separated):", reposFld);
            addComponent("HTTP proxy host:", hostFld);
            addComponent("HTTP proxy port:", portFld);
    }
    
    @Override
    protected void _save() {
        jEdit.setProperty(P_REPOS, reposFld.getText().trim());
        jEdit.setProperty(P_PROXYHOST, hostFld.getText().trim());
        jEdit.setProperty(P_PROXYPORT, portFld.getText().trim());
    }
}
