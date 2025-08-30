package ch.so.agi.jedit.uml;

import org.jhotdraw.draw.AttributeKeys;
import org.jhotdraw.draw.LineConnectionFigure;
import org.jhotdraw.draw.decoration.ArrowTip;
import org.jhotdraw.draw.decoration.LineDecoration;
import org.jhotdraw.draw.liner.ElbowLiner;

public class GeneralizationFigure extends LineConnectionFigure {
    public GeneralizationFigure() {        
        set(AttributeKeys.STROKE_WIDTH, 1.0d);
        setLiner(new ElbowLiner());
        LineDecoration tip = new ArrowTip(0.35, 12.0, 11.0, false, true, true); // hollow triangle
        set(AttributeKeys.START_DECORATION, null);
        set(AttributeKeys.END_DECORATION, tip);
    }
}
