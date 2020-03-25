package omegadrive.system.gb;

import eu.rekawek.coffeegb.gpu.Display;
import omegadrive.ui.DisplayWindow;
import omegadrive.ui.SwingWindow;

import java.awt.event.KeyListener;
import java.util.Arrays;

public class HeliosDisplay implements Display {
    public static final int DISPLAY_WIDTH = 160;

    public static final int DISPLAY_HEIGHT = 144;

    public static final int[] COLORS = new int[]{0xe6f8da, 0x99c886, 0x437969, 0x051f2a};
    static final int SCALE = 3;

    static {
        System.setProperty("helios.ui.scale", String.valueOf(SCALE));
    }

    private final int[] rgb;
    private int i;
    private boolean enabled;
    private DisplayWindow window;
    private Gb system;

    public HeliosDisplay() {
        window = new SwingWindow(null);
        window.init();
        rgb = new int[DISPLAY_WIDTH * DISPLAY_HEIGHT];
    }

    public HeliosDisplay(Gb system, DisplayWindow window) {
        this.window = window;
        this.system = system;
        this.rgb = new int[DISPLAY_WIDTH * DISPLAY_HEIGHT];
    }

    public static int translateGbcRgb(int gbcRgb) {
        int r = (gbcRgb >> 0) & 0x1f;
        int g = (gbcRgb >> 5) & 0x1f;
        int b = (gbcRgb >> 10) & 0x1f;
        int result = (r * 8) << 16;
        result |= (g * 8) << 8;
        result |= (b * 8) << 0;
        return result;
    }

    @Override
    public void putDmgPixel(int color) {
        rgb[i++] = COLORS[color];
        i = i % rgb.length;
    }

    @Override
    public void putColorPixel(int gbcRgb) {
        rgb[i++] = translateGbcRgb(gbcRgb);
    }

    @Override
    public void requestRefresh() {
        if (!enabled) {
            Arrays.fill(rgb, 0);
        }
        i = 0;
        system.newFrame();
    }

    @Override
    public void waitForRefresh() {
        //DO NOTHING
    }

    @Override
    public void enableLcd() {
        enabled = true;
    }

    @Override
    public void disableLcd() {
        enabled = false;
    }

    @Override
    public void addKeyListener(KeyListener listener) {
        window.addKeyListener(listener);
    }

    int[] getScreen() {
        return rgb;
    }
}
