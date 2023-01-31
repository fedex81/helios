package omegadrive.vdp.util;

import omegadrive.util.ImageUtil;
import omegadrive.vdp.md.VdpRenderHandlerImpl;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.model.InterlaceMode;
import omegadrive.vdp.model.VdpMemoryInterface;
import omegadrive.vdp.model.VdpRenderHandler;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * TileViewer
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public class TileViewer implements UpdatableViewer {

    private static final int MAX_TILES_PER_LINE = 64;
    private static final int PANEL_TEXT_HEIGHT = 20;
    private static final int PANEL_HEIGHT = 200 + PANEL_TEXT_HEIGHT;
    private static final int PLANE_IMG_HEIGHT = 128;
    private static final int PLANE_IMG_WIDTH = MAX_TILES_PER_LINE * 8;
    private static final int PANEL_WIDTH = PLANE_IMG_WIDTH;

    private final VdpMemoryInterface memoryInterface;
    private final GenesisVdpProvider vdp;
    private int[] javaPalette;
    private JPanel panel;
    private BufferedImage imageA, imageB, imageS, imageW;
    private int[] pixelsA, pixelsB, pixelsS, pixelsW;

    private TileViewer(GenesisVdpProvider vdp, VdpMemoryInterface memoryInterface, VdpRenderHandler renderHandler) {
        this.vdp = vdp;
        VdpRenderHandlerImpl renderHandler1 = (VdpRenderHandlerImpl) renderHandler;
        this.memoryInterface = memoryInterface;
        this.javaPalette = memoryInterface.getJavaColorPalette();
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
        int spriteTextVPos = planeBTilesVPos + imageB.getHeight() + 13;
        int spriteTilesVPos = spriteTextVPos + 10;
        int planeWTextVPos = spriteTextVPos;
        int planeWTilesVPos = spriteTilesVPos;
        int rightHalfPos = PLANE_IMG_WIDTH + 10;
        this.panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (g instanceof Graphics2D g2) {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Color.white);

                    g2.drawString("MD Plane A Tiles", 70, planeATextVPos);
                    g2.drawRect(0, planeATilesVPos, imageA.getWidth() + 1, imageA.getHeight() + 1);
                    g2.drawImage(imageA, 1, planeATilesVPos + 1, this);

                    g2.drawString("MD Plane B Tiles", rightHalfPos + 70, planeBTextVPos);
                    g2.drawRect(rightHalfPos, planeBTilesVPos, imageB.getWidth() + 1, imageB.getHeight() + 1);
                    g2.drawImage(imageB, rightHalfPos + 1, planeBTilesVPos + 1, this);

                    g2.drawString("MD Sprite Tiles", 70, spriteTextVPos);
                    g2.drawImage(imageS, 1, spriteTilesVPos + 1, this);
                    g2.drawRect(0, spriteTilesVPos, imageS.getWidth() + 1, imageS.getHeight() + 1);

                    g2.drawString("MD Window Tiles", rightHalfPos + 70, planeWTextVPos);
                    g2.drawImage(imageW, rightHalfPos + 1, planeWTilesVPos + 1, this);
                    g2.drawRect(rightHalfPos, planeWTilesVPos, imageW.getWidth() + 1, imageW.getHeight() + 1);
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
        int satLoc = VdpRenderHandler.getSpriteTableLocation(vdp, isH40);
        this.javaPalette = memoryInterface.getJavaColorPalette();

        //planeSize -> each cell 16 bit
        int planeADataEnd = getClosestUpperLimit(planeALoc, planeALoc + (planeSize << 1),
                new int[]{planeALoc + (planeSize << 1), planeBLoc, satLoc, planeWLoc, GenesisVdpProvider.VDP_VRAM_SIZE - 1});
        int planeBDataEnd = getClosestUpperLimit(planeBLoc, planeBLoc + (planeSize << 1),
                new int[]{planeBLoc + (planeSize << 1), planeALoc, satLoc, planeWLoc, GenesisVdpProvider.VDP_VRAM_SIZE - 1});
        int planeWDataEnd = getClosestUpperLimit(planeBLoc, planeWLoc + (wPlaneSize << 1),
                new int[]{planeWLoc + (wPlaneSize << 1), planeALoc, satLoc, planeBLoc, GenesisVdpProvider.VDP_VRAM_SIZE - 1});

        showPlaneTiles(pixelsA, planeALoc, planeADataEnd);
        showPlaneTiles(pixelsB, planeBLoc, planeBDataEnd);
        showPlaneTiles(pixelsW, planeWLoc, planeWDataEnd);
        showSpriteTiles(pixelsS, satLoc);
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

    private void showPlaneTiles(int[] pixels, int nameTableLocation, int nameTableEnd) {
        byte[] vram = vdp.getVdpMemory().getVram().array();
        int tileLinearShift = 0;
        int shownTiles = 0;
        Arrays.fill(pixels, 0);
        VdpRenderHandler.TileDataHolder tileDataHolder = new VdpRenderHandler.TileDataHolder();
        try {
            for (int i = nameTableLocation; i < nameTableEnd && i < vram.length; i += 2) {
                int vramPointer = vram[i] << 8 | vram[i + 1];
                //8x8 pixels
                tileDataHolder = VdpRenderHandlerImpl.getTileData(vramPointer, InterlaceMode.NONE, tileDataHolder);
                boolean nonBlank = renderInternal(tileDataHolder, pixels, vram, tileLinearShift);
                if (nonBlank) {
                    tileLinearShift += 8;
                    shownTiles++;
                    if (shownTiles % MAX_TILES_PER_LINE == 0) {
                        tileLinearShift = (shownTiles / 8) * PLANE_IMG_WIDTH;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showSpriteTiles(int[] pixels, int satLoc) {
        ByteBuffer vram = vdp.getVdpMemory().getVram();
        int tileLinearShift = 0;
        int shownTiles = 0;
        int satEnd = Math.min(satLoc + 640, vram.capacity() - 8);
        Arrays.fill(pixels, 0);
        VdpRenderHandler.SpriteDataHolder spriteDataHolder = new VdpRenderHandler.SpriteDataHolder();
        try {
            for (int vramOffset = satLoc; vramOffset < satEnd; vramOffset += 8) {  //8 bytes per sprite
                //8x8 pixels
                spriteDataHolder = VdpRenderHandlerImpl.getSpriteData(vram, vramOffset, InterlaceMode.NONE, spriteDataHolder);
                boolean nonBlank = renderInternal(spriteDataHolder, pixels, vram.array(), tileLinearShift);
                if (nonBlank) {
                    tileLinearShift += 8;
                    shownTiles++;
                    if (shownTiles % MAX_TILES_PER_LINE == 0) {
                        tileLinearShift = (shownTiles / 8) * PLANE_IMG_WIDTH;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean renderInternal(VdpRenderHandler.TileDataHolder tileDataHolder, int[] pixels, byte[] vram, int tileLinearShift) {
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
                    continue;
                }
                pixels[px] = javaPalette[javaColorIndex1];
                pixels[px + 1] = javaPalette[javaColorIndex1];
                nonBlank |= javaColorIndex1 > 0 || javaColorIndex2 > 0;
            }
            tileRowShift += PLANE_IMG_WIDTH;
        }
        return nonBlank;
    }
}
