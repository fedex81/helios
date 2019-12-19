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
import java.util.Arrays;

/**
 * TileViewer
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public class TileViewer implements UpdatableViewer {

    private static final int PANEL_HEIGHT = 300 + PANEL_TEXT_HEIGHT;
    private static final int PANEL_TEXT_HEIGHT = 20;
    private static final int PLANE_IMG_HEIGHT = 128;
    private static final int PANEL_WIDTH = PLANE_IMG_WIDTH;
    private static final int PLANE_IMG_WIDTH = MAX_TILES_PER_LINE * 8;
    private static int MAX_TILES_PER_LINE = 64;

    private VdpRenderHandlerImpl renderHandler;
    private VdpMemoryInterface memoryInterface;
    private GenesisVdpProvider vdp;
    private int[] javaPalette;
    private JPanel panel;
    private JFrame frame;
    private BufferedImage imageA, imageB, imageS, imageW;
    private int[] pixelsA, pixelsB, pixelsS, pixelsW;
    private VdpRenderHandler.TileDataHolder tileDataHolder = new VdpRenderHandler.TileDataHolder();
    private VdpRenderHandler.SpriteDataHolder spriteDataHolder = new VdpRenderHandler.SpriteDataHolder();

    private TileViewer(GenesisVdpProvider vdp, VdpMemoryInterface memoryInterface, VdpRenderHandler renderHandler) {
        this.vdp = vdp;
        this.renderHandler = (VdpRenderHandlerImpl) renderHandler;
        this.memoryInterface = memoryInterface;
        this.javaPalette = memoryInterface.getJavaColorPalette();
        this.frame = new JFrame();
        this.panel = new JPanel();
        initPanel();
    }

    public static TileViewer createInstance(GenesisVdpProvider vdp, VdpMemoryInterface memoryInterface,
                                            VdpRenderHandler renderHandler) {
        return new TileViewer(vdp, memoryInterface, renderHandler);
    }

    private static int[] getPixels(BufferedImage img) {
        return ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
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
        imageA = ImageUtil.createImage(gd, new Dimension(PLANE_IMG_WIDTH, PLANE_IMG_HEIGHT));
        imageB = ImageUtil.createImage(gd, new Dimension(PLANE_IMG_WIDTH, PLANE_IMG_HEIGHT));
        imageW = ImageUtil.createImage(gd, new Dimension(PLANE_IMG_WIDTH, PLANE_IMG_HEIGHT));
        imageS = ImageUtil.createImage(gd, new Dimension(PLANE_IMG_WIDTH, PLANE_IMG_HEIGHT));
        pixelsA = getPixels(imageA);
        pixelsB = getPixels(imageB);
        pixelsS = getPixels(imageS);
        pixelsW = getPixels(imageW);
        int planeATextVPos = 10;
        int planeATilesVPos = planeATextVPos + 10;
        int planeBTextVPos = planeATextVPos;
        int planeBTilesVPos = planeATilesVPos;
        int spriteTextVPos = planeBTilesVPos + imageB.getHeight() + 10;
        int spriteTilesVPos = spriteTextVPos + 10;
        int planeWTextVPos = spriteTextVPos;
        int planeWTilesVPos = spriteTilesVPos;
        int rightHalfPos = PLANE_IMG_WIDTH + 10;
        this.panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (g instanceof Graphics2D) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

                    g2.drawString("Plane A Tiles", 70, planeATextVPos);
                    g2.drawImage(imageA, 0, planeATilesVPos, this);
                    g2.drawString("Plane B Tiles", rightHalfPos + 70, planeBTextVPos);
                    g2.drawImage(imageB, rightHalfPos, planeBTilesVPos, this);
                    g2.drawString("Sprite Tiles", 70, spriteTextVPos);
                    g2.drawImage(imageS, 0, spriteTilesVPos, this);
                    g2.drawString("Window Tiles", rightHalfPos + 70, planeWTextVPos);
                    g2.drawImage(imageW, rightHalfPos, planeWTilesVPos, this);
                }
            }
        };
        panel.setBackground(Color.BLACK);
        panel.setForeground(Color.WHITE);
//        panel.setName(name);
        panel.setSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
    }

    public JPanel getPanel() {
        return panel;
    }

    @Override
    public void update() {
        int hCellSize = VdpRenderHandler.getHorizontalPlaneSize(vdp.getRegisterData(GenesisVdpProvider.VdpRegisterName.PLANE_SIZE));
        int vCellSize = VdpRenderHandler.getVerticalPlaneSize(vdp.getRegisterData(GenesisVdpProvider.VdpRegisterName.PLANE_SIZE));
        int planeSize = hCellSize * vCellSize;
        boolean isH40 = vdp.getVideoMode().isH40();
        int wPlaneSize = (isH40 ? 40 : 32) * 32;
        int planeALoc = VdpRenderHandler.getPlaneANameTableLocation(vdp);
        int planeBLoc = VdpRenderHandler.getPlaneBNameTableLocation(vdp);
        int planeWLoc = VdpRenderHandler.getWindowPlaneNameTableLocation(vdp, isH40);
        int satLoc = VdpRenderHandler.getSpriteTableLocation(vdp);

        //planeSize -> each cell 16 bit
        int planeADataEnd = getClosestUpperLimit(planeALoc, planeALoc + (planeSize << 1),
                new int[]{planeALoc + (planeSize << 1), planeBLoc, satLoc, planeWLoc, GenesisVdpProvider.VDP_VRAM_SIZE - 1});
        int planeBDataEnd = getClosestUpperLimit(planeBLoc, planeBLoc + (planeSize << 1),
                new int[]{planeBLoc + (planeSize << 1), planeALoc, satLoc, planeWLoc, GenesisVdpProvider.VDP_VRAM_SIZE - 1});
        int planeWDataEnd = getClosestUpperLimit(planeBLoc, planeWLoc + (wPlaneSize << 1),
                new int[]{planeWLoc + (wPlaneSize << 1), planeALoc, satLoc, planeBLoc, GenesisVdpProvider.VDP_VRAM_SIZE - 1});
        int satDataEnd = getClosestUpperLimit(satLoc, GenesisVdpProvider.VDP_VRAM_SIZE - 1,
                new int[]{planeALoc, planeWLoc, planeBLoc, GenesisVdpProvider.VDP_VRAM_SIZE - 1});

        showPlaneTiles(pixelsA, planeALoc, planeADataEnd);
        showPlaneTiles(pixelsB, planeBLoc, planeBDataEnd);
        showPlaneTiles(pixelsW, planeWLoc, planeWDataEnd);
        showSpriteTiles(pixelsS, satLoc, satDataEnd);
        panel.repaint();
    }


    private int getClosestUpperLimit(int value, int defaultVal, int[] values) {
        Arrays.sort(values);
        for (int i = 0; i < values.length; i++) {
            if (values[i] > value) {
                return values[i];
            }
        }
        return defaultVal;
    }

    private void showSpriteTiles(int[] pixels, int nameTableLocation, int nameTableEnd) {
        int[] vram = vdp.getVdpMemory().getVram();
        this.javaPalette = memoryInterface.getJavaColorPalette();
        int tileNumber = 0;
        int tileLinearShift = 0;
        int shownTiles = 0;
        Arrays.fill(pixels, 0);
        try {
            for (int i = nameTableLocation; i < nameTableEnd && i < vram.length - 8; i += 2, tileNumber++) {
                int vramPointer = vram[i] << 8 | vram[i + 1];
                if (vramPointer + 8 > 0xFFFF) {
                    continue;
                }
                //8x8 pixels
                spriteDataHolder = VdpRenderHandlerImpl.getSpriteData(vram, vramPointer, InterlaceMode.NONE, spriteDataHolder);
                int tileRowShift = 0;
                boolean nonBlank = false;
                for (int j = 0; j < 8; j++) { //for each tile row
                    for (int k = 0; k < 4; k++) { //for each two pixels
                        int rowData = vram[tileDataHolder.tileIndex + (j << 2) + k]; //2 pixels
                        int px1 = rowData & 0xF;
                        int px2 = (rowData & 0xF0) >> 4;
                        int javaColorIndex1 = (tileDataHolder.paletteLineIndex + (px1 << 1)) >> 1;
                        int javaColorIndex2 = (tileDataHolder.paletteLineIndex + (px2 << 1)) >> 1;
                        int px = (tileLinearShift + tileRowShift) + (k << 1);
                        if (px > 0xFFFF) {
                            return;
                        }
                        pixels[px] = javaPalette[javaColorIndex1];
                        pixels[px + 1] = javaPalette[javaColorIndex1];
                        nonBlank |= javaColorIndex1 > 0 || javaColorIndex2 > 0;
                    }
                    tileRowShift += PLANE_IMG_WIDTH;
                }
                if (nonBlank) {
                    tileLinearShift += 8;
                    shownTiles++;
                    if (shownTiles % 64 == 0) {
                        tileLinearShift = (shownTiles / 8) * PLANE_IMG_WIDTH;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showPlaneTiles(int[] pixels, int nameTableLocation, int nameTableEnd) {
        int[] vram = vdp.getVdpMemory().getVram();
        this.javaPalette = memoryInterface.getJavaColorPalette();
        int tileNumber = 0;
        int tileLinearShift = 0;
        int shownTiles = 0;
        Arrays.fill(pixels, 0);
        try {
            for (int i = nameTableLocation; i < nameTableEnd && i < vram.length; i += 2, tileNumber++) {
                int vramPointer = vram[i] << 8 | vram[i + 1];
                //8x8 pixels
                tileDataHolder = renderHandler.getTileData(vramPointer, InterlaceMode.NONE, tileDataHolder);
                int tileRowShift = 0;
                boolean nonBlank = false;
                for (int j = 0; j < 8; j++) { //for each tile row
                    for (int k = 0; k < 4; k++) { //for each two pixels
                        int rowData = vram[tileDataHolder.tileIndex + (j << 2) + k]; //2 pixels
                        int px1 = rowData & 0xF;
                        int px2 = (rowData & 0xF0) >> 4;
                        int javaColorIndex1 = (tileDataHolder.paletteLineIndex + (px1 << 1)) >> 1;
                        int javaColorIndex2 = (tileDataHolder.paletteLineIndex + (px2 << 1)) >> 1;
                        int px = (tileLinearShift + tileRowShift) + (k << 1);
                        if (px > 0xFFFF) {
                            return;
                        }
                        pixels[px] = javaPalette[javaColorIndex1];
                        pixels[px + 1] = javaPalette[javaColorIndex1];
                        nonBlank |= javaColorIndex1 > 0 || javaColorIndex2 > 0;
                    }
                    tileRowShift += PLANE_IMG_WIDTH;
                }
                if (nonBlank) {
                    tileLinearShift += 8;
                    shownTiles++;
                    if (shownTiles % 64 == 0) {
                        tileLinearShift = (shownTiles / 8) * PLANE_IMG_WIDTH;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
