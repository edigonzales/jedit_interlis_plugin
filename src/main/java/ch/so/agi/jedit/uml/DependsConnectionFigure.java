package ch.so.agi.jedit.uml;

import org.jhotdraw.draw.LineConnectionFigure;
import org.jhotdraw.draw.AttributeKeys;
import org.jhotdraw.draw.decoration.ArrowTip;
import org.jhotdraw.draw.decoration.LineDecoration;

public final class DependsConnectionFigure extends LineConnectionFigure {
    public DependsConnectionFigure() {
        
        // Ich kriegs nicht hin. Der ArrowTip müsste eigentlich offen sein
        // und ungefüllt. Aber wenn ich ihn nicht fülle, ist er gestrichelt.
        LineDecoration tip = new ArrowTip(0.35, 12.0, 11.0, true, false, true); 
        set(AttributeKeys.END_DECORATION, tip);

        // dashed line
        set(AttributeKeys.STROKE_WIDTH, 1.0d);
        set(AttributeKeys.STROKE_DASHES, new double[]{6d, 6d});
        set(AttributeKeys.STROKE_DASH_PHASE, 0.5d);

        // open, undashed arrow head at the END
//        org.jhotdraw.draw.decoration.ArrowTip tip =
//                new org.jhotdraw.draw.decoration.ArrowTip(
//                        0.35,   // angle (radians-ish)
//                        12,     // outer radius
//                        10,     // inner radius
//                        true,  // isFilled -> open
//                        false,   // isStroked -> outline
//                        true    // isSolid  -> ignore dash pattern for the head
//                );
        
    }
}