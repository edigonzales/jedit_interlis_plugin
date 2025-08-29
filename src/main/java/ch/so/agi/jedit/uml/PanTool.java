// file: ch/so/agi/jedit/uml/PanTool.java
package ch.so.agi.jedit.uml;

import org.jhotdraw.draw.DrawingView;
import org.jhotdraw.draw.tool.AbstractTool;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public class PanTool extends AbstractTool {
    private Point startMouse;
    private Point startViewPos;
    private JScrollPane scroller;
    private Cursor oldCursor;

    @Override
    public void activate(org.jhotdraw.draw.DrawingEditor editor) {
        super.activate(editor);
        DrawingView v = getView();
        if (v instanceof Component) {
            scroller = (JScrollPane) SwingUtilities.getAncestorOfClass(
                    JScrollPane.class, (Component) v);
            oldCursor = ((Component) v).getCursor();
            ((Component) v).setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        }
    }

    @Override
    public void deactivate(org.jhotdraw.draw.DrawingEditor editor) {
        DrawingView v = getView();
        if (v instanceof Component && oldCursor != null) {
            ((Component) v).setCursor(oldCursor);
        }
        scroller = null;
        startMouse = null;
        startViewPos = null;
        super.deactivate(editor);
    }

    @Override public void mousePressed(MouseEvent e) {
        super.mousePressed(e);
        if (!SwingUtilities.isLeftMouseButton(e) || scroller == null) return;
        startMouse = e.getPoint();
        startViewPos = scroller.getViewport().getViewPosition();
        e.consume();
    }

    @Override public void mouseDragged(MouseEvent e) {
        if (scroller == null || startMouse == null || startViewPos == null) return;
        Point cur = e.getPoint();
        int dx = cur.x - startMouse.x, dy = cur.y - startMouse.y;

        Point newPos = new Point(startViewPos.x - dx, startViewPos.y - dy);
        JViewport vp = scroller.getViewport();
        Dimension extent = vp.getExtentSize();
        Component viewComp = vp.getView();
        Dimension viewSize = (viewComp != null) ? viewComp.getPreferredSize() : new Dimension(0, 0);
        newPos.x = Math.max(0, Math.min(newPos.x, Math.max(0, viewSize.width  - extent.width)));
        newPos.y = Math.max(0, Math.min(newPos.y, Math.max(0, viewSize.height - extent.height)));
        vp.setViewPosition(newPos);
        e.consume();
    }

    @Override public void mouseReleased(MouseEvent e) {
        startMouse = null;
        startViewPos = null;
        super.mouseReleased(e);
        e.consume();
    }

    @Override public boolean supportsHandleInteraction() { return false; }
}
