package ch.so.agi.jedit.console;

import org.gjt.sp.jedit.View;

import console.Console;
import console.Output;
import console.Shell;

public class InterlisShell extends Shell {
    
    public InterlisShell() {
        super("INTERLIS");
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
