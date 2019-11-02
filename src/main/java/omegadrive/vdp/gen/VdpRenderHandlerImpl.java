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

import java.util.Arrays;

import static omegadrive.vdp.model.GenesisVdpProvider.MAX_SPRITES_PER_FRAME_H40;
import static omegadrive.vdp.model.GenesisVdpProvider.VdpEventListener;
import static omegadrive.vdp.model.GenesisVdpProvider.VdpRegisterName.*;

/**
 *
 * TODO Sprite Masking
 * Not quite. My notes from testing:
 * -Sprite masks at x=0 only work correctly when there's at least one higher priority sprite
 * on the same line which is not at x=0. (This is what Galaxy Force II relies on)
 * -Sprite masks at x=0 are never effective when not proceeded by a higher priority sprite
 * not at x=0 on the same line, unless the previous line ended with a sprite pixel overflow,
 * in which case, the mask becomes effective for that line only. If that line also causes a dot overflow,
 * the mask will also be effective on the following line, and so on.
 * -A sprite mask does contribute to the pixel overflow count for a line according to its given dimensions
 * just like any other sprite. Note that sprite masks still count towards the pixel count for a line
 * when sprite masks are the first sprites on a line and are ignored. (This is what Mickey Mania relies on)
 * -A sprite mask at x=0 does NOT stop the VDP from parsing the remainder of sprites for that line,
 * it just prevents it from displaying any more pixels as a result of the sprites it locates on that line.
 * As a result, a sprite pixel overflow can still occur on a line where all sprites were masked,
 * but there were enough dots in the sprites given after the masking sprite on that line to cause a dot overflow.
 *
 * Note point number 2. Nothing emulates that, and my test ROM will be checking for it.
 * I haven't yet confirmed whether a sprite count overflow on the previous line causes this behaviour,
 * or whether only a sprite dot overflow on the previous line triggers it.
 */
public class VdpRenderHandlerImpl implements VdpRenderHandler, VdpEventListener {

    private final static Logger LOG = LogManager.getLogger(VdpRenderHandlerImpl.class.getSimpleName());

    private GenesisVdpProvider vdpProvider;
    private VdpMemoryInterface memoryInterface;
    private VdpScrollHandler scrollHandler;
    private VdpRenderDump renderDump;

    private int spritesFrame = 0;
    private boolean shadowHighlightMode;

    private VideoMode videoMode;
    private VdpColorMapper colorMapper;

    private int[][] spritesPerLine = new int[INDEXES_NUM][MAX_SPRITES_PER_FRAME_H40];

    private int[][] planeA = new int[COLS][ROWS];
    private int[][] planeB = new int[COLS][ROWS];
    private int[][] planeBack = new int[COLS][ROWS];

    private int[][] sprites = new int[COLS][ROWS];
    private int[][] spritesIndex = new int[COLS][ROWS];

    private int[][] window = new int[COLS][ROWS];

    private RenderPriority[][] pixelPriority = new RenderPriority[COLS][ROWS];
    private ShadowHighlightType[][] shadowHighlight = new ShadowHighlightType[COLS][ROWS];

    private int[][] screenData = new int[COLS][ROWS];

    private SpriteDataHolder spriteDataHolder = new SpriteDataHolder();
    private int spriteTableLocation = 0;
    private boolean lineShowWindowPlane = false;
    private int spritePixelLineCount;
    private ScrollContext scrollContextA;
    private ScrollContext scrollContextB;

    private InterlaceMode interlaceMode = InterlaceMode.NONE;

    private int[] vram;
    private int[] javaPalette;

    public VdpRenderHandlerImpl(GenesisVdpProvider vdpProvider, VdpMemoryInterface memoryInterface) {
        this.vdpProvider = vdpProvider;
        this.memoryInterface = memoryInterface;
        this.colorMapper = VdpColorMapper.getInstance();
        this.renderDump = new VdpRenderDump();
        this.scrollHandler = VdpScrollHandler.createInstance(memoryInterface);
        this.vram = memoryInterface.getVram();
        this.javaPalette = memoryInterface.getJavaColorPalette();
        this.scrollContextA = ScrollContext.createInstance(true);
        this.scrollContextB = ScrollContext.createInstance(false);
//        vdpProvider.addVdpEventListener(this);
        clearData();
    }

    public void setVideoMode(VideoMode videoMode) {
        this.videoMode = videoMode;
    }

    private TileDataHolder getTileData(int nameTable, TileDataHolder holder) {
        //				An entry in a name table is 16 bits, and works as follows:
//				15			14 13	12				11		   			10 9 8 7 6 5 4 3 2 1 0
//				Priority	Palette	Vertical Flip	Horizontal Flip		Tile Index
        holder.tileIndex = (nameTable & TILE_INDEX_MASK) << interlaceMode.tileShift();// each tile uses 32/64 bytes

        holder.horFlip = (nameTable & TILE_HOR_FLIP_MASK) > 0;
        holder.vertFlip = (nameTable & TILE_VERT_FLIP_MASK) > 0;
        holder.paletteLineIndex = ((nameTable >> 13) & 0x3) << PALETTE_INDEX_SHIFT;
        holder.priority = (nameTable & TILE_PRIORITY_MASK) > 0;
        return holder;
    }

    @Override
    public void initLineData(int line) {
        if (line == 0) {
            LOG.debug("New Frame");
            this.interlaceMode = vdpProvider.getInterlaceMode();
            //need to do this here so I can dump data just after rendering the frame
            clearData();
            spriteTableLocation = VdpRenderHandler.getSpriteTableLocation(vdpProvider);
            phase1(0);
//            phase1AllLines();
        }
        scrollContextA.hScrollTableLocation = VdpRenderHandler.getHScrollDataLocation(vdpProvider);
        scrollContextB.hScrollTableLocation = scrollContextA.hScrollTableLocation;
    }

    @Override
    public void updateSatCache(int satLocation, int vramAddress) {

    }

    public void renderLine(int line) {
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

    public int[][] renderFrame() {
        return composeImage();
    }

    private void clearData() {
        Arrays.stream(sprites).forEach(a -> Arrays.fill(a, 0));
        Arrays.stream(spritesIndex).forEach(a -> Arrays.fill(a, 0));
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
            int pointVert = holder.vertFlip ? verSizePixels - 1 - spriteLine : spriteLine;

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
            if (horOffset < 0 || horOffset >= COLS || //Ayrton Senna
                    pixelPriority[horOffset][line].getRenderType() == RenderType.SPRITE //isSpriteAlreadyShown
            ) {
                continue;
            }
            int tileShift = holder.horFlip ? BYTES_PER_TILE - 1 - tileBytePos : tileBytePos;
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
        if (horOffset >= COLS) {
            return;
        }
        int cramIndexColor = getPixelIndexColor(tileBytePointer, pixelInTile, holder.horFlip);
        int javaColor = getJavaColorValue(cramIndexColor, holder.paletteLineIndex);
        cramIndexColor = processShadowHighlightSprite(holder.paletteLineIndex + cramIndexColor
                , cramIndexColor, horOffset, line);
        sprites[horOffset][line] = javaColor;
        spritesIndex[horOffset][line] = cramIndexColor;
        if (cramIndexColor > 0) {
            updatePriority(horOffset, line, holder.priority ? RenderPriority.SPRITE_PRIO : RenderPriority.SPRITE_NO_PRIO);
        }
    }

    private int[][] composeImage() {
        int height = videoMode.getDimension().height;
        int width = videoMode.getDimension().width;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                screenData[i][j] = getPixelFromLayer(pixelPriority[i][j], i, j);
            }
        }
        return screenData;
    }

    private int getPixelFromLayer(RenderPriority rp, int i, int j) {
        int javaColor = 0;
        switch (rp.getRenderType()) {
            case BACK_PLANE:
                javaColor = planeBack[i][j];
                break;
            case PLANE_A:
                javaColor = planeA[i][j];
                break;
            case PLANE_B:
                javaColor = planeB[i][j];
                break;
            case WINDOW_PLANE:
                javaColor = window[i][j];
                break;
            case SPRITE:
                javaColor = sprites[i][j];
                break;
        }
        return processShadowHighlight(shadowHighlightMode, shadowHighlight[i][j], javaColor, rp);
    }

    private int processShadowHighlight(boolean shadowHighlightMode, ShadowHighlightType shadowHighlightType,
                                       int javaColor, RenderPriority rp) {
        if (!shadowHighlightMode) {
            return javaColor;
        }
        //TODO fix this
        boolean noChange = rp.getPriorityType() == PriorityType.YES;
//        boolean noChange = priorityType == PriorityType.YES ||
//                (renderType == RenderType.SPRITE && (cramColorIndex % 32) == 28);  //14*2
        //TODO fix this
        shadowHighlightType = noChange ? shadowHighlightType : shadowHighlightType.darker();
        return colorMapper.getJavaColor(javaColor, shadowHighlightType);
    }

    private int processShadowHighlightSprite(int paletteColorIndex, int indexColor, int x, int y) {
        if (!shadowHighlightMode) {
            return indexColor;
        }
        switch (paletteColorIndex) {
            case 0x6E: // palette (3 * 32) + color (14) = 110
                shadowHighlight[x][y] = shadowHighlight[x][y].brighter();
                indexColor = 0;
                break;
            case 0x6F:  // palette (3 * 32) + color (15) = 111
                shadowHighlight[x][y] = shadowHighlight[x][y].darker();
                indexColor = 0;
                break;
        }
        return indexColor;
    }

    private void renderBack(int line) {
        int limitHorTiles = VdpRenderHandler.getHorizontalTiles(videoMode.isH40());
        int reg7 = vdpProvider.getRegisterData(BACKGROUND_COLOR);
        int backLine = (reg7 >> 4) & 0x3;
        int backEntry = (reg7) & 0xF;
        int cramColorIndex = (backLine << 5) + (backEntry << 1);
        int javaColor = getJavaColorValue(cramColorIndex);

        for (int pixel = 0; pixel < (limitHorTiles << 3); pixel++) {
            planeBack[pixel][line] = javaColor;
            pixelPriority[pixel][line] = RenderPriority.BACK_PLANE;
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
            for (int pixel = startPixel; pixel < startPixel + 16; pixel++) {
                currentPrio = pixelPriority[pixel][line].ordinal();
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
                int rowCell = planeLine % cellHeight;
                int xPosCell = (pixel + hScrollPixelOffset) % CELL_WIDTH;

                rowCell = tileDataHolder.vertFlip ? cellHeight - 1 - rowCell : rowCell;
                int colCell = tileDataHolder.horFlip ? CELL_WIDTH - 1 - xPosCell : xPosCell;

                //two pixels per byte, 4 bytes per row
                int tileBytePointer = tileDataHolder.tileIndex + (colCell >> 1) + (rowCell << 2);
                int onePixelData = getPixelIndexColor(tileBytePointer, xPosCell, tileDataHolder.horFlip);

                plane[pixel][line] = getJavaColorValue(tileDataHolder.paletteLineIndex + (onePixelData << 1));
                if (onePixelData > 0) {
                    updatePriority(pixel, line, rp);
                }
            }
        }
    }

    private int getPixelIndexColor(int tileBytePointer, int pixelInTile, boolean isHorizontalFlip) {
        //1 byte represents 2 pixels, 1 pixel = 4 bit = 16 color gamut
        int twoPixelsData = vram[tileBytePointer];
        boolean isFirstPixel = (pixelInTile % 2) == (isHorizontalFlip ? 0 : 1);
        return isFirstPixel ? twoPixelsData & 0x0F : (twoPixelsData & 0xF0) >> 4;
    }

    private int getJavaColorValue(int cramIndex) {
        return javaPalette[cramIndex >> 1];
    }

    private int getJavaColorValue(int pixelIndexColor, int paletteLine) {
        return javaPalette[(paletteLine + (pixelIndexColor << 1)) >> 1];
    }

    // This value is effectively the address divided by $400; however, the low
    // bit is ignored, so the Window nametable has to be located at a VRAM
    // address that's a multiple of $800. For example, if the Window nametable
    // was to be located at $F000 in VRAM, it would be divided by $400, which
    // results in $3C, the proper value for this register.
    private void renderWindow(int line) {
        int reg11 = vdpProvider.getRegisterData(WINDOW_PLANE_HOR_POS);
        int reg12 = vdpProvider.getRegisterData(WINDOW_PLANE_VERT_POS);

        int windowVert = reg12 & 0x1F;
        boolean down = (reg12 & 0x80) == 0x80;

        int windowHorizontal = reg11 & 0x1F;
        boolean right = (reg11 & 0x80) == 0x80;

        int horizontalLimit = windowHorizontal << 4; //2-cell = 2*8 pixels
        int vertLimit = windowVert << 3;

        boolean legalVertical = down || (!down && windowVert != 0);
//        When DOWN=0, the window is shown from line zero to the line specified
//        by the WVP field.
//        When DOWN=1, the window is shown from the line specified in the WVP
//        field up to the last line in the display.
        boolean legalDown = (down && line >= vertLimit);
        boolean legalUp = (!down && line < vertLimit);
        legalVertical &= (legalDown || legalUp);

        boolean legalHorizontal = right || (!right && windowHorizontal != 0);
//        When RIGT=0, the window is shown from column zero to the column
//        specified by the WHP field.
//        When RIGHT=1, the window is shown from the column specified in the WHP
//        field up to the last column in the display meaning column 31 or 39
//        depending on the screen width setting.
        boolean legalRight = right && horizontalLimit < videoMode.getDimension().width;
        legalHorizontal &= legalRight;

        boolean isH40 = videoMode.isH40();
        int limitHorTiles = VdpRenderHandler.getHorizontalTiles(isH40);
        boolean drawWindow = legalVertical || (!legalVertical && legalHorizontal);

        if (drawWindow) {
            //Ayrton Senna -> vertical
            //Bad Omen -> horizontal
            int tileStart = legalHorizontal ? (right ? windowHorizontal << 1 : 0) : 0;
            int tileEnd = legalHorizontal ? (right ? limitHorTiles : windowHorizontal << 1) : limitHorTiles;
            drawWindowPlane(line, tileStart, tileEnd, isH40);
        }
        lineShowWindowPlane = drawWindow;
    }

    @Override
    public void onNewFrame() {

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

            //two pixels at a time as they share a tile
            for (int k = 0; k < 4; k++) {
                int point = tileDataHolder.horFlip ? 3 - k : k;
                int po = (horTile << 3) + (k << 1);

                int tileBytePointer = (tileDataHolder.tileIndex + point) + (pointVert << 2);

                int pixelIndexColor1 = getPixelIndexColor(tileBytePointer, 0, tileDataHolder.horFlip);
                int pixelIndexColor2 = getPixelIndexColor(tileBytePointer, 1, tileDataHolder.horFlip);

                int javaColor1 = getJavaColorValue(pixelIndexColor1, tileDataHolder.paletteLineIndex);
                int javaColor2 = getJavaColorValue(pixelIndexColor2, tileDataHolder.paletteLineIndex);

                window[po][line] = javaColor1;
                window[po + 1][line] = javaColor2;
                RenderPriority rp = tileDataHolder.priority ? RenderPriority.WINDOW_PLANE_PRIO :
                        RenderPriority.WINDOW_PLANE_NO_PRIO;

                if (pixelIndexColor1 > 0) {
                    updatePriority(po, line, rp);
                }
                if (pixelIndexColor2 > 0) {
                    updatePriority(po + 1, line, rp);
                }
            }
        }
    }

    private void updatePriority(int x, int y, RenderPriority rp) {
        RenderPriority prev = pixelPriority[x][y];
        pixelPriority[x][y] = rp.ordinal() > prev.ordinal() ? rp : prev;
    }

    protected int[][] getPlaneData(RenderType type) {

        int[][] res = new int[COLS][ROWS];
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
                res = screenData;
                break;
        }
        return res;
    }

    @Override
    public int[][] getScreenData() {
        return screenData;
    }

    @Override
    public void dumpScreenData() {
        Arrays.stream(RenderType.values()).forEach(r -> renderDump.saveRenderToFile(getPlaneData(r), videoMode, r));
    }
}
