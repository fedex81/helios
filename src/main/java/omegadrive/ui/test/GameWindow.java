package omegadrive.ui.test;

import omegadrive.GenesisProvider;
import omegadrive.ui.GenesisWindow;
import omegadrive.ui.RenderingStrategy;
import omegadrive.util.ScreenSizeHelper;
import omegadrive.util.VideoMode;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.VolatileImage;
import java.util.Locale;

/*
 * The GameWindow class manages the jFrame, canvas and buffer strategy. It creates a
 * Window with features like switching between full screen and windowed mode.
 *
 * This class also solves some problems with Java2D:
 * - Correct focus acquisition;
 * - Invisible cursor in fullscreen mode;
 * - No flickering during the resize of a Window
 */
public class GameWindow implements GenesisWindow {
    // Graphics attributes
    private GraphicsEnvironment graphicsEnv = null;
    private GraphicsDevice graphicsDev = null;
    private GraphicsConfiguration graphicsConf = null;

    // The focus and window listener
    private CanvasFocusListener canvasFocusListener = null;
    private WindowEventListener windowEventListener = null;

    //TODO
    // Custom listener for the GameWindow
//    private GameWindowListener gameWindowListener = null;

    // The frame to contain this canvas
    private JFrame jFrame = null;

    // The canvas to draw on
    private Canvas canvas = null;

    // The buffer strategy
    private BufferStrategy strategy = null;

    // The drawing graphics
    private Graphics2D drawGraphics = null;


    // Use real FSEM or fake FSEM
    private boolean useFakeFSEM = false;
    // Windowed mode or FSEM mode
    // We need both variables because the view can not be initialised yet
    private boolean isWindow = false;
    private boolean isFSEM = false;

    // Hide the mouse cursor in FSEM mode
    private boolean hideMouseCursor = false;

    // Custom mouse cursor
    private Cursor customCursor = null;

    // The status of windowed mode before it was closed
    private int sizeX = 0;
    private int sizeY = 0;
    private int posX = 0;
    private int posY = 0;
    private boolean maximized = false;

    // FPS Counter
    private FPSCounter fpsCounter = null;
    private boolean showFPS = false;

    private BufferedImage img;
    private int[] pixels;

    // Variables used to avoid flickering during a resize of the window
    private VolatileImage backScreen = null;    // The back buffer to use during resizing

    private boolean resize = false;                // Resize in progress
    private long timeStamp = 0L;                // Time since last resize

    private int prevX = 0;                        // Previous size of the window
    private int prevY = 0;

    private GenesisProvider genesisProvider;

    /*
     * Create a new gameWindow
     */
    public GameWindow(GenesisProvider genesisProvider, String title) {
        // No flickering during resize (otherwise
        // the background is redrawn automatically).
        // Preferably use "sun.awt.noerasebackground" else use
        // Dynamic layout. If both are not supported... too bad.
        if (System.getProperty("sun.awt.noerasebackground") == null) {
            System.setProperty("sun.awt.noerasebackground", "true");
        } else {
            Toolkit.getDefaultToolkit().setDynamicLayout(false);
        }

        // Acquiring the current Graphics Environment, Device and Configuration
        graphicsEnv = GraphicsEnvironment.getLocalGraphicsEnvironment();
        graphicsDev = graphicsEnv.getDefaultScreenDevice();
        graphicsConf = graphicsDev.getDefaultConfiguration();

        // Create a canvas
        canvas = new Canvas(graphicsConf);
        canvas.setFocusTraversalKeysEnabled(false);
        canvas.setIgnoreRepaint(true);

        // Set background color to transparent
        canvas.setBackground(new Color(0.0f, 0.0f, 0.0f, 0.0f));

        // Add focus listener to the canvas
        canvasFocusListener = new CanvasFocusListener();
        canvas.addFocusListener(canvasFocusListener);

        // Create a frame
        jFrame = new JFrame(graphicsConf);
        // Implement own listener
        jFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        jFrame.setTitle(title);
        jFrame.setFocusTraversalKeysEnabled(false);
        jFrame.setIgnoreRepaint(true);

        // Add an window listener to manage closing of the window
        windowEventListener = new WindowEventListener();
        jFrame.addWindowListener(windowEventListener);

        // Add an component listener that manages the location of the frame
        jFrame.addComponentListener(new GameFrameListener());

        // Add canvas to the frame
        jFrame.add(canvas);

        // Create a new FPSCounter
        fpsCounter = new FPSCounter();

        this.genesisProvider = genesisProvider;
    }

    /*
     * Add a keyListener to the canvas of the gameWindow
     */
    public void addKeyListener(KeyListener keyListener) {
        canvas.addKeyListener(keyListener);
    }

    /*
     * Remove a keyListener from the canvas of the gameWindow
     */
    public void removeKeyListener(KeyListener keyListener) {
        canvas.removeKeyListener(keyListener);
    }

    /*
     * Add a mouseListener to the canvas of the gameWindow
     */
    public void addMouseListener(MouseListener mouseListener) {
        canvas.addMouseListener(mouseListener);
    }

    /*
     * Remove a mouseListener from the canvas of the gameWindow
     */
    public void removeMouseListener(MouseListener mouseListener) {
        canvas.removeMouseListener(mouseListener);
    }

    /*
     * Add a mouseMotionListener to the canvas of the gameWindow
     */
    public void addMouseMotionListener(MouseMotionListener mouseMotionListener) {
        canvas.addMouseMotionListener(mouseMotionListener);
    }

    /*
     * Remove a mouseMotionListener from the canvas of the gameWindow
     */
    public void removeMouseMotionListener(MouseMotionListener mouseMotionListener) {
        canvas.removeMouseMotionListener(mouseMotionListener);
    }

    /*
     * Add a mouseWheelListener to the canvas of the gameWindow
     */
    public void addMouseWheelListener(MouseWheelListener mouseWheelListener) {
        canvas.addMouseWheelListener(mouseWheelListener);
    }

    /*
     * Remove a mouseWheelListener from the canvas of the gameWindow
     */
    public void removeMouseWheelListener(MouseWheelListener mouseWheelListener) {
        canvas.removeMouseWheelListener(mouseWheelListener);
    }

    //TODO
    /*
     * Add a GameWindowListener to the frame of the gameWindow
     */
//    public void addGameWindowListener(GameWindowListener gameWindowListener)
//    {
//        this.gameWindowListener = gameWindowListener;
//    }

    /*
     * Get the size of the gameWindow
     */
    public Dimension getSize() {
        return canvas.getSize();
    }

    /*
     * Get current frames per second
     */
    public float getFPS() {
        return fpsCounter.getFPS();
    }

    /*
     * Show the currect frames per second
     */
    public void showFPS(boolean showFPS) {
        this.showFPS = showFPS;
    }

    /*
     * Get the graphics object to render the new frame on.
     */
    public Graphics2D getDrawGraphics() {
        // Get the size of the canvas
        Dimension windowSize = canvas.getSize();

        // Determine if a resize is in progress. The standard Java listeners
        // have a high delay when a window is resized so we don't use these.
        if (windowSize.width != prevX || windowSize.height != prevY) {
            prevX = windowSize.width;
            prevY = windowSize.height;

            if (!resize) {
                backScreen = createBackBuffer();
            }

            resize = true;
            timeStamp = System.nanoTime();
        } else if (resize) {
            // A second has expired since the last resize. Resizing is done.
            if ((System.nanoTime() - timeStamp) >= 1000000000L) {
                resetResizingVariables();
            }
        }

        if (resize) {
            int valid = backScreen.validate(graphicsConf);

            if (valid == VolatileImage.IMAGE_INCOMPATIBLE) {
                backScreen = createBackBuffer();
            }

            drawGraphics = (Graphics2D) backScreen.getGraphics();

            // Clear the back buffer
            drawGraphics.setBackground(new Color(0.0f, 0.0f, 0.0f, 0.0f));
            drawGraphics.clearRect(0, 0, backScreen.getWidth(), backScreen.getWidth());
            img = new BufferedImage(backScreen.getWidth(), backScreen.getHeight(), BufferedImage.TYPE_INT_RGB);
            pixels = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        } else {
            // Get hold of a graphics context for the accelerated surface
            drawGraphics = (Graphics2D) strategy.getDrawGraphics();
        }

        return drawGraphics;
    }

    /*
     * Show a new frame based on the graphics object.
     */
    public void show() {
        if (showFPS) {
            drawGraphics.setColor(Color.white);
            drawGraphics.drawString(String.format(Locale.US, "%.1f", getFPS()), 10, prevY - 10);
        }

        if (resize) {
            // Get hold of a graphics context for the accelerated surface
            Graphics2D g = (Graphics2D) strategy.getDrawGraphics();

            // If contensLost. Too bad...
            g.drawImage(backScreen, 0, 0, null);
            drawGraphics.dispose();
            drawGraphics = g;
        }

        drawGraphics.dispose();
        drawGraphics = null;

        strategy.show();
        fpsCounter.countFrame();
    }

    /*
     * Create a backbuffer to draw on.
     */
    private VolatileImage createBackBuffer() {
        VolatileImage image = null;

        // Get the size of the screen
        int width = graphicsDev.getDisplayMode().getWidth();
        int height = graphicsDev.getDisplayMode().getHeight();

        image = graphicsConf.createCompatibleVolatileImage(width, height, Transparency.TRANSLUCENT);

        int valid = image.validate(graphicsConf);

        if (valid == VolatileImage.IMAGE_INCOMPATIBLE) {
            image = this.createBackBuffer();
            return image;
        }

        return image;
    }

    /*
     * Reset resizing variables
     */
    private void resetResizingVariables() {
        Dimension winSize = canvas.getSize();
        prevX = winSize.width;
        prevY = winSize.height;

        resize = false;

        // Cleanup VRAM
        if (backScreen != null) {
            backScreen.flush();
            backScreen = null;
        }
    }

    /*
     * Close the game window and return resources to the OS.
     */
    public void close() {
        if (jFrame != null) {
            // Real FSEM mode
            if (isFSEM && !useFakeFSEM) {
                graphicsDev.setFullScreenWindow(null);
            }
            jFrame.setVisible(false);
            jFrame.dispose();
        }

        isWindow = false;
        isFSEM = false;
    }

    /*
     * Switch between display modes.
     */
    public void changeDisplayMode(DisplayMode displayMode) {
        if (isWindow && !isFSEM) {
            maximized = (jFrame.getExtendedState() == JFrame.MAXIMIZED_BOTH);
            if (!maximized) {
                sizeX = jFrame.getWidth();
                sizeY = jFrame.getHeight();
                posX = jFrame.getX();
                posY = jFrame.getY();
            }
            setFSEMMode(displayMode);
        } else {
            if (maximized) {
                setWindowedModeMaximized();
            } else {
                setWindowedMode(sizeX, sizeY, posX, posY);
            }
        }
    }

    /*
     * Mouse cursor can't be disabled. Create an transparent cursor for full screen mode.
     */
    private Image createInvisibleCursor() {
        BufferedImage cursorImage = graphicsConf.createCompatibleImage(32, 32, Transparency.TRANSLUCENT);

        Graphics2D gImage = (Graphics2D) cursorImage.getGraphics();
        gImage.setColor(new Color(0.0f, 0.0f, 0.0f, 0.0f));
        gImage.fillRect(0, 0, 32, 32);

        return (Image) cursorImage;
    }

    /*
     * Focus is not always gained on the first try. This method
     * tries to gain focus for ten times with a 100 ms delay
     * between tries
     */
    private void aquireFocus() {
        int numTry = 10;

        do {
            canvas.requestFocusInWindow();

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // Ignore. Not relevant.
            }

            numTry--;
        }
        while (!canvasFocusListener.isFocus() && numTry > 0);
    }

    /*
     * Create the buffering strategy which will allow AWT to manage the accelerated graphics.
     */
    private void createBufferStrategy() {

        if (strategy == null) {
            canvas.createBufferStrategy(2);
            strategy = canvas.getBufferStrategy();
        }
    }

    /*
     * Set a custom mouse cursor
     */
    public void setCustomCursor(Cursor customCursor) {
        this.customCursor = customCursor;
        jFrame.setCursor(customCursor == null ? Cursor.getDefaultCursor() : customCursor);
    }

    /*
     * Get the custom mouse cursor
     */
    public Cursor getCustomCursor() {
        return customCursor;
    }

    /*
     * Show or hide the mouse cursor in FSEM mode
     */
    public void hideMouseCursor(boolean hideMouseCursor) {
        if (this.hideMouseCursor != hideMouseCursor && !isWindow && isFSEM) {
            if (hideMouseCursor) {
                jFrame.setCursor(canvas.getToolkit().createCustomCursor(createInvisibleCursor(), new Point(0, 0), ""));
            } else {
                jFrame.setCursor(customCursor == null ? Cursor.getDefaultCursor() : customCursor);
            }
        }

        this.hideMouseCursor = hideMouseCursor;
    }

    /*
     * Is the mouse cursor Shown or hidden in FSEM mode
     */
    public boolean hideMouseCursor() {
        return hideMouseCursor;
    }

    /*
     * Are we in windowed mode.
     */
    public boolean isWindow() {
        return isWindow;
    }

    /*
     * Are we in FSEM mode.
     */
    public boolean isFSEM() {
        return isFSEM;
    }

    /*
     * Set normal or fake FSEM mode
     */
    public void useFakeFSEM(boolean useFakeFSEM) {
        if (this.useFakeFSEM != useFakeFSEM) {
            this.useFakeFSEM = useFakeFSEM;

            // Actions are only necessary when we are in FSEM mode
            if (!isWindow && isFSEM) {
                // Switch to fake FSEM. First close FSEM mode then
                // return to the desktop before switching
                if (useFakeFSEM) {
                    graphicsDev.setFullScreenWindow(null);
                    setWindowedMode(0, 0, 0, 0, false, false);
                    getDrawGraphics();
                    show();
                    setFSEMMode(null);
                }
                // Switch to FSEM. First return to the
                // desktop before switching
                else {
                    setWindowedMode(0, 0, 0, 0, false, false);
                    getDrawGraphics();
                    show();
                    setFSEMMode(graphicsDev.getDisplayMode());
                }
            }
        }
    }

    /*
     * Get the current FSEM mode
     */
    public boolean isFakeFSEM() {
        return useFakeFSEM;
    }

    /*
     * Set windowed mode. Center window and scale.
     */
    public void setWindowedMode(float scaleX, float scaleY) {
        int screenX = 0;
        int screenY = 0;
        int posX = 0;
        int posY = 0;

        // Get the size of the screen
        int width = graphicsDev.getDisplayMode().getWidth();
        int height = graphicsDev.getDisplayMode().getHeight();

        // Set the window size
        screenX = Math.round(width * scaleX);
        screenY = Math.round(height * scaleY);

        // Set location of JFrame to the center of desktop
        posX = Math.round((width - screenX) / 2.0f);
        posY = Math.round((height - screenY) / 2.0f);

        setWindowedMode(screenX, screenY, posX, posY, false, true);
    }

    /*
     * Set windowed mode. Set window maximized.
     */
    public void setWindowedModeMaximized() {
        setWindowedMode(0, 0, 0, 0, true, true);
    }

    /*
     * Set windowed mode with custom size and location.
     */
    public void setWindowedMode(int sizeX, int sizeY, int posX, int posY) {
        setWindowedMode(sizeX, sizeY, posX, posY, false, true);
    }

    /*
     * Set windowed mode. This mode is by default vsynched.
     */
    private void setWindowedMode(int sizeX, int sizeY, int posX, int posY, boolean maximized, boolean aquireFocus) {
        // When we are already in windowed mode we only have to change
        // the size and position of the window.
        if (isWindow && !isFSEM) {
            setWindowSizeAndPosition(sizeX, sizeY, posX, posY, maximized);
        } else {
            // Close window
            close();

            // Set new mode
            isWindow = true;
            isFSEM = false;

            // Set decorated window and not always on top
            jFrame.setAlwaysOnTop(false);
            jFrame.setUndecorated(false);

            // Make the window visible
            jFrame.pack();
            jFrame.setResizable(true);
            jFrame.setCursor(customCursor == null ? Cursor.getDefaultCursor() : customCursor);
            jFrame.setVisible(true);

            // Set the size and position of the window
            setWindowSizeAndPosition(sizeX, sizeY, posX, posY, maximized);

            // Set focus on window
            if (aquireFocus) {
                aquireFocus();
            }

            // Create the buffer strategy
            createBufferStrategy();

            // Set resize detection variables
            resetResizingVariables();
        }
    }

    /*
     * Change the size and position of the window
     */
    private void setWindowSizeAndPosition(int sizeX, int sizeY, int posX, int posY, boolean maximized) {
        if (maximized) {
            jFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        } else {
            // Set the size and location of the jframe
            jFrame.setSize(sizeX, sizeY);
            jFrame.setLocation(posX, posY);
        }
    }

    public void setFSEMMode(DisplayMode displayMode) {
        if (useFakeFSEM) {
            // Do not reinitialise when already in fake FSEM mode
            if (isWindow && !isFSEM) {
                initFakeFSEMMode();
            }
        } else {
            // Do not change display mode when already in this mode in FSEM
            if ((!graphicsDev.getDisplayMode().equals(displayMode)) || (isWindow && !isFSEM)) {
                // When changing mode in FSEM first return to windowed mode and draw one frame
                // Changing mode with a different resolution is possible without going back
                // to windowed mode, but changing mode for only refresh rate (and possibly
                // bit depth) doesn't work correctly without first switching back to the desktop.
                // So we play it safe for every change in display mode and first return to the desktop.
                if (!isWindow && isFSEM) {
                    setWindowedMode(0, 0, 0, 0, false, false);
                    getDrawGraphics();
                    show();
                }
                initFSEMMode(displayMode);
            }
        }
    }

    /*
     * Set full screen exclusive mode. This mode is by default vsynched.
     * To create a non vsynched full screen display use the fake fsem
     * display mode.
     */
    private void initFSEMMode(DisplayMode displayMode) {
        // Close window
        close();

        // Set new mode
        isWindow = false;
        isFSEM = true;

        // Set undecorated window and always on top
        jFrame.setAlwaysOnTop(true);
        jFrame.setUndecorated(true);

        // Make the window visible
        jFrame.pack();
        jFrame.setResizable(false);
        if (hideMouseCursor) {
            jFrame.setCursor(canvas.getToolkit().createCustomCursor(createInvisibleCursor(), new Point(0, 0), ""));
        }
        jFrame.setVisible(true);

        // Switching Resolution and to Full Screen
        graphicsDev.setFullScreenWindow(jFrame);
        graphicsDev.setDisplayMode(displayMode);

        // Set focus on window
        aquireFocus();

        // Create the buffer strategy
        createBufferStrategy();

        // Set resize detection variables
        resetResizingVariables();
    }

    /*
     * Set fake full screen mode. This mode is non vsynced an has only
     * one resolution. The resolution of the desktop.
     */
    private void initFakeFSEMMode() {
        // Close window
        close();

        // Set new mode
        isWindow = false;
        isFSEM = true;

        // Set undecorated window and always on top
        jFrame.setAlwaysOnTop(true);
        jFrame.setUndecorated(true);

        // Make the window visible
        jFrame.pack();
        jFrame.setResizable(false);
        if (hideMouseCursor) {
            jFrame.setCursor(canvas.getToolkit().createCustomCursor(createInvisibleCursor(), new Point(0, 0), ""));
        }
        jFrame.setVisible(true);

        // Set the FrameSize
        jFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);

        // Set focus on window
        aquireFocus();

        // Create the buffer strategy
        createBufferStrategy();

        // Set resize detection variables
        resetResizingVariables();
    }

    @Override
    public void addKeyListener(KeyAdapter keyAdapter) {
        jFrame.addKeyListener(keyAdapter);
    }

    @Override
    public void setTitle(String rom) {
        jFrame.setTitle(rom);
    }

    @Override
    public void init() {

        addKeyListener(new KeyAdapter() {

            @Override
            public void keyReleased(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_L:
                        genesisProvider.handleNewGame();
                        break;
                }
            }
        });
        showFPS(true);
        setWindowedMode(0.2f, 0.2f);
        useFakeFSEM(false);
        getDrawGraphics();
        img = new BufferedImage(jFrame.getWidth(), jFrame.getHeight(), BufferedImage.TYPE_INT_RGB);
        pixels = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        show();
    }

    @Override
    public void renderScreen(int[][] data, String label, VideoMode videoMode) {
        Dimension sourceScreenSize = ScreenSizeHelper.getScreenSize(videoMode, 1);

        RenderingStrategy.renderSimple(pixels, data, sourceScreenSize, sourceScreenSize, 1);

        // Size of the Canvas
        Dimension windowSize = getSize();
        int screenX = windowSize.width;
        int screenY = windowSize.height;

        // Get hold of a graphics context for the accelerated surface
        Graphics2D g = getDrawGraphics();

        // Set bilinear interpolation
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, screenX, screenY, null);
        g.dispose();

        show();
    }

    @Override
    public void resetScreen() {

    }

    @Override
    public void setFullScreen(boolean value) {

    }


    /*
     * A component listener to set the location to the top left
     * corner of the screen in case of fake full screen mode.
     */
    private class GameFrameListener implements ComponentListener {
        public void componentHidden(ComponentEvent e) {
            // Ignore
        }

        // Reset the frame to the original location
        // Not perfect but does the job. Move can't
        // be disabled...
        public void componentMoved(ComponentEvent e) {
            // Fake FSEM mode
            if (!isWindow && isFSEM && useFakeFSEM) {
                jFrame.setLocation(0, 0);
            }
        }

        public void componentResized(ComponentEvent e) {
            // Ignore
        }

        public void componentShown(ComponentEvent e) {
            // Ignore
        }
    }

    /*
     * A focus listener to check if the window has received focus.
     */
    private class CanvasFocusListener implements FocusListener {
        private boolean isFocus = false;

        public void focusGained(FocusEvent e) {
            isFocus = true;
        }

        public void focusLost(FocusEvent e) {
            isFocus = false;
        }

        public boolean isFocus() {
            return isFocus;
        }
    }

    /*
     * A window listener to implement the actions when closing the window
     */
    private class WindowEventListener implements WindowListener {
        public void windowActivated(WindowEvent e) {
            // ignore
        }

        public void windowClosed(WindowEvent e) {
            // ignore
        }

        /*
         * When an GameWindowListener is provided use this code
         * when closing the window. Otherwise exit the application.
         */
        public void windowClosing(WindowEvent e) {
            //TODO
//            if(gameWindowListener != null)
//            {
//                gameWindowListener.windowClosing();
//            }
//            else
//            {
//                System.exit(0);
//            }
        }

        public void windowDeactivated(WindowEvent e) {
            // ignore
        }

        public void windowDeiconified(WindowEvent e) {
            // ignore
        }

        public void windowIconified(WindowEvent e) {
            // ignore
        }

        public void windowOpened(WindowEvent e) {
            // ignore
        }
    }
}
