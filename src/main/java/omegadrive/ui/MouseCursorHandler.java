package omegadrive.ui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hides the mouse cursor after inactivity
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class MouseCursorHandler extends MouseMotionAdapter {

    private static final Logger LOG = LogManager.getLogger(MouseCursorHandler.class.getSimpleName());

    private int hideMouseDelayFrames =
            Integer.parseInt(System.getProperty("ui.hide.mouse.delay.frames", "180"));
    // Transparent 16 x 16 pixel cursor image.
    private BufferedImage cursorImg = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

    // Create a new blank cursor.
    private Cursor blankCursor = Toolkit.getDefaultToolkit().createCustomCursor(
            cursorImg, new Point(0, 0), "blank cursor");
    private Cursor defaultCursor = Cursor.getDefaultCursor();

    private AtomicBoolean hasMoved = new AtomicBoolean();
    private long frameCnt, lastMovementFrame;
    private Component cmp;

    public MouseCursorHandler(Component cmp) {
        cmp.addMouseMotionListener(this);
        this.cmp = cmp;
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        hasMoved.set(true);
    }

    public void newFrame() {
        frameCnt++;
        boolean isShowingCursor = defaultCursor.equals(cmp.getCursor());
        boolean hasMovedB = hasMoved.compareAndSet(true, false);
        boolean frameInterval = frameCnt - lastMovementFrame > hideMouseDelayFrames;
        if (!isShowingCursor && hasMovedB) {
            cmp.setCursor(null);
        } else if (isShowingCursor && !hasMovedB && frameInterval) {
            cmp.setCursor(blankCursor);
//            LOG.info("Hiding mouse cursor");
        }
        if (hasMovedB) {
            lastMovementFrame = frameCnt;
        }
    }

    public void reset() {
        cmp.setCursor(null);
        cmp.removeMouseMotionListener(this);
        cmp = null;
    }
}
