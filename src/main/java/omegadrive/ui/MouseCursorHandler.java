package omegadrive.ui;

import omegadrive.util.LogHelper;
import org.slf4j.Logger;

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

    private static final Logger LOG = LogHelper.getLogger(MouseCursorHandler.class.getSimpleName());

    private final int hideMouseDelayFrames =
            Integer.parseInt(System.getProperty("ui.hide.mouse.delay.frames", "180"));
    // Transparent 16 x 16 pixel cursor image.
    private final BufferedImage cursorImg = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

    // Create a new blank cursor.
    private final Cursor blankCursor = Toolkit.getDefaultToolkit().createCustomCursor(
            cursorImg, new Point(0, 0), "blank cursor");
    private final Cursor defaultCursor = Cursor.getDefaultCursor();

    private final AtomicBoolean hasMoved = new AtomicBoolean();
    private long frameCnt, lastMovementFrame;
    private final Component cmp;

    public MouseCursorHandler(Component cmp) {
        cmp.addMouseMotionListener(this);
        this.cmp = cmp;
        LOG.info("#MouseMotionListeners: {}", cmp.getMouseMotionListeners().length);
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
        frameCnt = lastMovementFrame = 0;
    }
}
