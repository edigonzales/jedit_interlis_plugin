package ch.so.agi.jedit.console;

import console.Console;
import console.Output;
import console.Shell;

/**
 * Damit beim erstmaligen Starten die Console leer bleibt.
 */
public class InterlisShell extends Shell {
    
    public InterlisShell() {
        super("InterlisShell");
    }
    
    @Override
    public void execute(Console arg0, String arg1, Output arg2, Output arg3, String arg4) {
        // TODO Auto-generated method stub
    }
    
    @Override
    public void openConsole(Console console) {
        // Do nothing instead of super.openConsole(console);
    }

    @Override
    public void printInfoMessage(Output output) {
        // Do nothing instead of super.printInfoMessage(output);
    }
}
