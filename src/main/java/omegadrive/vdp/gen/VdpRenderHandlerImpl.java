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

import static omegadrive.vdp.model.GenesisVdpProvider.MAX_SPRITES_PER_FRAME_H40;
import static omegadrive.vdp.model.GenesisVdpProvider.VdpEventListener;
import static omegadrive.vdp.model.GenesisVdpProvider.VdpRegisterName.*;

public class VdpRenderHandlerImpl implements VdpRenderHandler, VdpEventListener {

    private final static Logger LOG = LogManager.getLogger(VdpRenderHandlerImpl.class.getSimpleName());

    private GenesisVdpProvider vdpProvider;
    private VdpMemoryInterface memoryInterface;
    private VdpScrollHandler scrollHandler;
    private VdpRenderDump renderDump;

    private int spritesFrame = 0;
    private boolean shadowHighlightMode;
    int[] res = new int[0];

    private VideoMode videoMode;
    private VideoMode newVideoMode;
    private VdpColorMapper colorMapper;

    private int[][] spritesPerLine = new int[INDEXES_NUM][MAX_SPRITES_PER_FRAME_H40];

    private int[][] planeA = new int[ROWS][COLS];
    private int[][] planeB = new int[ROWS][COLS];
    private int[][] planeBack = new int[ROWS][COLS];
    private int[][] sprites = new int[ROWS][COLS];

    private int[][] window = new int[ROWS][COLS];

    private RenderPriority[][] pixelPriority = new RenderPriority[ROWS][COLS];
    private ShadowHighlightType[][] shadowHighlight = new ShadowHighlightType[ROWS][COLS];
    private int[] linearScreen;

    private SpriteDataHolder spriteDataHolder = new SpriteDataHolder();
    private int spriteTableLocation = 0;
    private boolean lineShowWindowPlane = false;
    private int spritePixelLineCount;
    private ScrollContext scrollContextA;
    private ScrollContext scrollContextB;

    private InterlaceMode interlaceMode = InterlaceMode.NONE;

    private int[] vram;
    private int[] cram;
    private int[] javaPalette;
    private int activeLines = 0;

    public VdpRenderHandlerImpl(GenesisVdpProvider vdpProvider, VdpMemoryInterface memoryInterface) {
        this.vdpProvider = vdpProvider;
        this.memoryInterface = memoryInterface;
        this.colorMapper = VdpColorMapper.getInstance();
        this.renderDump = new VdpRenderDump();
        this.scrollHandler = VdpScrollHandler.createInstance(memoryInterface);
        this.vram = memoryInterface.getVram();
        this.cram = memoryInterface.getCram();
        this.javaPalette = memoryInterface.getJavaColorPalette();
        this.scrollContextA = ScrollContext.createInstance(true);
        this.scrollContextB = ScrollContext.createInstance(false);
        vdpProvider.addVdpEventListener(this);
        clearData();
    }

    protected void setVideoMode(VideoMode videoMode) {
        this.newVideoMode = videoMode;
        if (this.videoMode == null) {
            initVideoMode(); //force init
        }
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

    @Override
    public void initLineData(int line) {
        if (line == 0) {
            LOG.debug("New Frame");
            this.interlaceMode = vdpProvider.getInterlaceMode();
            initVideoMode();
            //need to do this here so I can dump data just after rendering the frame
            clearData();
            spriteTableLocation = VdpRenderHandler.getSpriteTableLocation(vdpProvider);
            phase1(0);
//            phase1AllLines();
        }
        scrollContextA.hScrollTableLocation = VdpRenderHandler.getHScrollDataLocation(vdpProvider);
        scrollContextB.hScrollTableLocation = scrollContextA.hScrollTableLocation;
    }

    public void renderFrame() {
        composeImageLinear();
    }

    private void clearData() {
        Arrays.stream(sprites).forEach(a -> Arrays.fill(a, 0));
        Arrays.stream(spritesPerLine).forEach(a -> Arrays.fill(a, -1));
        Arrays.stream(pixelPriority).forEach(a -> Arrays.fill(a, RenderPriority.BACK_PLANE));
        Arrays.stream(shadowHighlight).forEach(a -> Arrays.fill(a, ShadowHighlightType.NORMAL));
        Arrays.stream(window).forEach(a -> Arrays.fill(a, 0));
        Arrays.stream(planeA).forEach(a -> Arrays.fill(a, 0));
        Arrays.stream(planeB).forEach(a -> Arrays.fill(a, 0));
        spritesFrame = 0;
    }

    private void phase1AllLines() {
        //TODO check that this is counting a sprite once per frame and not once per line
        for (int i = 0; i < spritesPerLine.length; i++) {
            phase1(i);
        }
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
            int baseAddress = spriteTableLocation + (current << 3);
            holder = getPhase1SpriteData(baseAddress, holder);
            next = holder.linkData;
            int verSizePixels = (holder.verticalCellSize + 1) << 3;
            int realY = holder.verticalPos - 128;
            boolean isSpriteOnLine = line >= realY && line < realY + verSizePixels;

            stop = next == 0 || next >= maxSpritesPerFrame;
            if (!isSpriteOnLine) {
                continue;
            }
            spritesPerLine[line][count] = current;
            spritesFrame += line == realY ? 1 : 0;
            count++;

            stop |= count >= maxSpritesPerLine || spritesFrame >= maxSpritesPerFrame;
        }
    }

    //* -Sprite masks at x=0 only work correctly when there's at least one higher priority sprite
//            * on the same line which is not at x=0. (This is what Galaxy Force II relies on)
    private void renderSprites(int line) {
        int[] spritesInLine = spritesPerLine[line];
        int currSprite = spritesInLine[0];
        SpriteDataHolder holder = spriteDataHolder;
        //Sonic intro screen
        int spritePixelLineLimit = VdpRenderHandler.maxSpritesPixelPerLine(videoMode.isH40());
        spritePixelLineCount = 0;

        int ind = 0;
        int baseAddress;
        boolean nonZeroSpriteOnLine = false;
        while (currSprite != -1) {
            baseAddress = spriteTableLocation + (currSprite << 3);
            holder = getSpriteData(baseAddress, holder);
            int verSizePixels = (holder.verticalCellSize + 1) << 3; //8 * sizeInCells
            int realY = holder.verticalPos - 128;
            int realX = holder.horizontalPos - 128;
            int spriteLine = (line - realY) % verSizePixels;
            int pointVert = holder.vertFlip ? verSizePixels - 1 - spriteLine : spriteLine; //8,16,24

            boolean stop = (nonZeroSpriteOnLine && holder.horizontalPos == 0) || spritePixelLineCount >= spritePixelLineLimit;
            if (stop) {
                return;
            }
//            LOG.info("Line: " + line + ", sprite: "+currSprite +", lastSpriteNonZero: "
//                        + "\n" + holder.toString());
            int horOffset = realX;
            int spriteVerticalCell = pointVert >> 3;
            int vertLining = (spriteVerticalCell << interlaceMode.tileShift())
                    + ((pointVert % interlaceMode.getVerticalCellPixelSize()) << 2);
            vertLining = interlaceMode == InterlaceMode.MODE_2 ? vertLining << 1 : vertLining;
            for (int cellX = 0; cellX <= holder.horizontalCellSize &&
                    spritePixelLineCount < spritePixelLineLimit; cellX++) {
                int spriteCellX = holder.horFlip ? holder.horizontalCellSize - cellX : cellX;
                int horLining = vertLining + (spriteCellX * ((holder.verticalCellSize + 1) << interlaceMode.tileShift()));
                renderSprite(line, holder, holder.tileIndex + horLining, horOffset, spritePixelLineLimit);
                //8 pixels
                horOffset += CELL_WIDTH;
            }
            ind++;
            currSprite = spritesInLine[ind];
            nonZeroSpriteOnLine |= holder.horizontalPos != 0;
        }
    }

    private boolean renderSprite(int line, SpriteDataHolder holder, int tileBytePointerBase,
                                 int horOffset, int spritePixelLineLimit) {
        for (int tileBytePos = 0; tileBytePos < BYTES_PER_TILE &&
                spritePixelLineCount < spritePixelLineLimit; tileBytePos++, horOffset += 2) {
            spritePixelLineCount += 2;
            int tileShift = tileBytePos ^ (holder.horFlipAmount & 3); //[0,4]
            int tileBytePointer = tileBytePointerBase + tileShift;
            storeSpriteData(tileBytePointer, horOffset, line, holder, 0);
            storeSpriteData(tileBytePointer, horOffset + 1, line, holder, 1);
        }
        return true;
    }

    //    The priority flag takes care of the order between each layer, but between sprites the situation is different
//    (since they're all in the same layer).
//    Sprites are sorted by their order in the sprite table (going by their link order).
//    Sprites earlier in the list show up on top of sprites later in the list (priority flag does nothing here).
// Whichever sprite ends up on top in a given pixel is what will
// end up in the sprite layer (and sorted against plane A and B).
    private void storeSpriteData(int tileBytePointer, int horOffset, int line, SpriteDataHolder holder, int pixelInTile) {
        if (horOffset < 0 || horOffset >= COLS || //Ayrton Senna
                pixelPriority[line][horOffset].getRenderType() == RenderType.SPRITE) { //isSpriteAlreadyShown)
            return;
        }
        int pixelIndex = getPixelIndexColor(tileBytePointer, pixelInTile, holder.horFlipAmount);
        int cramIndexColor = holder.paletteLineIndex + (pixelIndex << 1);
        cramIndexColor = processShadowHighlightSprite(cramIndexColor, horOffset, line);
        sprites[line][horOffset] = cramIndexColor;
        if (pixelIndex > 0 && cramIndexColor > 0) {
            updatePriority(horOffset, line, holder.priority ? RenderPriority.SPRITE_PRIO : RenderPriority.SPRITE_NO_PRIO);
        }
    }

    private int[] composeImageLinear() {
        int height = videoMode.getDimension().height;
        int width = videoMode.getDimension().width;
        int k = 0;
        for (int line = 0; line < height; line++) {
            for (int col = 0; col < width; col++) {
                linearScreen[k++] = getPixelFromLayer(pixelPriority[line][col], col, line);
            }
        }
        return linearScreen;
    }

    public void renderLine(int line) {
        if (line >= activeLines) {
            return;
        }
        initLineData(line);
        renderBack(line);
        phase1(line + 1);
        boolean disp = vdpProvider.isDisplayEnabled();
        if (!disp) {
            return;
        }
        shadowHighlightMode = vdpProvider.isShadowHighlight();
        renderSprites(line);
        renderWindow(line);
        renderPlaneA(line);
        renderPlaneB(line);
    }

    protected int[] composeImageLinearLine(int line) {
        int width = videoMode.getDimension().width;
        if (res.length != width) {
            res = new int[width];
        }
        int k = 0;
        for (int col = 0; col < width; col++) {
            res[k++] = getPixelFromLayer(pixelPriority[line][col], col, line);
        }
        return res;
    }

    private int getPixelFromLayer(RenderPriority rp, int col, int line) {
        int cramIndex = 0;
        switch (rp.getRenderType()) {
            case PLANE_A:
                cramIndex = planeA[line][col];
                break;
            case PLANE_B:
                cramIndex = planeB[line][col];
                break;
            case SPRITE:
                cramIndex = sprites[line][col];
                break;
            case BACK_PLANE:
                cramIndex = planeBack[line][col];
                break;
            case WINDOW_PLANE:
                cramIndex = window[line][col];
                break;
            default:
                break;
        }
        return processShadowHighlight(shadowHighlightMode, col, line, cramIndex, rp);
    }

    private int processShadowHighlight(boolean shadowHighlightMode, int col, int line, int cramIndex, RenderPriority rp) {
        if (!shadowHighlightMode) {
            return getJavaColorValue(cramIndex);
        }
        ShadowHighlightType type = shadowHighlight[line][col];
        shadowHighlight[line][col] = rp.getPriorityType() == PriorityType.YES ? type.brighter() : type;
        return colorMapper.getColor(cram[cramIndex] << 8 | cram[cramIndex + 1],
                shadowHighlight[line][col].darker());
    }

    private int processShadowHighlightSprite(int cramIndexColor, int col, int line) {
        if (!shadowHighlightMode) {
            return cramIndexColor;
        }
        switch (cramIndexColor) {
            case 0x7C: // palette 3, color E (14) = 3*0x10+E << 1
                shadowHighlight[line][col] = shadowHighlight[line][col].brighter();
                cramIndexColor = 0;
                break;
            case 0x7E:  // palette 3, color F (15)
                shadowHighlight[line][col] = shadowHighlight[line][col].darker();
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

        if (planeBack[line][0] != cramColorIndex) {
            Arrays.fill(planeBack[line], 0, limitHorTiles << 3, cramColorIndex);
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
        if (lineShowWindowPlane) {
            return;
        }
        // TODO bit 3 for 128KB VRAM
        int nameTableLocation = (vdpProvider.getRegisterData(PLANE_A_NAMETABLE) & 0x38) << PLANE_A_SHIFT;
        renderPlane(line, nameTableLocation, scrollContextA);
    }

    //	$04 - Plane B Name Table Location
//	Register 04 - Plane B Name Table Location
//	7	6	5	4	3		2		1		0
//	x	x	x	x	SB16	SB15	SB14	SB13
//	SB15-SB13 defines the upper three bits of the VRAM location of Plane B's nametable.
// This value is effectively the address divided by $2000, meaning that the Plane B nametable
// has to be located at a VRAM address that's a multiple of $2000.
// For example, if the Plane A nametable was to be located at $E000 in VRAM,
// it would be divided by $2000, which results in $07, the proper value for this register.
//	SB16 is only valid if 128 KB mode is enabled, and allows for rebasing the
// Plane B nametable to the second 64 KB of VRAM.
    private void renderPlaneB(int line) {
        int nameTableLocation = (vdpProvider.getRegisterData(PLANE_B_NAMETABLE) & 0x7) << PLANE_B_SHIFT;
        renderPlane(line, nameTableLocation, scrollContextB);
    }

    protected void renderPlane(int line, int nameTableLocation, ScrollContext sc) {
        int limitHorTiles = VdpRenderHandler.getHorizontalTiles(videoMode.isH40());
        final int cellHeight = interlaceMode.getVerticalCellPixelSize();
        int regB = vdpProvider.getRegisterData(MODE_3);
        int reg10 = vdpProvider.getRegisterData(PLANE_SIZE);

        sc.hScrollType = HSCROLL.getHScrollType(regB & 3);
        sc.vScrollType = VSCROLL.getVScrollType((regB >> 2) & 0x1);
        sc.interlaceMode = interlaceMode;
        sc.planeWidth = VdpRenderHandler.getHorizontalPlaneSize(reg10);
        sc.planeHeight = VdpRenderHandler.getVerticalPlaneSize(reg10);

        int vScrollSizeMask = (sc.planeHeight << 3) - 1;
        int hScrollPixelOffset = scrollHandler.getHorizontalScroll(line, sc);

        int[][] plane = sc.planeA ? planeA : planeB;
        TileDataHolder tileDataHolder = spriteDataHolder;

        RenderType renderType = sc.planeA ? RenderType.PLANE_A : RenderType.PLANE_B;
        RenderPriority highPrio = RenderPriority.getRenderPriority(renderType, true);
        RenderPriority lowPrio = RenderPriority.getRenderPriority(renderType, false);

        int vScrollLineOffset, planeCellVOffset, planeLine;
        final int totalTwoCells = limitHorTiles >> 1;

        for (int twoCell = 0; twoCell < totalTwoCells; twoCell++) {
            vScrollLineOffset = scrollHandler.getVerticalScroll(twoCell, sc);
            planeLine = (vScrollLineOffset + line) & vScrollSizeMask;
            planeCellVOffset = (planeLine >> 3) * sc.planeWidth;
            int startPixel = twoCell << 4;
            int currentPrio = 0;
            int rowCellBase = planeLine % 8; //cellHeight;
            for (int pixel = startPixel; pixel < startPixel + 16; pixel++) {
                currentPrio = pixelPriority[line][pixel].ordinal();
                if (currentPrio >= RenderPriority.PLANE_A_PRIO.ordinal()) {
                    continue;
                }

                int planeCellHOffset = ((pixel + hScrollPixelOffset) % (sc.planeWidth << 3)) >> 3;
                int tileLocatorVram = nameTableLocation + ((planeCellHOffset + planeCellVOffset) << 1);
                //one word per 8x8 tile
                int tileNameTable = vram[tileLocatorVram] << 8 | vram[tileLocatorVram + 1];

                tileDataHolder = getTileData(tileNameTable, tileDataHolder);
                RenderPriority rp = tileDataHolder.priority ? highPrio : lowPrio;
                if (currentPrio >= rp.ordinal()) {
                    continue;
                }
                int xPosCell = (pixel + hScrollPixelOffset) % CELL_WIDTH;

                int colCell = xPosCell ^ (spriteDataHolder.horFlipAmount & 7); //[0,7]
                int rowCell = rowCellBase ^ (spriteDataHolder.vertFlipAmount & (cellHeight - 1)); //[0,7] or [0,15] IM2

                //two pixels per byte, 4 bytes per row
                //TODO
                int rowCellShift = interlaceMode == InterlaceMode.MODE_2 ? rowCell << 3 : rowCell << 2;
                int tileBytePointer = tileDataHolder.tileIndex + (colCell >> 1) + rowCellShift;
                int onePixelData = getPixelIndexColor(tileBytePointer, xPosCell, tileDataHolder.horFlipAmount);

                plane[line][pixel] = tileDataHolder.paletteLineIndex + (onePixelData << 1);
                if (onePixelData > 0) {
                    updatePriority(pixel, line, rp);
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

    private int getJavaColorValue(int cramIndex) {
        return javaPalette[cramIndex >> 1];
    }

    // This value is effectively the address divided by $400; however, the low
    // bit is ignored, so the Window nametable has to be located at a VRAM
    // address that's a multiple of $800. For example, if the Window nametable
    // was to be located at $F000 in VRAM, it would be divided by $400, which
    // results in $3C, the proper value for this register.
    private void renderWindow(int line) {
        int reg11 = vdpProvider.getRegisterData(WINDOW_PLANE_HOR_POS);
        int reg12 = vdpProvider.getRegisterData(WINDOW_PLANE_VERT_POS);

        boolean isH40 = videoMode.isH40();
        int hCellTotal = VdpRenderHandler.getHorizontalTiles(isH40);

        boolean down = (reg12 & 0x80) == 0x80;
        boolean right = (reg11 & 0x80) == 0x80;

        int hCell = (reg11 & 0x1F) << 1; //2-cell = 2*8 pixels
        int vCell = (reg12 & 0x1F); // unit of 8 lines
        int lineCell = line >> 3;

//        When DOWN=0, the window is shown from line zero to the line specified
//        by the WVP field.
//        When DOWN=1, the window is shown from the line specified in the WVP
//        field up to the last line in the display.
//        boolean legalVertical = vCellRange != 0;
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
            drawWindowPlane(line, hStartCell, hEndCell, isH40);
        }
        //if the line belongs to the window, do not show planeA
        lineShowWindowPlane = legalVertical;
    }

    @Override
    public void onVdpEvent(BaseVdpProvider.VdpEvent event, Object value) {
        switch (event) {
            case VIDEO_MODE:
                setVideoMode((VideoMode) value);
                break;
            case NEW_FRAME:
                renderFrame();
                break;
            default:
                break;
        }
    }

    @Override
    public void onRegisterChange(int reg, int value) {

    }

    private SpriteDataHolder getPhase1SpriteData(int baseAddress, SpriteDataHolder holder) {
        int byte0 = vram[baseAddress];
        int byte1 = vram[baseAddress + 1];
        int byte2 = vram[baseAddress + 2];
        int byte3 = vram[baseAddress + 3];

        holder.linkData = byte3 & 0x7F;
        holder.verticalPos = ((byte0 & 0x1) << 8) | byte1;
        if (interlaceMode == InterlaceMode.MODE_2) {
            holder.verticalPos = ((byte0 & 0x3) << 7) | (byte1 >> 1);
        }
        holder.verticalCellSize = byte2 & 0x3;
        return holder;
    }

    private SpriteDataHolder getSpriteData(int baseAddress, SpriteDataHolder holder) {
        int byte0 = vram[baseAddress];
        int byte1 = vram[baseAddress + 1];
        int byte2 = vram[baseAddress + 2];
        int byte3 = vram[baseAddress + 3];
        int byte4 = vram[baseAddress + 4];
        int byte5 = vram[baseAddress + 5];
        int byte6 = vram[baseAddress + 6];
        int byte7 = vram[baseAddress + 7];

        holder.verticalPos = ((byte0 & 0x1) << 8) | byte1;
        if (interlaceMode == InterlaceMode.MODE_2) {
            holder.verticalPos = ((byte0 & 0x3) << 7) | (byte1 >> 1);
        }
        holder.horizontalCellSize = (byte2 >> 2) & 0x3;
        holder.verticalCellSize = byte2 & 0x3;
        holder.linkData = byte3 & 0x7F;
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

    private void drawWindowPlane(final int line, int tileStart, int tileEnd, boolean isH40) {
        int vertTile = line >> 3;
        int nameTableLocation = VdpRenderHandler.getWindowPlaneNameTableLocation(vdpProvider, isH40);
        int tileShiftFactor = isH40 ? 128 : 64;
        int tileLocator = nameTableLocation + (tileShiftFactor * vertTile);

        int vramLocation = tileLocator;
        int rowInTile = (line % CELL_WIDTH);
        TileDataHolder tileDataHolder = spriteDataHolder;

        for (int horTile = tileStart; horTile < tileEnd; horTile++) {
            int nameTable = vram[vramLocation] << 8 | vram[vramLocation + 1];
            vramLocation += 2;

            tileDataHolder = getTileData(nameTable, tileDataHolder);
            int pointVert = tileDataHolder.vertFlip ? CELL_WIDTH - 1 - rowInTile : rowInTile;
            RenderPriority rp = tileDataHolder.priority ? RenderPriority.WINDOW_PLANE_PRIO :
                    RenderPriority.WINDOW_PLANE_NO_PRIO;

            //two pixels at a time as they share a tile
            for (int k = 0; k < 4; k++) {
                int point = k ^ (tileDataHolder.horFlipAmount & 3); //[0,4]
                int po = (horTile << 3) + (k << 1);

                int tileBytePointer = (tileDataHolder.tileIndex + point) + (pointVert << 2);

                int pixelIndexColor1 = getPixelIndexColor(tileBytePointer, 0, tileDataHolder.horFlipAmount);
                int pixelIndexColor2 = getPixelIndexColor(tileBytePointer, 1, tileDataHolder.horFlipAmount);

                window[line][po] = tileDataHolder.paletteLineIndex + (pixelIndexColor1 << 1);
                window[line][po + 1] = tileDataHolder.paletteLineIndex + (pixelIndexColor2 << 1);
                if (pixelIndexColor1 > 0) {
                    updatePriority(po, line, rp);
                }
                if (pixelIndexColor2 > 0) {
                    updatePriority(po + 1, line, rp);
                }
            }
        }
    }

    private void updatePriority(int col, int line, RenderPriority rp) {
        RenderPriority prev = pixelPriority[line][col];
        pixelPriority[line][col] = rp.ordinal() > prev.ordinal() ? rp : prev;
    }

    public VideoMode getVideoMode() {
        return videoMode;
    }

    public Object getPlaneData(RenderType type) {
        Object res = null;
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
        Arrays.stream(RenderType.values()).forEach(r -> renderDump.saveRenderObjectToFile(getPlaneData(r), videoMode, r));
    }
}
