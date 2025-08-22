package ch.so.agi.jedit;

import org.gjt.sp.jedit.View;
import org.gjt.sp.util.Log;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Supplier;

public class ModelDiscoveryWindow {
    
    private ModelDiscoveryWindow() {}
    
    public static void show(View owner, String prompt, String response) {
        buildAndShow(owner, prompt, response);
    }
    
    public static void showWhenReady(View owner, String prompt, Supplier<String> responseSupplier) {
        new SwingWorker<String, Void>() {
            @Override 
            protected String doInBackground() {
                Log.log(Log.DEBUG, this, "Calling API...");
                return responseSupplier.get();
            }
            @Override 
            protected void done() {
                String resp;
                try {
                    resp = get();
                } catch (Exception ex) {
                    resp = "Error calling api:\n" + ex.getMessage();
                }
                buildAndShow(owner, prompt, resp); // runs on EDT (done() is on EDT)
            }
        }.execute();
    }

    private static void buildAndShow(View owner, String prompt, String response) {
        JDialog dlg = new JDialog(owner, "Model tags", false);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dlg.setSize(900, 700);
        dlg.setLocationRelativeTo(owner);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(new EmptyBorder(8, 8, 8, 8));

        JTabbedPane tabs = new JTabbedPane();

        // Prompt tab (read-only)
        JTextArea taPrompt = new JTextArea();
        taPrompt.setEditable(false);
        taPrompt.setLineWrap(true);
        taPrompt.setWrapStyleWord(true);
        taPrompt.setText(prompt != null ? prompt : "");
        taPrompt.setCaretPosition(0);

        JPanel promptPanel = new JPanel(new BorderLayout());
        promptPanel.add(new JScrollPane(taPrompt), BorderLayout.CENTER);
        tabs.addTab("Prompt", promptPanel);

        // Response tab (read-only, selected by default)
        JTextArea taAnswer = new JTextArea();
        taAnswer.setEditable(false);
        taAnswer.setLineWrap(true);
        taAnswer.setWrapStyleWord(true);
        taAnswer.setText(response != null ? response : "");
        taAnswer.setCaretPosition(0);

        JPanel answerPanel = new JPanel(new BorderLayout());
        answerPanel.add(new JScrollPane(taAnswer), BorderLayout.CENTER);
        tabs.addTab("Response", answerPanel);
        tabs.setSelectedIndex(1); // show "Antwort" first

        // Bottom bar with Close button
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Close");
        close.addActionListener(e -> dlg.dispose());
        bottom.add(close);

        content.add(tabs, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);
        dlg.setContentPane(content);
        dlg.setVisible(true);
    }
}
