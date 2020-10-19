/*
 * VdpRenderHandlerImpl
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 10:56
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package omegadrive.vdp.gen;

import omegadrive.util.VideoMode;
import omegadrive.vdp.VdpRenderDump;
import omegadrive.vdp.gen.VdpScrollHandler.HSCROLL;
import omegadrive.vdp.gen.VdpScrollHandler.ScrollContext;
import omegadrive.vdp.gen.VdpScrollHandler.VSCROLL;
import omegadrive.vdp.model.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.util.Arrays;
import java.util.function.BiConsumer;

import static omegadrive.vdp.model.BaseVdpProvider.*;
import static omegadrive.vdp.model.GenesisVdpProvider.MAX_SPRITES_PER_LINE_H40;
import static omegadrive.vdp.model.GenesisVdpProvider.VdpRegisterName.*;

public class VdpRenderHandlerImpl implements VdpRenderHandler, VdpEventListener {

    private final static Logger LOG = LogManager.getLogger(VdpRenderHandlerImpl.class.getSimpleName());
    private GenesisVdpProvider vdpProvider;
    private VdpMemoryInterface memoryInterface;
    private VdpScrollHandler scrollHandler;
    private VdpRenderDump renderDump;
    private int spritesFrame = 0;
    private boolean shadowHighlightMode;
    private VideoMode videoMode;
    private VideoMode newVideoMode;
    private VdpColorMapper colorMapper;

    private static final BiConsumer<SpriteDataHolder, SpriteDataHolder> updatePhase1DataFn =
            (src, dest) -> {
                dest.verticalPos = src.verticalPos;
                dest.linkData = src.linkData;
                dest.verticalCellSize = src.verticalCellSize;
                dest.horizontalCellSize = src.horizontalCellSize;
                dest.spriteNumber = src.spriteNumber;
            };
    private SpriteDataHolder[] spriteDataHoldersCurrent = new SpriteDataHolder[MAX_SPRITES_PER_LINE_H40];

    private int[] planeA = new int[COLS];
    private int[] planeB = new int[COLS];
    private int[] planeBack = new int[COLS];
    private int[] sprites = new int[COLS];
    private int[] window = new int[COLS];
    private RenderPriority[] pixelPriority = new RenderPriority[COLS];
    private ShadowHighlightType[] shadowHighlight = new ShadowHighlightType[COLS];
    private int[] linearScreen = new int[0];
    private SpriteDataHolder spriteDataHolder = new SpriteDataHolder();
    private int spriteTableLocation = 0;
    private int spritePixelLineCount;
    private ScrollContext scrollContextA;
    private ScrollContext scrollContextB;
    private WindowPlaneContext windowPlaneContext;
    private InterlaceMode interlaceMode = InterlaceMode.NONE;
    private int[] vram;
    private int[] cram;
    private int[] javaPalette;
    private int activeLines = 0;
    private SpriteDataHolder[] spriteDataHoldersNext = new SpriteDataHolder[MAX_SPRITES_PER_LINE_H40];

    public static VdpRenderHandler createInstance(GenesisVdpProvider vdpProvider, VdpMemoryInterface memoryInterface) {
        VdpRenderHandler v = new VdpRenderHandlerImpl(vdpProvider, memoryInterface);
        return v;
    }

    public static TileDataHolder getTileData(int nameTable, InterlaceMode interlaceMode, TileDataHolder holder) {
        //				An entry in a name table is 16 bits, and works as follows:
//				15			14 13	12				11		   			10 9 8 7 6 5 4 3 2 1 0
//				Priority	Palette	Vertical Flip	Horizontal Flip		Tile Index
        holder.tileIndex = (nameTable & TILE_INDEX_MASK) << interlaceMode.tileShift();// each tile uses 32/64 bytes

        holder.horFlip = (nameTable & TILE_HOR_FLIP_MASK) > 0;
        holder.vertFlip = (nameTable & TILE_VERT_FLIP_MASK) > 0;
        holder.paletteLineIndex = ((nameTable >> 13) & 0x3) << PALETTE_INDEX_SHIFT;
        holder.priority = (nameTable & TILE_PRIORITY_MASK) > 0;
        holder.horFlipAmount = holder.horFlip ? CELL_WIDTH - 1 : 0;
        holder.vertFlipAmount = holder.vertFlip ? interlaceMode.getVerticalCellPixelSize() - 1 : 0;
        return holder;
    }

    private TileDataHolder getTileData(int nameTable, TileDataHolder holder) {
        return getTileData(nameTable, interlaceMode, holder);
    }

    private void initVideoMode() {
        if (newVideoMode != videoMode) {
            Dimension d = newVideoMode.getDimension();
            linearScreen = new int[d.width * d.height];
            videoMode = newVideoMode;
            activeLines = d.height;
        }
    }

    @Override
    public void updateSatCache(int satLocation, int vramAddress) {

    }

    public VdpRenderHandlerImpl(GenesisVdpProvider vdpProvider, VdpMemoryInterface memoryInterface) {
        this.vdpProvider = vdpProvider;
        this.memoryInterface = memoryInterface;
        this.colorMapper = VdpColorMapper.getInstance();
        this.renderDump = new VdpRenderDump();
        this.scrollHandler = VdpScrollHandler.createInstance(memoryInterface);
        this.vram = memoryInterface.getVram();
        this.cram = memoryInterface.getCram();
        this.javaPalette = memoryInterface.getJavaColorPalette();
        this.scrollContextA = ScrollContext.createInstance(RenderType.PLANE_A);
        this.scrollContextB = ScrollContext.createInstance(RenderType.PLANE_B);
        this.windowPlaneContext = new WindowPlaneContext();
        vdpProvider.addVdpEventListener(this);
        for (int i = 0; i < spriteDataHoldersCurrent.length; i++) {
            spriteDataHoldersCurrent[i] = new SpriteDataHolder();
            spriteDataHoldersNext[i] = new SpriteDataHolder();
        }
        clearDataLine();
        clearDataFrame();
    }

    @Override
    public void renderLine(int line) {
        if (line >= activeLines) {
            return;
        }
        initLineData(line);
        renderBack(line);
        phase1(line + 1);
        boolean disp = vdpProvider.isDisplayEnabled();
        if (!disp) {
            composeImageLinearLine(line);
            return;
        }
        shadowHighlightMode = vdpProvider.isShadowHighlight();
        renderSprites(line);
        renderWindow(line);
        renderPlaneA(line);
        renderPlaneB(line);
        composeImageLinearLine(line);
    }

    public static SpriteDataHolder getSpriteData(int[] vram, int vramOffset,
                                                 InterlaceMode interlaceMode, SpriteDataHolder holder) {
        int byte4 = vram[vramOffset + 4];
        int byte5 = vram[vramOffset + 5];
        int byte6 = vram[vramOffset + 6];
        int byte7 = vram[vramOffset + 7];

        holder.tileIndex = (((byte4 & 0x7) << 8) | byte5) << interlaceMode.tileShift();
        holder.paletteLineIndex = ((byte4 >> 5) & 0x3) << PALETTE_INDEX_SHIFT;
        holder.priority = ((byte4 >> 7) & 0x1) == 1;
        holder.vertFlip = ((byte4 >> 4) & 0x1) == 1;
        holder.horFlip = ((byte4 >> 3) & 0x1) == 1;
        holder.horizontalPos = ((byte6 & 0x1) << 8) | byte7;
        holder.horFlipAmount = holder.horFlip ? (holder.horizontalCellSize << 3) - 1 : 0;
        holder.vertFlipAmount = holder.vertFlip ? (holder.verticalCellSize << 3) - 1 : 0;
        return holder;
    }

    @Override
    public void initLineData(int line) {
        if (line == 0) {
            LOG.debug("New Frame");
            this.interlaceMode = vdpProvider.getInterlaceMode();
            initVideoMode();
            //need to do this here so I can dump data just after rendering the frame
            clearDataFrame();
            phase1(0);
        }
        scrollContextA.hScrollTableLocation = VdpRenderHandler.getHScrollDataLocation(vdpProvider);
        scrollContextB.hScrollTableLocation = scrollContextA.hScrollTableLocation;
        spriteTableLocation = VdpRenderHandler.getSpriteTableLocation(vdpProvider, videoMode.isH40());
        clearDataLine();
    }

    private void clearDataLine() {
        Arrays.fill(sprites, 0);
        Arrays.fill(window, 0);
        Arrays.fill(planeA, 0);
        Arrays.fill(planeB, 0);
        Arrays.fill(pixelPriority, RenderPriority.BACK_PLANE);
        Arrays.fill(shadowHighlight, ShadowHighlightType.NORMAL);
        SpriteDataHolder[] temp = spriteDataHoldersCurrent;
        spriteDataHoldersCurrent = spriteDataHoldersNext;
        for (int i = 0; i < spriteDataHoldersCurrent.length; i++) {
            temp[i].spriteNumber = -1;
        }
        spriteDataHoldersNext = temp;
    }

    private void clearDataFrame() {
        for (int i = 0; i < spriteDataHoldersCurrent.length; i++) {
            spriteDataHoldersCurrent[i].spriteNumber = -1;
            spriteDataHoldersNext[i].spriteNumber = -1;
        }
        spritesFrame = 0;
    }

    private void phase1(int line) {
        boolean isH40 = videoMode.isH40();
        int maxSpritesPerFrame = VdpRenderHandler.maxSpritesPerFrame(isH40);
        int maxSpritesPerLine = VdpRenderHandler.maxSpritesPerLine(isH40);
        int count = 0;

        if (spritesFrame >= maxSpritesPerFrame) {
            return;
        }
        SpriteDataHolder holder = spriteDataHolder;
        int next = 0;
        int current;
        boolean stop = false;
        for (int index = 0; index < maxSpritesPerFrame && !stop; index++) {
            current = next;
            holder = getPhase1SpriteData(current, holder);
            next = holder.linkData;
            int verSizePixels = (holder.verticalCellSize + 1) << 3;
            int realY = holder.verticalPos - 128;
            boolean isSpriteOnLine = line >= realY && line < realY + verSizePixels;

            stop = next == 0 || next >= maxSpritesPerFrame;
            if (!isSpriteOnLine) {
                continue;
            }
            updatePhase1DataFn.accept(holder, spriteDataHoldersNext[count]);
            spritesFrame += line == realY ? 1 : 0;
            count++;

            stop |= count >= maxSpritesPerLine || spritesFrame >= maxSpritesPerFrame;
        }
    }

    private boolean renderSprite(SpriteDataHolder holder, int tileBytePointerBase,
                                 int horOffset, int spritePixelLineLimit) {
        for (int tileBytePos = 0; tileBytePos < BYTES_PER_TILE &&
                spritePixelLineCount < spritePixelLineLimit; tileBytePos++, horOffset += 2) {
            spritePixelLineCount += 2;
            int tileShift = tileBytePos ^ (holder.horFlipAmount & 3); //[0,4]
            int tileBytePointer = tileBytePointerBase + tileShift;
            storeSpriteData(tileBytePointer, horOffset, holder, 0);
            storeSpriteData(tileBytePointer, horOffset + 1, holder, 1);
        }
        return true;
    }

    //    The priority flag takes care of the order between each layer, but between sprites the situation is different
//    (since they're all in the same layer).
//    Sprites are sorted by their order in the sprite table (going by their link order).
//    Sprites earlier in the list show up on top of sprites later in the list (priority flag does nothing here).
// Whichever sprite ends up on top in a given pixel is what will
// end up in the sprite layer (and sorted against plane A and B).
    private void storeSpriteData(int tileBytePointer, int horOffset, SpriteDataHolder holder, int pixelInTile) {
        if (horOffset < 0 || horOffset >= COLS || //Ayrton Senna
                pixelPriority[horOffset].getRenderType() == RenderType.SPRITE) { //isSpriteAlreadyShown)
            return;
        }
        int pixelIndex = getPixelIndexColor(tileBytePointer, pixelInTile, holder.horFlipAmount);
        int cramIndexColor = holder.paletteLineIndex + (pixelIndex << 1);
        cramIndexColor = processShadowHighlightSprite(cramIndexColor, horOffset);
        sprites[horOffset] = cramIndexColor;
        if (pixelIndex > 0 && cramIndexColor > 0) {
            updatePriority(horOffset, holder.priority ? RenderPriority.SPRITE_PRIO : RenderPriority.SPRITE_NO_PRIO);
        }
    }

    protected void composeImageLinearLine(int line) {
        int width = videoMode.getDimension().width;
        int k = width * line;
        for (int col = 0; col < width; col++) {
            linearScreen[k++] = getPixelFromLayer(pixelPriority[col], col);
        }
    }

    private int getPixelFromLayer(RenderPriority rp, int col) {
        int cramIndex = 0;
        switch (rp.getRenderType()) {
            case PLANE_A:
                cramIndex = planeA[col];
                break;
            case PLANE_B:
                cramIndex = planeB[col];
                break;
            case SPRITE:
                cramIndex = sprites[col];
                break;
            case BACK_PLANE:
                cramIndex = planeBack[col];
                break;
            case WINDOW_PLANE:
                cramIndex = window[col];
                break;
            default:
                break;
        }
        return processShadowHighlight(shadowHighlightMode, col, cramIndex, rp);
    }

    private int processShadowHighlight(boolean shadowHighlightMode, int col, int cramIndex, RenderPriority rp) {
        if (!shadowHighlightMode) {
            return javaPalette[cramIndex >> 1];
        }
        ShadowHighlightType type = shadowHighlight[col];
        shadowHighlight[col] = rp.getPriorityType() == PriorityType.YES ? type.brighter() : type;
        return colorMapper.getColor(cram[cramIndex] << 8 | cram[cramIndex + 1],
                shadowHighlight[col].darker());
    }

    private int processShadowHighlightSprite(int cramIndexColor, int col) {
        if (!shadowHighlightMode) {
            return cramIndexColor;
        }
        switch (cramIndexColor) {
            case 0x7C: // palette 3, color E (14) = 3*0x10+E << 1
                shadowHighlight[col] = shadowHighlight[col].brighter();
                cramIndexColor = 0;
                break;
            case 0x7E:  // palette 3, color F (15)
                shadowHighlight[col] = shadowHighlight[col].darker();
                cramIndexColor = 0;
                break;
        }
        return cramIndexColor;
    }

    private void renderBack(int line) {
        int limitHorTiles = VdpRenderHandler.getHorizontalTiles(videoMode.isH40());
        int reg7 = vdpProvider.getRegisterData(BACKGROUND_COLOR);
        int backLine = (reg7 >> 4) & 0x3;
        int backEntry = (reg7) & 0xF;
        int cramColorIndex = (backLine << 5) + (backEntry << 1);

        if (planeBack[0] != cramColorIndex) {
            Arrays.fill(planeBack, 0, limitHorTiles << 3, cramColorIndex);
        }
    }

    //* -Sprite masks at x=0 only work correctly when there's at least one higher priority sprite
//            * on the same line which is not at x=0. (This is what Galaxy Force II relies on)
    private void renderSprites(int line) {
        //Sonic intro screen
        int spritePixelLineLimit = VdpRenderHandler.maxSpritesPixelPerLine(videoMode.isH40());
        int maxSpritesPerLine = VdpRenderHandler.maxSpritesPerLine(videoMode.isH40());
        spritePixelLineCount = 0;
        boolean nonZeroSpriteOnLine = false;

        int ind = 0;
        SpriteDataHolder holder = spriteDataHoldersCurrent[0];
        boolean stop = holder.spriteNumber < 0;
        while (!stop) {
            holder = spriteDataHoldersCurrent[ind];
            holder = getSpriteData(holder);
            int verSizePixels = (holder.verticalCellSize + 1) << 3; //8 * sizeInCells
            int realY = holder.verticalPos - 128;
            int realX = holder.horizontalPos - 128;
            int spriteLine = (line - realY) % verSizePixels;
            int pointVert = holder.vertFlip ? verSizePixels - 1 - spriteLine : spriteLine; //8,16,24

            stop = (nonZeroSpriteOnLine && holder.horizontalPos == 0) || spritePixelLineCount >= spritePixelLineLimit;
            if (stop) {
                return;
            }
//            LOG.info("Line: " + line + ", sprite: "+currSprite +", lastSpriteNonZero: "
//                        + "\n" + holder.toString());
            int horOffset = realX;
            int spriteVerticalCell = pointVert >> 3;
            int vertLining = (spriteVerticalCell << interlaceMode.tileShift())
                    + ((pointVert % interlaceMode.getVerticalCellPixelSize()) << 2);
            vertLining <<= interlaceMode.interlaceAdjust();
            for (int cellX = 0; cellX <= holder.horizontalCellSize &&
                    spritePixelLineCount < spritePixelLineLimit; cellX++) {
                int spriteCellX = holder.horFlip ? holder.horizontalCellSize - cellX : cellX;
                int horLining = vertLining + (spriteCellX * ((holder.verticalCellSize + 1) << interlaceMode.tileShift()));
                renderSprite(holder, holder.tileIndex + horLining, horOffset, spritePixelLineLimit);
                //8 pixels
                horOffset += CELL_WIDTH;
            }
            ind++;
            stop = !(ind < maxSpritesPerLine && spriteDataHoldersCurrent[ind].spriteNumber >= 0);
            nonZeroSpriteOnLine |= holder.horizontalPos != 0;
        }
    }

    //Register 02 - Plane A Name Table Location
//7	6		5		4		3		2	1	0
//x	SA16	SA15	SA14	SA13	x	x	x
//	SA15-SA13 defines the upper three bits of the VRAM location of Plane A's nametable.
// This value is effectively the address divided by $400; however, the low three bits are ignored,
// so the Plane A nametable has to be located at a VRAM address that's a multiple of $2000.
// For example, if the Plane A nametable was to be located at $C000 in VRAM, it would be divided by $400,
// which results in $30, the proper value for this register.
//	SA16 is only valid if 128 KB mode is enabled, and allows for rebasing the Plane A nametable to the second 64 KB of VRAM.
    private void renderPlaneA(int line) {
        if (windowPlaneContext.lineWindow) { //entire line is window plane
            return;
        }
        // TODO bit 3 for 128KB VRAM
        renderScrollPlane(line, VdpRenderHandler.getPlaneANameTableLocation(vdpProvider),
                scrollContextA, windowPlaneContext);
    }

    protected void renderScrollPlane(int line, int nameTableLocation, ScrollContext sc, WindowPlaneContext wpc) {
        int limitHorTiles = VdpRenderHandler.getHorizontalTiles(videoMode.isH40());
        int regB = vdpProvider.getRegisterData(MODE_3);
        int reg10 = vdpProvider.getRegisterData(PLANE_SIZE);

        sc.hScrollType = HSCROLL.getHScrollType(regB & 3);
        sc.vScrollType = VSCROLL.getVScrollType((regB >> 2) & 0x1);
        sc.interlaceMode = interlaceMode;
        sc.planeWidth = VdpRenderHandler.getHorizontalPlaneSize(reg10);
        sc.planeHeight = VdpRenderHandler.getVerticalPlaneSize(reg10);

        final int startTwoCells = wpc.endHCell > 0 && wpc.endHCell < limitHorTiles
                ? wpc.endHCell >> 1 : 0;
        final int endTwoCells = wpc.startHCell > 0 ? wpc.startHCell >> 1 : limitHorTiles >> 1;
        renderPlaneInternal(line, nameTableLocation, startTwoCells, endTwoCells, sc);
    }

    private void renderPlaneInternal(final int line, final int nameTableLocation,
                                     final int startTwoCells, final int endTwoCells, ScrollContext sc) {
        int vScrollLineOffset, planeCellVOffset, planeLine;
        int vScrollSizeMask = (sc.planeHeight << 3) - 1;
        int hScrollPixelOffset = scrollHandler.getHorizontalScroll(line, sc);
        final int cellHeight = interlaceMode.getVerticalCellPixelSize();

        TileDataHolder tileDataHolder = spriteDataHolder;
        RenderPriority highPrio = RenderPriority.getRenderPriority(sc.planeType, true);
        RenderPriority lowPrio = RenderPriority.getRenderPriority(sc.planeType, false);
        int[] plane = sc.planeType == RenderType.PLANE_A ? planeA :
                (sc.planeType == RenderType.PLANE_B ? planeB : window);

        for (int twoCell = startTwoCells; twoCell < endTwoCells; twoCell++) {
            vScrollLineOffset = scrollHandler.getVerticalScroll(twoCell, sc);
            planeLine = (vScrollLineOffset + line) & vScrollSizeMask;
            planeCellVOffset = (planeLine >> 3) * sc.planeWidth;
            int startPixel = twoCell << 4;
            int currentPrio;
            int rowCellBase = planeLine % 8; //cellHeight;
            int latestTileLocatorVram = -1;
            for (int pixel = startPixel; pixel < startPixel + 16; pixel++) {
                currentPrio = pixelPriority[pixel].ordinal();
                if (currentPrio >= RenderPriority.PLANE_A_PRIO.ordinal()) {
                    continue;
                }

                int planeCellHOffset = ((pixel + hScrollPixelOffset) >> 3) % sc.planeWidth;
                int tileLocatorVram = nameTableLocation + ((planeCellHOffset + planeCellVOffset) << 1);
                if (tileLocatorVram != latestTileLocatorVram) {
                    //one word per 8x8 tile
                    int tileNameTable = vram[tileLocatorVram] << 8 | vram[tileLocatorVram + 1];
                    tileDataHolder = getTileData(tileNameTable, tileDataHolder);
                    latestTileLocatorVram = tileLocatorVram;
                }
                RenderPriority rp = tileDataHolder.priority ? highPrio : lowPrio;
                if (currentPrio >= rp.ordinal()) {
                    continue;
                }
                int xPosCell = (pixel + hScrollPixelOffset) % CELL_WIDTH;

                int colCell = xPosCell ^ (spriteDataHolder.horFlipAmount & 7); //[0,7]
                int rowCell = rowCellBase ^ (spriteDataHolder.vertFlipAmount & (cellHeight - 1)); //[0,7] or [0,15] IM2

                //two pixels per byte, 4 bytes per row
                int rowCellShift = rowCell << (2 + interlaceMode.interlaceAdjust());
                int tileBytePointer = tileDataHolder.tileIndex + (colCell >> 1) + rowCellShift;
                int onePixelData = getPixelIndexColor(tileBytePointer, xPosCell, tileDataHolder.horFlipAmount);

                plane[pixel] = tileDataHolder.paletteLineIndex + (onePixelData << 1);
                if (onePixelData > 0) {
                    updatePriority(pixel, rp);
                }
//                System.out.printf("NEW PixelPos %d-%d, nameTableLocation: %x, tileLocatorVram: %x, " +
//                                    "tileBytePointer: %x, cramIdx: %d\n",
//                            line, linePixel, nameTableLocation, tileLocatorVram, tileBytePointer, plane[linePixel]);
            }
        }
    }

    private void drawWindowPlane(final int line, int hCellStart, int hCellEnd, int wPlaneWidth, boolean isH40) {
        int lineVCell = line >> 3;
        int nameTableLocation = VdpRenderHandler.getWindowPlaneNameTableLocation(vdpProvider, isH40);
        //do all the screenHCells fit within wPlane width?
        int planeTileShift = wPlaneWidth << (2 - (wPlaneWidth / (isH40 ? H40_TILES : H32_TILES)));
        int tileLocatorVram = nameTableLocation + (planeTileShift * lineVCell) + (hCellStart << 1);
//        System.out.println(String.format("Line: %d, pW: %d, pH: %d, tileStart: %d, tileEnd: %d", line, sc.planeWidth,
//                sc.planeHeight, tileStart, tileEnd));
        int rowInTile = (line % CELL_WIDTH);
        TileDataHolder tileDataHolder = spriteDataHolder;

        for (int hCell = hCellStart; hCell < hCellEnd; hCell++, tileLocatorVram += 2) {
            int tileNameTable = vram[tileLocatorVram] << 8 | vram[tileLocatorVram + 1];
            tileDataHolder = getTileData(tileNameTable, tileDataHolder);
            int pixelVPosTile = tileDataHolder.vertFlip ? CELL_WIDTH - 1 - rowInTile : rowInTile;
            RenderPriority rp = tileDataHolder.priority ? RenderPriority.WINDOW_PLANE_PRIO :
                    RenderPriority.WINDOW_PLANE_NO_PRIO;

            //two pixels at a time as they share a tile
            for (int k = 0; k < 4; k++) {
                int pixelHPosTile = k ^ (tileDataHolder.horFlipAmount & 3); //[0,4]
                int pos = (hCell << 3) + (k << 1);

                int tileBytePointer = (tileDataHolder.tileIndex + pixelHPosTile) + (pixelVPosTile << 2);

                int pixelIndexColor1 = getPixelIndexColor(tileBytePointer, 0, tileDataHolder.horFlipAmount);
                int pixelIndexColor2 = getPixelIndexColor(tileBytePointer, 1, tileDataHolder.horFlipAmount);

                int val1 = tileDataHolder.paletteLineIndex + (pixelIndexColor1 << 1);
                int val2 = tileDataHolder.paletteLineIndex + (pixelIndexColor2 << 1);

//                System.out.printf("OLD PixelPos %d-%d, nameTableLocation: %x, tileLocatorVram: %x, tileBytePointer: %x, " +
//                                    "cramIdx: %d\n", line, po,
//                            nameTableLocation, tileLocatorVram, tileBytePointer, val1);
                window[pos] = val1;
                window[pos + 1] = val2;
                if (pixelIndexColor1 > 0) {
                    updatePriority(pos, rp);
                }
                if (pixelIndexColor2 > 0) {
                    updatePriority(pos + 1, rp);
                }
            }
        }
    }

    private int getPixelIndexColor(int tileBytePointer, int pixelInTile, int horFlipAmount) {
        //1 byte represents 2 pixels, 1 pixel = 4 bit = 16 color gamut
        int twoPixelsData = vram[tileBytePointer & 0xFFFF];
        boolean isFirstPixel = (pixelInTile % 2) == (~horFlipAmount & 1);
        return isFirstPixel ? twoPixelsData & 0x0F : (twoPixelsData & 0xF0) >> 4;
    }

    // This value is effectively the address divided by $400; however, the low
    // bit is ignored, so the Window nametable has to be located at a VRAM
    // address that's a multiple of $800. For example, if the Window nametable
    // was to be located at $F000 in VRAM, it would be divided by $400, which
    // results in $3C, the proper value for this register.
    private void renderWindow(int line) {
        windowPlaneContext.endHCell = windowPlaneContext.startHCell = 0;
        windowPlaneContext.lineWindow = false;

        int reg11 = vdpProvider.getRegisterData(WINDOW_PLANE_HOR_POS);
        int reg12 = vdpProvider.getRegisterData(WINDOW_PLANE_VERT_POS);

        boolean isH40 = videoMode.isH40();
        int hCellTotal = VdpRenderHandler.getHorizontalTiles(isH40);

        boolean down = (reg12 & 0x80) == 0x80;
        boolean right = (reg11 & 0x80) == 0x80;

        final int hCell = (reg11 & 0x1F) << 1; //2-cell = 2*8 pixels
        final int vCell = (reg12 & 0x1F); // unit of 8 lines
        int lineCell = line >> 3;

//        When DOWN=0, the window is shown from line zero to the line specified
//        by the WVP field.
//        When DOWN=1, the window is shown from the line specified in the WVP
//        field up to the last line in the display.
        boolean legalDown = (down && lineCell >= vCell);
        boolean legalUp = (!down && lineCell < vCell);
        boolean legalVertical = (legalDown || legalUp);

        int hStartCell = right ? Math.min(hCell, hCellTotal) : 0;
        int hEndCell = right ? hCellTotal : Math.min(hCell, hCellTotal);
        //if the line belongs to the window, the entire line becomes window
        hStartCell = legalVertical ? 0 : hStartCell;
        hEndCell = legalVertical ? hCellTotal : hEndCell;

//        When RIGT=0, the window is shown from column zero to the column
//        specified by the WHP field.
//        When RIGHT=1, the window is shown from the column specified in the WHP
//        field up to the last column in the display meaning column 31 or 39
//        depending on the screen width setting.
        boolean legalHorizontal = hStartCell < hEndCell;
        boolean drawWindow = legalVertical || legalHorizontal;

        if (drawWindow) {
//            System.out.println(String.format("Line: %d, h40: %b, hPosCell: %d, vPosCell: %d, lineCell: %d, down: %b, " +
//                            "right: %b, hStartCell: %d, hEndCell: %d",
//                    line, isH40, hCell,
//                    vCell, lineCell, down, right, hStartCell, hEndCell));
            int wPlaneWidth = VdpRenderHandler.getHorizontalPlaneSize(vdpProvider.getRegisterData(PLANE_SIZE));
            drawWindowPlane(line, hStartCell, hEndCell, wPlaneWidth, isH40);
            windowPlaneContext.startHCell = hStartCell;
            windowPlaneContext.endHCell = hEndCell;
            windowPlaneContext.lineWindow = legalVertical;
        }
    }

    @Override
    public void onVdpEvent(BaseVdpProvider.VdpEvent event, Object value) {
        switch (event) {
            case VIDEO_MODE:
                setVideoMode((VideoMode) value);
                break;
            default:
                break;
        }
    }

    private SpriteDataHolder getPhase1SpriteData(int spriteIdx, SpriteDataHolder holder) {
        if (spriteIdx + 3 >= 640) {
            LOG.error("Invalid sprite address: {}", spriteIdx); //titan2
            return holder;
        }
        int satAddress = spriteIdx << 3;
        int[] satCache = memoryInterface.getSatCache();
        int byte0 = satCache[satAddress];
        int byte1 = satCache[satAddress + 1];
        int byte2 = satCache[satAddress + 2];
        int byte3 = satCache[satAddress + 3];

        holder.linkData = byte3 & 0x7F;
        holder.verticalPos = ((byte0 & 0x1) << 8) | byte1;
        if (interlaceMode == InterlaceMode.MODE_2) {
            holder.verticalPos = ((byte0 & 0x3) << 7) | (byte1 >> 1);
        }
        holder.verticalCellSize = byte2 & 0x3;
        holder.horizontalCellSize = (byte2 >> 2) & 0x3;
        holder.spriteNumber = spriteIdx;
        return holder;
    }

    private void renderPlaneB(int line) {
        renderScrollPlane(line, VdpRenderHandler.getPlaneBNameTableLocation(vdpProvider),
                scrollContextB, NO_CONTEXT);
    }

    private SpriteDataHolder getSpriteData(SpriteDataHolder holder) {
        int vramOffset = spriteTableLocation + (holder.spriteNumber << 3);
        return getSpriteData(vram, vramOffset, interlaceMode, holder);
    }

    private void updatePriority(int col, RenderPriority rp) {
        RenderPriority prev = pixelPriority[col];
        pixelPriority[col] = rp.ordinal() > prev.ordinal() ? rp : prev;
    }

    public VideoMode getVideoMode() {
        return videoMode;
    }

    private void setVideoMode(VideoMode videoMode) {
        this.newVideoMode = videoMode;
        if (this.videoMode == null) {
            initVideoMode(); //force init
        }
    }

    public int[] getPlaneData(RenderType type) {
        int[] res = null;
        switch (type) {
            case BACK_PLANE:
                res = planeBack;
                break;
            case WINDOW_PLANE:
                res = window;
                break;
            case PLANE_A:
                res = planeA;
                break;
            case PLANE_B:
                res = planeB;
                break;
            case SPRITE:
                res = sprites;
                break;
            case FULL:
                res = linearScreen;
                break;
        }
        return res;
    }

    @Override
    public int[] getScreenDataLinear() {
        return linearScreen;
    }

    @Override
    public void dumpScreenData() {
        Arrays.stream(RenderType.values()).forEach(r -> renderDump.saveRenderToFile(getPlaneData(r), videoMode, r));
    }
}
