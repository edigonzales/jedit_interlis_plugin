package ch.so.agi.jedit.uml;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import ch.interlis.ili2c.metamodel.Topic;

import org.jhotdraw.draw.GraphicalCompositeFigure;
import org.jhotdraw.draw.RectangleFigure;
import org.jhotdraw.draw.TextFigure;
import org.jhotdraw.draw.connector.ChopRectangleConnector;
import org.jhotdraw.draw.connector.Connector;

/**
 * JHotDraw 7.6 TopicFigure (UML package-style):
 * - Transparent presentation "envelope" enclosing tab + body
 * - Tab rectangle on top-left with the topic name
 * - Body rectangle below (empty content for now)
 * - Uniform stroke width; no rounded corners
 * - Auto-sizes to content (tab width follows title), origin preserved during layout
 */
public class TopicFigure extends GraphicalCompositeFigure {
    /* ===== styling ===== */
    private static final double STROKE   = 1.0;

    // Tab internal padding (around the title text)
    private static final double TPAD_L = 10;
    private static final double TPAD_R = 10;
    private static final double TPAD_T = 6;
    private static final double TPAD_B = 6;

    // Body minimal size (so package doesn’t collapse)
    private static final double BODY_MIN_W = 140;
    private static final double BODY_MIN_H = 80;

    // Extra body width allowance beyond the tab (purely cosmetic)
    private static final double BODY_EXTRA_W = 12;

    // Small vertical gap between tab bottom and body top (0 for tight join)
    private static final double TAB_BODY_GAP = 0;

    /* ===== figures ===== */
    /** Transparent presentation “envelope” covering tab + body. */
    private final RectangleFigure envelopeRect = new RectangleFigure();
    /** Visible body rectangle (main package rectangle). */
    private final RectangleFigure bodyRect     = new RectangleFigure();
    /** Visible tab rectangle (holds the title). */
    private final RectangleFigure tabRect      = new RectangleFigure();
    /** Topic title text placed inside tabRect. */
    private final TextFigure      titleTf      = new TextFigure();

    public TopicFigure(Topic topic) {
        this(topic.getName());
    }

    public TopicFigure(String title) {
        // Presentation (transparent envelope)
        setPresentationFigure(envelopeRect);
        envelopeRect.set(org.jhotdraw.draw.AttributeKeys.FILL_COLOR, null);
        envelopeRect.set(org.jhotdraw.draw.AttributeKeys.STROKE_WIDTH, 0d); // invisible

        // Body rect: plain, white, uniform stroke
        bodyRect.set(org.jhotdraw.draw.AttributeKeys.STROKE_WIDTH, STROKE);
        bodyRect.set(org.jhotdraw.draw.AttributeKeys.FILL_COLOR, Color.white);
        add(bodyRect);

        // Tab rect: white fill + stroke; sits above the body’s top edge
        tabRect.set(org.jhotdraw.draw.AttributeKeys.STROKE_WIDTH, STROKE);
        tabRect.set(org.jhotdraw.draw.AttributeKeys.FILL_COLOR, Color.white);
        add(tabRect);

        // Title text inside the tab
        titleTf.setText(title);
        titleTf.set(org.jhotdraw.draw.AttributeKeys.FONT_BOLD, Boolean.FALSE);
        titleTf.set(org.jhotdraw.draw.AttributeKeys.FONT_SIZE, 12d);
        add(titleTf);
    }

    public void setTitle(String title) {
        titleTf.setText(title);
        layout();
    }
    
    public Connector connector() {
        return new ChopRectangleConnector(this);
    }

    @Override
    public void layout() {
        // Preserve current origin of the envelope to avoid snapping to (0,0)
        Rectangle2D old = envelopeRect.getBounds();
        double ox = old.getX();
        double oy = old.getY();

        // Measure title
        Rectangle2D tb = titleTf.getBounds();
        double tW = Math.max(1, tb.getWidth());
        double tH = Math.max(1, tb.getHeight());

        // Compute tab size from title + padding
        double tabW = Math.max(60, TPAD_L + tW + TPAD_R); // min tab width for aesthetics
        double tabH = TPAD_T + tH + TPAD_B;

        // Compute body size (independent; no contents for now)
        double bodyW = Math.max(BODY_MIN_W, tabW + BODY_EXTRA_W);
        double bodyH = BODY_MIN_H;

        // Envelope covers both tab (above) and body (below)
        double totalW = Math.max(tabW, bodyW);
        double totalH = tabH + TAB_BODY_GAP + bodyH;

        // Position tab at the top-left of the envelope
        tabRect.setBounds(
            new Point2D.Double(ox, oy),
            new Point2D.Double(ox + tabW, oy + tabH)
        );

        // Place title inside the tab
        Point2D.Double tA = new Point2D.Double(ox + TPAD_L, oy + TPAD_T);
        Point2D.Double tL = new Point2D.Double(tA.x + tW,  tA.y + tH);
        titleTf.setBounds(tA, tL);

        // Place body directly under the tab, left-aligned; the tab’s bottom edge
        // visually “attaches” to the body’s top edge (TAB_BODY_GAP controls any gap)
        double by = oy + tabH + TAB_BODY_GAP;
        bodyRect.setBounds(
            new Point2D.Double(ox, by),
            new Point2D.Double(ox + bodyW, by + bodyH)
        );

        // Finally, set the transparent envelope to enclose both shapes
        envelopeRect.setBounds(
            new Point2D.Double(ox, oy),
            new Point2D.Double(ox + totalW, oy + totalH)
        );
    }
}
