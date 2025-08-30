package ch.so.agi.jedit.uml;

import org.jhotdraw.draw.GraphicalCompositeFigure;
import org.jhotdraw.draw.RectangleFigure;
import org.jhotdraw.draw.TextFigure;
import org.jhotdraw.draw.AttributeKeys;

import java.awt.Color;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.Point2D;

/** A label with a colored background, sized to its text with padding. */
public class BadgeLabelFigure extends GraphicalCompositeFigure {
    private final RectangleFigure bg = new RectangleFigure();
    private final TextFigure tf = new TextFigure();

    private double padH = 4, padV = 2;
    private final double fontSize;
    private final boolean bold, italic;

    public BadgeLabelFigure(Color bgColor, double fontSize) {
        this(bgColor, fontSize, false, false);
    }
    public BadgeLabelFigure(Color bgColor, double fontSize, boolean bold, boolean italic) {
        this.fontSize = fontSize;
        this.bold = bold;
        this.italic = italic;

        setPresentationFigure(bg);

        bg.set(AttributeKeys.FILL_COLOR, bgColor);
        bg.set(AttributeKeys.STROKE_COLOR, null);
        bg.set(AttributeKeys.STROKE_WIDTH, 0d);

        tf.set(AttributeKeys.FONT_SIZE, fontSize);
        tf.set(AttributeKeys.FONT_BOLD, Boolean.valueOf(bold));
        tf.set(AttributeKeys.FONT_ITALIC, Boolean.valueOf(italic));
        tf.setSelectable(false);
        tf.setTransformable(false);
        tf.setText(""); // avoid default "Text"

        setSelectable(false);
        setTransformable(false);

        add(tf);
        layoutToNaturalSizeAt(0, 0);
    }

    public void setText(String s) {
        if (s == null) s = "";
        tf.setText(s);
        // size to natural text size at current top-left
        java.awt.geom.Rectangle2D b = getBounds();
        layoutToNaturalSizeAt(b.getX(), b.getY());
    }

    public void setTextColor(Color c) { tf.set(AttributeKeys.TEXT_COLOR, c); }
    public void setBackground(Color c) { bg.set(AttributeKeys.FILL_COLOR, c); }

    @Override
    public void layout() {
        java.awt.geom.Rectangle2D b = getBounds();
        layoutToNaturalSizeAt(b.getX(), b.getY());
    }

    @Override
    public void setBounds(Point2D.Double anchor, Point2D.Double lead) {
        // We ignore 'lead' size; badge always sizes to its text.
        layoutToNaturalSizeAt(anchor.x, anchor.y);
    }
    
    public java.awt.geom.Dimension2D getNaturalSize() {
        Size s = measure(tf.getText());
        final double w = s.w + padH * 2;
        final double h = s.h + padV * 2;
        return new java.awt.geom.Dimension2D() {
            double W = w, H = h;
            @Override public double getWidth()  { return W; }
            @Override public double getHeight() { return H; }
            @Override public void setSize(double w, double h) { W = w; H = h; }
        };
    }

    /* ===================== sizing ===================== */

    private void layoutToNaturalSizeAt(double x, double y) {
        Size s = measure(tf.getText());
        double w = s.w + padH * 2;
        double h = s.h + padV * 2;

        bg.setBounds(new Point2D.Double(x, y), new Point2D.Double(x + w, y + h));

        // place text inside with padding
        Point2D.Double a = new Point2D.Double(x + padH, y + padV);
        Point2D.Double l = new Point2D.Double(a.x + s.w, a.y + s.h);
        tf.setBounds(a, l);
    }

    private static final class Size { final double w,h; Size(double w,double h){this.w=w;this.h=h;} }

    /** Measure string width/height from font metrics (independent of TextFigure bounds). */
    private Size measure(String s) {
        if (s == null || s.isEmpty()) {
            // reserve a minimal badge size for empty strings
            return new Size(1, Math.max(10, fontSize + 2));
        }
        int style = (bold ? Font.BOLD : Font.PLAIN) | (italic ? Font.ITALIC : 0);
        Font f = new Font("Dialog", style, (int)Math.round(fontSize));
        FontRenderContext frc = new FontRenderContext(null, true, true);
        double w = f.getStringBounds(s, frc).getWidth();
        LineMetrics lm = f.getLineMetrics(s, frc);
        double h = lm.getAscent() + lm.getDescent();
        return new Size(Math.max(1, w), Math.max(1, h));
    }
}
