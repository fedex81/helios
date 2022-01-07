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

package omegadrive.vdp.md;

import omegadrive.util.VideoMode;
import omegadrive.vdp.VdpRenderDump;
import omegadrive.vdp.md.VdpScrollHandler.HSCROLL;
import omegadrive.vdp.md.VdpScrollHandler.ScrollContext;
import omegadrive.vdp.md.VdpScrollHandler.VSCROLL;
import omegadrive.vdp.model.*;
import omegadrive.vdp.model.VdpMisc.PriorityType;
import omegadrive.vdp.model.VdpMisc.RenderType;
import omegadrive.vdp.model.VdpMisc.ShadowHighlightType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.util.Arrays;
import java.util.function.BiConsumer;

import static omegadrive.vdp.model.BaseVdpProvider.VdpEventListener;
import static omegadrive.vdp.model.GenesisVdpProvider.MAX_SPRITES_PER_LINE_H40;
import static omegadrive.vdp.model.GenesisVdpProvider.VdpRegisterName.*;

public class VdpRenderHandlerImpl implements VdpRenderHandler, VdpEventListener {

    private final static Logger LOG = LogManager.getLogger(VdpRenderHandlerImpl.class.getSimpleName());
    private final GenesisVdpProvider vdpProvider;
    private final VdpMemoryInterface memoryInterface;
    private final VdpScrollHandler scrollHandler;
    private final VdpRenderDump renderDump;
    private int spritesFrame = 0;
    private boolean shadowHighlightMode;
    private VideoMode videoMode;
    private VideoMode newVideoMode;
    private final VdpColorMapper colorMapper;
    private boolean lcb;

    private static final BiConsumer<SpriteDataHolder, SpriteDataHolder> updatePhase1DataFn =
            (src, dest) -> {
                dest.verticalPos = src.verticalPos;
                dest.linkData = src.linkData;
                dest.verticalCellSize = src.verticalCellSize;
                dest.horizontalCellSize = src.horizontalCellSize;
                dest.spriteNumber = src.spriteNumber;
            };
    private SpriteDataHolder[] spriteDataHoldersCurrent = new SpriteDataHolder[MAX_SPRITES_PER_LINE_H40];

    private final int[] planeA = new int[COLS];
    private final int[] planeB = new int[COLS];
    private final int[] planeBack = new int[COLS];
    private final int[] sprites = new int[COLS];
    private int[] linearScreen = new int[0];
    private final SpriteDataHolder spriteDataHolder = new SpriteDataHolder();
    private int spriteTableLocation = 0;
    private int spritePixelLineCount;
    private final ScrollContext scrollContextA;
    private final ScrollContext scrollContextB;
    private final WindowPlaneContext windowPlaneContext;
    private InterlaceMode interlaceMode = InterlaceMode.NONE;
    private final int[] vram;
    private final int[] cram;
    private final int[] javaPalette;
    private int activeLines = 0;
    private SpriteDataHolder[] spriteDataHoldersNext = new SpriteDataHolder[MAX_SPRITES_PER_LINE_H40];
    private final PixelData[] linePixelData = new PixelData[COLS];
    private int odd;

    public static VdpRenderHandler createInstance(GenesisVdpProvider vdpProvider, VdpMemoryInterface memoryInterface) {
        return new VdpRenderHandlerImpl(vdpProvider, memoryInterface);
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

    public VdpRenderHandlerImpl(GenesisVdpProvider vdpProvider, VdpMemoryInterface memoryInterface) {
        this.vdpProvider = vdpProvider;
        this.memoryInterface = memoryInterface;
        this.colorMapper = VdpColorMapper.getInstance();
        this.renderDump = new VdpRenderDump();
        this.scrollHandler = VdpScrollHandler.createInstance(memoryInterface);
        this.vram = memoryInterface.getVram();
        this.cram = memoryInterface.getCram();
        this.javaPalette = memoryInterface.getJavaColorPalette();
        this.scrollContextA = ScrollContext.createInstance(RenderType.PLANE_A, planeA);
        this.scrollContextB = ScrollContext.createInstance(RenderType.PLANE_B, planeB);
        this.windowPlaneContext = new WindowPlaneContext();
        vdpProvider.addVdpEventListener(this);
        for (int i = 0; i < MAX_SPRITES_PER_LINE_H40; i++) {
            spriteDataHoldersCurrent[i] = new SpriteDataHolder();
            spriteDataHoldersNext[i] = new SpriteDataHolder();
        }
        for (int i = 0; i < COLS; i++) {
            linePixelData[i] = new PixelData();
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
        renderBack();
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

        holder.tileIndex = ((((byte4 & 0x7) << 8) | byte5) << interlaceMode.tileShift()) &
                interlaceMode.getTileIndexMask();
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
        Arrays.fill(planeA, 0);
        Arrays.fill(planeB, 0);
        for (int i = 0; i < linePixelData.length; i++) {
            linePixelData[i].reset(shadowHighlightMode);
        }
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

    private void renderSprite(SpriteDataHolder holder, int tileBytePointerBase,
                              int horOffset, int spritePixelLineLimit) {
        final RenderPriority priority = holder.priority ? RenderPriority.SPRITE_PRIO :
                RenderPriority.SPRITE_NO_PRIO;
        for (int tileBytePos = 0; tileBytePos < BYTES_PER_TILE &&
                spritePixelLineCount < spritePixelLineLimit; tileBytePos++, horOffset += 2) {
            spritePixelLineCount += 2;
            int tileShift = tileBytePos ^ (holder.horFlipAmount & 3); //[0,4]
            int tileBytePointer = tileBytePointerBase + tileShift;
            storeSpriteData(priority, tileBytePointer, horOffset, holder, 0);
            storeSpriteData(priority, tileBytePointer, horOffset + 1, holder, 1);
        }
    }

    //    The priority flag takes care of the order between each layer, but between sprites the situation is different
//    (since they're all in the same layer).
//    Sprites are sorted by their order in the sprite table (going by their link order).
//    Sprites earlier in the list show up on top of sprites later in the list (priority flag does nothing here).
// Whichever sprite ends up on top in a given pixel is what will
// end up in the sprite layer (and sorted against plane A and B).
    private void storeSpriteData(RenderPriority priority, int tileBytePointer, int horOffset, SpriteDataHolder holder, int pixelInTile) {
        if (horOffset < 0 || horOffset >= COLS || //Ayrton Senna, TODO check this, can it be removed??
                linePixelData[horOffset].pixelPriority.getRenderType() == RenderType.SPRITE) { //isSpriteAlreadyShown)
            return;
        }
        int pixelIndex = getPixelIndexColor(tileBytePointer, pixelInTile, holder.horFlipAmount);
        int cramIndexColor = holder.paletteLineIndex + (pixelIndex << 1);
        sprites[horOffset] = cramIndexColor;
        updatePixelData(horOffset, priority, sprites[horOffset]);
    }

    private static void getPlaneCells(WindowPlaneContext wpc, int cellWidth) {
        if (wpc.startHCell == wpc.endHCell) {//no window
            wpc.startHCellPlane = 0;
            wpc.endHCellPlane = cellWidth;
        } else if (wpc.startHCell == 0) {
            wpc.startHCellPlane = wpc.endHCell;
            wpc.endHCellPlane = cellWidth;
        } else if (wpc.endHCell == cellWidth) {
            wpc.startHCellPlane = 0;
            wpc.endHCellPlane = wpc.startHCell;
        } else {
            LOG.error("Unexpected windowPlane setup: {}, {}", wpc.startHCell, wpc.endHCell);
            wpc.startHCellPlane = 0;
            wpc.endHCellPlane = cellWidth;
        }
    }

    //shadow/highlight version
    private int getPixelFromLayerSh(int col) {
        final ShadowHighlightType shadowHighlight = processShadowHighlight(col);
        final PixelData pxData = linePixelData[col];
        int cramIndex = planeBack[col];
        int blanking = 1;
        for (int i = RenderPriority.enums.length - 1; i > 0; i--) {
            RenderPriority rp = RenderPriority.enums[i];
            final int rt = rp.getRenderType().ordinal();
            if (pxData.priorityMap[rt] == rp.getPriorityType()
                    && (pxData.cramIndexMap[rt] & CRAM_TRANSP_PIXEL_MASK) != 0) {
                cramIndex = pxData.cramIndexMap[rt];
                blanking = 0;
                break;
            }
        }
        int color = colorMapper.getColor(cram[cramIndex] << 8 | cram[cramIndex + 1],
                shadowHighlight) & ~1;
        return color | blanking;
    }

    private int getPixelFromLayer(RenderPriority rp, int col) {
        switch (rp.getRenderType()) {
            case PLANE_A:
            case WINDOW_PLANE:
                return javaPalette[planeA[col] >> 1] & ~1;
            case PLANE_B:
                return javaPalette[planeB[col] >> 1] & ~1;
            case SPRITE:
                return javaPalette[sprites[col] >> 1] & ~1;
            case BACK_PLANE:
                //lsb (bit) indicates blanking
                return javaPalette[planeBack[col] >> 1] | 1;
            default:
                return -1;
        }
    }

    private ShadowHighlightType processShadowHighlight(int col) {
        final int spriteRt = RenderType.SPRITE.ordinal();
        final PixelData pxData = linePixelData[col];
        final int spriteCramIndex = pxData.cramIndexMap[spriteRt];
        final boolean spriteTransparent = (spriteCramIndex & CRAM_TRANSP_PIXEL_MASK) == 0;
        ShadowHighlightType shadowHighlight = ShadowHighlightType.NORMAL;
        if (!spriteTransparent) {
            switch (spriteCramIndex) {
                case 0x7C: // palette 3, color E (14) = (3*0x10)+E << 1
                    shadowHighlight = shadowHighlight.brighter();
                    pxData.cramIndexMap[spriteRt] = 0;
                    break;
                case 0x7E:  // palette 3, color F (15)
                    shadowHighlight = shadowHighlight.darker();
                    pxData.cramIndexMap[spriteRt] = 0;
                    break;
            }
        }
        boolean spritePalette14 = !spriteTransparent && spriteCramIndex % 0x1C == 0;
        boolean anyLayerHighPrio =
                pxData.priorityMap[RenderType.PLANE_A.ordinal()] == PriorityType.YES ||
                        pxData.priorityMap[RenderType.PLANE_B.ordinal()] == PriorityType.YES ||
                        (!spriteTransparent && pxData.priorityMap[spriteRt] == PriorityType.YES);
        if (!anyLayerHighPrio && !spritePalette14) {
            shadowHighlight = shadowHighlight.darker();
        }
        return shadowHighlight;
    }

    private void renderBack() {
        int reg7 = vdpProvider.getRegisterData(BACKGROUND_COLOR);
        int backLine = (reg7 >> 4) & 0x3;
        int backEntry = (reg7) & 0xF;
        int cramColorIndex = (backLine << 5) + (backEntry << 1);

        if (planeBack[0] != cramColorIndex) {
            //turbo outrun switches h40 -> h32
            Arrays.fill(planeBack, cramColorIndex);
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
                    + ((pointVert & 7) << (2 + interlaceMode.interlaceAdjust()));
            vertLining += odd << 2; //shift by 4 when odd field, 0 otherwise
            for (int cellX = 0; cellX <= holder.horizontalCellSize &&
                    spritePixelLineCount < spritePixelLineLimit; cellX++) {
                int spriteCellX = holder.horFlip ? holder.horizontalCellSize - cellX : cellX;
                int horLining = vertLining + (spriteCellX * ((holder.verticalCellSize + 1) << interlaceMode.tileShift()));
                renderSprite(holder, holder.tileIndex + horLining, horOffset, spritePixelLineLimit);
                horOffset += CELL_WIDTH; //8 pixels
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
        // TODO bit 3 for 128KB VRAM
        renderScrollPlane(line, VdpRenderHandler.getPlaneANameTableLocation(vdpProvider),
                scrollContextA, windowPlaneContext);
    }

    protected void composeImageLinearLine(int line) {
        final int width = videoMode.getDimension().width;
        int k = width * line;
        if (!shadowHighlightMode) { //faster
            for (int col = 0; col < width; col++) {
                linearScreen[k++] = getPixelFromLayer(linePixelData[col].pixelPriority, col);
            }
        } else {
            for (int col = 0; col < width; col++) {
                linearScreen[k++] = getPixelFromLayerSh(col);
            }
        }
        if (lcb) { //left column blank, use BACK_PLANE color
            k = width * line;
            for (int col = 0; col < CELL_WIDTH; col++) {
                linearScreen[k++] = getPixelFromLayer(RenderPriority.BACK_PLANE, col);
            }
        }
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

        getPlaneCells(wpc, limitHorTiles);
        final int startTwoCells = wpc.startHCellPlane >> 1;
        final int endTwoCells = wpc.endHCellPlane >> 1;

        renderPlaneInternal(line, nameTableLocation, startTwoCells, endTwoCells, sc);
    }

    private void renderPlaneInternal(final int line, final int nameTableLocation,
                                     final int startTwoCells, final int endTwoCells, ScrollContext sc) {

        final int vScrollSizeMask = (sc.planeHeight << 3) - 1;
        final int hScrollPixelOffset = scrollHandler.getHorizontalScroll(line, sc);
        final int[] plane = sc.plane;

        TileDataHolder tileDataHolder = spriteDataHolder;
        RenderPriority rp = null;

        for (int twoCell = startTwoCells; twoCell < endTwoCells; twoCell++) {
            int rowCellShift = 0, latestTileLocatorVram = -1;
            final int vScrollLineOffset = scrollHandler.getVerticalScroll(twoCell, sc);
            final int planeLine = (vScrollLineOffset + line) & vScrollSizeMask;
            final int planeCellVOffset = (planeLine >> 3) * sc.planeWidth;
            final int rowCellBase = planeLine & 7; //cellHeight;
            final int startPixel = twoCell << 4;
            for (int pixel = startPixel; pixel < startPixel + 16; pixel++) {
                int planeCellHOffset = ((pixel + hScrollPixelOffset) >> 3) % sc.planeWidth;
                int tileLocatorVram = nameTableLocation + ((planeCellHOffset + planeCellVOffset) << 1);
                int xPosCell = (pixel + hScrollPixelOffset) % CELL_WIDTH;
                if (tileLocatorVram != latestTileLocatorVram) {
                    //one word per 8x8 tile
                    int tileNameTable = vram[tileLocatorVram] << 8 | vram[tileLocatorVram + 1];
                    tileDataHolder = getTileData(tileNameTable, tileDataHolder);
                    latestTileLocatorVram = tileLocatorVram;
                    rp = tileDataHolder.priority ? sc.highPrio : sc.lowPrio;
                    int rowCell = rowCellBase ^ (tileDataHolder.vertFlipAmount & 7); //[0,7]
                    rowCellShift = rowCell << (2 + interlaceMode.interlaceAdjust());
                    rowCellShift += (odd << 2); //shift by 4 when odd field, 0 otherwise
                }
                int colCell = xPosCell ^ (tileDataHolder.horFlipAmount & 7); //[0,7]
                //two pixels per byte, 4 bytes per row
                int tileBytePointer = tileDataHolder.tileIndex + (colCell >> 1) + rowCellShift;
                int onePixelData = getPixelIndexColor(tileBytePointer, xPosCell, tileDataHolder.horFlipAmount);

                plane[pixel] = tileDataHolder.paletteLineIndex + (onePixelData << 1);
                updatePixelData(pixel, rp, plane[pixel]);
//                System.out.printf("\n%s %d-%d, nameTableLocation: %x, tileLocatorVram: %x, " +
//                        "tileBytePointer: %x, cramIdx: %d\n%s", sc.planeType,
//                         line, pixel, nameTableLocation, tileLocatorVram, tileBytePointer, plane[pixel],
//                         tileDataHolder);
            }
        }
    }

    private void drawWindowPlane(final int line, int hCellStart, int hCellEnd, boolean isH40) {
        int lineVCell = line >> 3;
        int nameTableLocation = VdpRenderHandler.getWindowPlaneNameTableLocation(vdpProvider, isH40);
        //do all the screenHCells fit within wPlane width?
        //cant remember why h40 -> 0, but it works...
        int planeTileShift = 32 << (2 - (isH40 ? 0 : 1));
        int tileLocatorVram = nameTableLocation + (planeTileShift * lineVCell) + (hCellStart << 1);
//        System.out.println(String.format("Line: %d, pW: %d, pH: %d, tileStart: %d, tileEnd: %d", line, sc.planeWidth,
//                sc.planeHeight, tileStart, tileEnd));
        int rowInTile = (line % CELL_WIDTH);
        TileDataHolder tileDataHolder = spriteDataHolder;

        for (int hCell = hCellStart; hCell < hCellEnd; hCell++, tileLocatorVram += 2) {
            int tileNameTable = vram[tileLocatorVram] << 8 | vram[tileLocatorVram + 1];
            tileDataHolder = getTileData(tileNameTable, tileDataHolder);
            int pixelVPosTile = tileDataHolder.vertFlip ? CELL_WIDTH - 1 - rowInTile : rowInTile;
            RenderPriority rp = tileDataHolder.priority ? RenderPriority.PLANE_A_PRIO :
                    RenderPriority.PLANE_A_NO_PRIO;

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
                planeA[pos] = val1;
                planeA[pos + 1] = val2;
                updatePixelData(pos, rp, val1);
                updatePixelData(pos + 1, rp, val2);
            }
        }
    }

    private int getPixelIndexColor(int tileBytePointer, int pixelInTile, int horFlipAmount) {
        //1 byte represents 2 pixels, 1 pixel = 4 bit = 16 color gamut
        int twoPixelsData = vram[tileBytePointer & 0xFFFF];
        boolean isFirstPixel = (pixelInTile & 1) == (~horFlipAmount & 1);
        return isFirstPixel ? twoPixelsData & 0x0F : (twoPixelsData & 0xF0) >> 4;
    }

    private void updatePixelData(int pixel, RenderPriority rp, int cramIndex) {
        final int rto = rp.getRenderType().ordinal();
        final PixelData pixelData = linePixelData[pixel];
        pixelData.priorityMap[rto] = rp.getPriorityType();
        pixelData.cramIndexMap[rto] = cramIndex;
        //if non transparent and of higher priority
        if ((cramIndex & CRAM_TRANSP_PIXEL_MASK) != 0 && rp.ordinal() > pixelData.pixelPriority.ordinal()) {
            pixelData.pixelPriority = rp;
        }
    }

    // This value is effectively the address divided by $400; however, the low
    // bit is ignored, so the Window nametable has to be located at a VRAM
    // address that's a multiple of $800. For example, if the Window nametable
    // was to be located at $F000 in VRAM, it would be divided by $400, which
    // results in $3C, the proper value for this register.
    private void renderWindow(int line) {
        windowPlaneContext.reset();
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
            drawWindowPlane(line, hStartCell, hEndCell, isH40);
            windowPlaneContext.startHCell = hStartCell;
            windowPlaneContext.endHCell = hEndCell;
        }
    }

    @Override
    public void onVdpEvent(BaseVdpProvider.VdpEvent event, Object value) {
        switch (event) {
            case VIDEO_MODE:
                setVideoMode((VideoMode) value);
                break;
            case LEFT_COL_BLANK:
                lcb = (boolean) value;
                break;
            case INTERLACE_FIELD_CHANGE:
                odd = INTERLACE_SHOW_ONE_FIELD ? odd : Integer.parseInt(value.toString());
                break;
            case INTERLACE_MODE_CHANGE:
                interlaceMode = (InterlaceMode) value;
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
