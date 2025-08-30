package ch.so.agi.jedit.uml;

import org.jhotdraw.draw.AttributeKeys;
import org.jhotdraw.draw.Drawing;
import org.jhotdraw.draw.LineConnectionFigure;
import org.jhotdraw.draw.liner.ElbowLiner;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

public class AssociationFigure extends LineConnectionFigure {
    private final BadgeLabelFigure startLabel =
            new BadgeLabelFigure(new Color(255, 255, 255), 11d); 
    private final BadgeLabelFigure endLabel   =
            new BadgeLabelFigure(new Color(255, 255, 255), 11d);

    private static final double OUT_DIST   = 10; // how far outside the box
    private static final double EDGE_ALONG = 0;  // optional slide along the edge

    public AssociationFigure() {
        set(AttributeKeys.STROKE_WIDTH, 1.0d);
        setLiner(new ElbowLiner());

        startLabel.setSelectable(false);
        startLabel.setTransformable(false);
        endLabel.setSelectable(false);
        endLabel.setTransformable(false);
        
        startLabel.setText("*******");
    }

    public void setStartMultiplicity(String s) {
        startLabel.setText(s == null ? "" : s);
        if (getDrawing() != null) updateLabelPositions();
    }
    public void setEndMultiplicity(String s) {
        endLabel.setText(s == null ? "" : s);
        if (getDrawing() != null) updateLabelPositions();
    }
    
    public void addTo(Drawing drawing) {
        drawing.add(this);
        drawing.add(startLabel);
        drawing.add(endLabel);
        updateLabelPositions();
    }
    
    @Override
    public void removeNotify(org.jhotdraw.draw.Drawing d) {
        d.remove(startLabel);
        d.remove(endLabel);
        super.removeNotify(d);
    }

    @Override
    public void validate() {
        super.validate();
        updateLabelPositions();
    }

    private void updateLabelPositions() {
        placeLabelOutsideBox(true,  startLabel);
        placeLabelOutsideBox(false, endLabel);
    }

    /** Places the badge just OUTSIDE the owner rectangle at the endpoint side. */
    private void placeLabelOutsideBox(boolean atStart, BadgeLabelFigure label) {
        if (getStartConnector() == null || getEndConnector() == null) return;

        final org.jhotdraw.draw.Figure owner =
                (atStart ? getStartConnector().getOwner() : getEndConnector().getOwner());
        if (owner == null) return;

        final Rectangle2D ob = owner.getBounds();
        final Point2D.Double p = atStart ? getStartPoint() : getEndPoint();

        // Determine edge we attach to
        final double eps = Math.max(1.5, Math.min(ob.getWidth(), ob.getHeight()) * 1e-6);
        boolean onLeft   = Math.abs(p.x - ob.getMinX()) <= eps;
        boolean onRight  = Math.abs(p.x - ob.getMaxX()) <= eps;
        boolean onTop    = Math.abs(p.y - ob.getMinY()) <= eps;
        boolean onBottom = Math.abs(p.y - ob.getMaxY()) <= eps;

        if (!(onLeft || onRight || onTop || onBottom)) {
            double dxL = Math.abs(p.x - ob.getMinX());
            double dxR = Math.abs(p.x - ob.getMaxX());
            double dyT = Math.abs(p.y - ob.getMinY());
            double dyB = Math.abs(p.y - ob.getMaxY());
            double m = Math.min(Math.min(dxL, dxR), Math.min(dyT, dyB));
            onLeft   = dxL == m;
            onRight  = dxR == m;
            onTop    = dyT == m;
            onBottom = dyB == m;
        }

        // outward normal + tangential
        double nx=0, ny=0, tx=0, ty=0;
        if (onLeft)   { nx = -1; ny =  0; tx = 0; ty = 1; }
        if (onRight)  { nx =  1; ny =  0; tx = 0; ty = 1; }
        if (onTop)    { nx =  0; ny = -1; tx = 1; ty = 0; }
        if (onBottom) { nx =  0; ny =  1; tx = 1; ty = 0; }

        // position for the TOP-LEFT of the badge
        java.awt.geom.Dimension2D ns = label.getNaturalSize();
        double w = ns.getWidth(), h = ns.getHeight();

        double ax = p.x + nx * OUT_DIST + tx * EDGE_ALONG;
        double ay = p.y + ny * OUT_DIST + ty * EDGE_ALONG;

        Point2D.Double a;
        if (onLeft)   a = new Point2D.Double(ax - w, ay - h * 0.5);
        else if (onRight) a = new Point2D.Double(ax, ay - h * 0.5);
        else if (onTop)   a = new Point2D.Double(ax - w * 0.5, ay - h);
        else              a = new Point2D.Double(ax - w * 0.5, ay);

        label.setBounds(a, new Point2D.Double(a.x + w, a.y + h));
    }
}
