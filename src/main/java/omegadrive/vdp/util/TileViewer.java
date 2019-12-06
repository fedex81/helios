package omegadrive.vdp.util;

import omegadrive.util.ImageUtil;
import omegadrive.vdp.gen.VdpRenderHandlerImpl;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.model.InterlaceMode;
import omegadrive.vdp.model.VdpMemoryInterface;
import omegadrive.vdp.model.VdpRenderHandler;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * TileViewer
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public class TileViewer implements UpdatableViewer {

    private static final int PANEL_TEXT_HEIGHT = 20;
    private static final int PANEL_HEIGHT = 600 + PANEL_TEXT_HEIGHT;
    private static final int PANEL_WIDTH = 800;

    private VdpRenderHandlerImpl renderHandler;
    private VdpMemoryInterface memoryInterface;
    private GenesisVdpProvider vdp;
    private int[] javaPalette;
    private JPanel panel;
    private JFrame frame;
    private BufferedImage image;
    private int[] pixels;
    private VdpRenderHandler.TileDataHolder tileDataHolder = new VdpRenderHandler.TileDataHolder();

    public TileViewer(GenesisVdpProvider vdp, VdpMemoryInterface memoryInterface, VdpRenderHandlerImpl renderHandler) {
        this.vdp = vdp;
        this.renderHandler = renderHandler;
        this.memoryInterface = memoryInterface;
        this.javaPalette = memoryInterface.getJavaColorPalette();
        this.frame = new JFrame();
        this.panel = new JPanel();
        initFrame();
    }

    private static int[] getPixels(BufferedImage img) {
        return ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
    }

    public static void main(String[] args) {
        int nameTableLocation = 50;
        int tileNumber = 0;
        int tileLinearShift = 0;
        for (int i = nameTableLocation; i < 80; i += 2, tileNumber++) {
            for (int j = 0; j < 8; j++) { //for each tile row
                for (int k = 0; k < 4; k++) { //for each two pixels
                    int tileCol = k << 1;
                    int px = tileLinearShift + (j << 3) + tileCol;
                    int memLoc = (j << 2) + k;
//                    System.out.println("t" + tileNumber + ",(" + tileCol + "," + j + "): " + px);
//                    System.out.println("t" + tileNumber + ",(" + (tileCol + 1) + "," + j + "): " + (px + 1));

                    System.out.println("t" + tileNumber + ",(" + tileCol + "," + j + "): " + memLoc);
                }
            }
            tileLinearShift += 64; //64 pixels per tile
        }
    }

    private void initFrame() {
        initPanel();
        SwingUtilities.invokeLater(() -> {
            this.frame = new JFrame();
            frame.add(panel);
            frame.setMinimumSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
            frame.setTitle("Tile Viewer");
            frame.pack();
            frame.setVisible(true);
        });
    }

    private void initPanel() {
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        image = ImageUtil.createImage(gd, new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
        pixels = getPixels(image);
        String name = "Window Plane Tiles";
        this.panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (g instanceof Graphics2D) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

                    g2.drawString(name, 70, 10);
                    g2.drawImage(image, 0, 10, this);
                }
            }
        };
        panel.setBackground(Color.BLACK);
        panel.setForeground(Color.WHITE);
        panel.setName(name);
        panel.setSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
    }

    //64 * 32 cells
    @Override
    public void update() {
        int nameTableLocation = VdpRenderHandler.getWindowPlaneNameTableLocation(vdp, true);
        int[] vram = vdp.getVdpMemory().getVram();
        this.javaPalette = memoryInterface.getJavaColorPalette();
        int tileNumber = 0;
        int tileLinearShift = 0;
        int maxMemory = PANEL_WIDTH * PANEL_HEIGHT / 2;
        try {
            for (int i = nameTableLocation; i < nameTableLocation + maxMemory && i < vram.length; i += 2, tileNumber++) {
                int vramPointer = vram[i] << 8 | vram[i + 1];
                tileDataHolder = VdpRenderHandlerImpl.getTileData(vramPointer, InterlaceMode.NONE, tileDataHolder);
                for (int j = 0; j < 8; j++) { //for each tile row
                    for (int k = 0; k < 4; k++) { //for each two pixels
                        int rowData = vram[tileDataHolder.tileIndex + (j << 2) + k]; //2 pixels
                        int px1 = rowData & 0xF;
                        int px2 = (rowData & 0xF0) >> 4;
                        int javaColorIndex1 = (tileDataHolder.paletteLineIndex + (px1 << 1)) >> 1;
                        int javaColorIndex2 = (tileDataHolder.paletteLineIndex + (px2 << 1)) >> 1;
                        int px = tileLinearShift + (j << 3) + (k << 1);
                        pixels[px] = javaPalette[javaColorIndex1];
                        pixels[px + 1] = javaPalette[javaColorIndex2];
                    }
                }
                tileLinearShift += 64; //64 pixels per tile
            }
        } catch (Exception e) {
//            e.printStackTrace();
        }
        panel.repaint();
    }
}
