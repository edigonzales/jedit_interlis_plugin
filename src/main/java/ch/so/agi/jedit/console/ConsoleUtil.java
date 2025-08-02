package ch.so.agi.jedit.console;

import console.*;
import org.gjt.sp.jedit.*;
import java.io.BufferedReader;
import java.nio.file.*;

import javax.swing.SwingUtilities;

import java.io.IOException;

public final class ConsoleUtil {

    private ConsoleUtil() {}

    public static void showLog(View view, Path logFile) {
        view.getDockableWindowManager().addDockableWindow("console");
        Console console = ConsolePlugin.getConsole(view);
        if (console == null) {
            GUIUtilities.error(view, "console-missing", null);
            return;
        }

        Shell shell = Shell.getShell("InterlisShell");
        if (shell == null) {
            GUIUtilities.error(view, "shell-not-registered", new String[] { "InterlisShell" });
            return;
        }

        console.setShell(shell);
        console.clear();
        Console.ShellState state = console.getShellState(shell);

        try (BufferedReader r = Files.newBufferedReader(logFile)) {
            String line;
            while ((line = r.readLine()) != null) {
                state.print(null, line);
            }
            state.commandDone();
        } catch (IOException e) {
            state.print(null, "Error reading logfile “" + logFile + "”: " + e.getMessage() + "\n");
        }
        
        SwingUtilities.invokeLater(() -> {
            if (view != null && view.getEditPane() != null) {
                view.getEditPane().focusOnTextArea(); // jEdit ≥ 5.6
                // If you’re on an older jEdit use:
                // view.getTextArea().requestFocusInWindow();
            }
        });
    }
}