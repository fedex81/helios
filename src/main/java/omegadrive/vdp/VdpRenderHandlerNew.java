package omegadrive.vdp;

import omegadrive.util.Util;
import omegadrive.util.VideoMode;
import omegadrive.vdp.model.IVdpRenderHandler;
import omegadrive.vdp.model.RenderPriority;
import omegadrive.vdp.model.RenderType;
import omegadrive.vdp.model.VdpMemoryInterface;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Based on genefusto GenVdp
 * https://github.com/DarkMoe/genefusto
 *
 * @author DarkMoe
 * <p>
 * Copyright 2018
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
public class VdpRenderHandlerNew implements IVdpRenderHandler {

    private static Logger LOG = LogManager.getLogger(VdpRenderHandlerNew.class.getSimpleName());

    private VdpProvider vdpProvider;
    private VdpMemoryInterface memoryInterface;
    private VdpRenderDump renderDump;

    private int spritesFrame = 0;
    private boolean disp;

    private final static int ROWS = VdpProvider.VDP_VIDEO_ROWS;
    private final static int COLS = VdpProvider.VDP_VIDEO_COLS;
    private final static int INDEXES_NUM = ROWS;

    private VideoMode videoMode;
    private VdpColorMapper colorMapper;

    private int[][] spritesPerLine = new int[INDEXES_NUM][VdpProvider.MAX_SPRITES_PER_FRAME_H40];
    private int[] verticalScrollRes = new int[2];

    private int[][] planeA = new int[COLS][ROWS];
    private int[][] planeB = new int[COLS][ROWS];
    private int[][] planeBack = new int[COLS][ROWS];

    private boolean[][] planePrioA = new boolean[COLS][ROWS];
    private boolean[][] planePrioB = new boolean[COLS][ROWS];

    private int[][] sprites = new int[COLS][ROWS];
    private int[][] spritesIndex = new int[COLS][ROWS];
    private boolean[][] spritesPrio = new boolean[COLS][ROWS];

    private int[][] window = new int[COLS][ROWS];
    private boolean[][] windowPrio = new boolean[COLS][ROWS];

    private RenderPriority[][] pixelPriority = new RenderPriority[COLS][ROWS];

    private int[][] screenData = new int[COLS][ROWS];

    public VdpRenderHandlerNew(VdpProvider vdpProvider, VdpMemoryInterface memoryInterface) {
        this.vdpProvider = vdpProvider;
        this.memoryInterface = memoryInterface;
        this.colorMapper = new VdpColorMapper();
        this.renderDump = new VdpRenderDump();
    }

    public void setVideoMode(VideoMode videoMode) {
        this.videoMode = videoMode;
    }

    private int getHorizontalTiles(boolean isH40) {
        return isH40 ? 40 : 32;
    }

    private int getHorizontalTiles() {
        return getHorizontalTiles(videoMode.isH40());
    }

    private int maxSpritesPerFrame(boolean isH40) {
        return isH40 ? VdpProvider.MAX_SPRITES_PER_FRAME_H40 : VdpProvider.MAX_SPRITES_PER_FRAME_H32;
    }

    private int maxSpritesPerLine(boolean isH40) {
        return isH40 ? VdpProvider.MAX_SPRITES_PER_LINE_H40 : VdpProvider.MAX_SPRITES_PER_LiNE_H32;
    }

    private int getHorizontalPlaneSize() {
        int reg10 = vdpProvider.getRegisterData(0x10);
        int horScrollSize = reg10 & 3;
        switch (horScrollSize) {
            case 0:
                return 32;
            case 0b01:
                return 64;
            case 0b10:
                return 32;
            case 0b11:
                return 128;

        }
        return 0;
    }

    private int getVerticalPlaneSize() {
        int reg10 = vdpProvider.getRegisterData(0x10);
        int horScrollSize = reg10 & 3;
        int vertScrollSize = (reg10 >> 4) & 3;
        switch (vertScrollSize) {
            case 0b00:
                return horScrollSize == 0b10 ? 1 : 32;
            case 0b01:
            case 0b10:
                return horScrollSize == 0b10 ? 1 : (horScrollSize == 0b11 ? 32 : 64);
            case 0b11:
                return horScrollSize == 0b10 ? 1 :
                        (horScrollSize == 0b11 ? 32 : (horScrollSize == 0b01 ? 64 : 128));

        }
        return 0;
    }

    private int getHScrollDataLocation() {
        int regD = vdpProvider.getRegisterData(0xD);
        int hScrollBase = (regD & 0x3F) * 0x400;    //	bit 6 = mode 128k
        return hScrollBase;
    }

    private int getWindowPlaneNameTableLocation(boolean isH40) {
        int reg3 = vdpProvider.getRegisterData(0x3);
        //	WD11 is ignored if the display resolution is 320px wide (H40),
        // which limits the Window nametable address to multiples of $1000.
        // TODO bit 6 = 128k mode
        int nameTableLocation = isH40 ? reg3 & 0x3C : reg3 & 0x3E;
        nameTableLocation *= 0x400;
        return nameTableLocation;
    }

    private TileDataHolder getTileData(int nameTable, TileDataHolder holder) {
        //				An entry in a name table is 16 bits, and works as follows:
//				15			14 13	12				11		   			10 9 8 7 6 5 4 3 2 1 0
//				Priority	Palette	Vertical Flip	Horizontal Flip		Tile Index
        holder.tileIndex = (nameTable & 0x07FF);    // each tile uses 32 bytes

        holder.horFlip = Util.bitSetTest(nameTable, 11);
        holder.vertFlip = Util.bitSetTest(nameTable, 12);
        holder.paletteLineIndex = (nameTable >> 13) & 0x3;
        holder.priority = Util.bitSetTest(nameTable, 15);
        return holder;
    }

    public void renderLine(int line) {
        initFrameData(line);
        renderBack(line);
        disp = vdpProvider.isDisplayEnabled();
        if (!disp) {
            return;
        }
        renderPlaneA(line);
        renderPlaneB(line);
        renderWindow(line);
        renderSprites(line);
        phase1(line + 1);
    }

    private void initFrameData(int line) {
        if (line == 0) {
            //need to do this here so I can dump data just after rendering the frame
            renderSpritesBeforeLine0();
        }
    }

    public int[][] renderFrame() {
        return composeImage();
    }

    private void renderSpritesBeforeLine0() {
        clearData();
        phase1(0);
    }

    //TODO why needed?
    private void clearData() {
        Arrays.stream(sprites).forEach(a -> Arrays.fill(a, 0));
        Arrays.stream(spritesIndex).forEach(a -> Arrays.fill(a, 0));
        Arrays.stream(spritesPrio).forEach(a -> Arrays.fill(a, false));
        Arrays.stream(spritesPerLine).forEach(a -> Arrays.fill(a, -1));
        Arrays.stream(pixelPriority).forEach(a -> Arrays.fill(a, RenderPriority.BACK_PLANE));
        spritesFrame = 0;
    }

    private void phase1(int line) {
        //	AT16 is only valid if 128 KB mode is enabled,
        // and allows for rebasing the Sprite Attribute Table to the second 64 KB of VRAM.
        int spriteTableLoc = vdpProvider.getRegisterData(0x5) & 0x7F;
        int spriteTable = spriteTableLoc * 0x200;

        boolean isH40 = videoMode.isH40();
        int height = videoMode.getDimension().height;
        int maxSpritesPerFrame = maxSpritesPerFrame(isH40);
        int maxSpritesPerLine = maxSpritesPerLine(isH40);
        int count = 0;

        if (spritesFrame >= maxSpritesPerFrame) {
            return;
        }
        SpriteDataHolder holder = new SpriteDataHolder();
        int next = 0;
        for (int index = 0; index < maxSpritesPerFrame; index++) {
            int baseAddress = spriteTable + (next * 8);
            holder = getPhase1SpriteData(baseAddress, holder);

            int verSizePixels = (holder.verSize + 1) * 8;
            int realY = holder.verticalPos - 128;
            boolean isSpriteOnLine = line >= realY && line < realY + verSizePixels;
            if (realY < 0 || realY >= height || !isSpriteOnLine) {
                next = holder.linkData;
                continue;
            }
            spritesPerLine[line][count] = next;
            spritesFrame += line == realY ? 1 : 0;
            count++;
            next = holder.linkData;

            if (next == 0 || next > maxSpritesPerFrame ||
                    count >= maxSpritesPerLine || spritesFrame >= maxSpritesPerFrame) {
                return;
            }
        }
    }

    private void renderSprites(int line) {
        //	AT16 is only valid if 128 KB mode is enabled, and allows
        // for rebasing the Sprite Attribute Table to the second 64 KB of VRAM.
        int spriteTableLoc = vdpProvider.getRegisterData(0x5) & 0x7F;
        int spriteTable = spriteTableLoc * 0x200;

        int[] spritesInLine = spritesPerLine[line];
        int currSprite = spritesInLine[0];
        SpriteDataHolder holder = new SpriteDataHolder();

        int ind = 0;
        int baseAddress;
        while (currSprite != -1) {
//            LOG.info("Showing sprite, Line: " + line + ", spritesLine: " + ind + ", spritesFrame: " + spritesFrame);

            baseAddress = spriteTable + (currSprite * 8);
            holder = getSpriteData(baseAddress, holder);
            int verSizePixels = (holder.verSize + 1) * 8;
            int realY = holder.verticalPos - 128;
            int horOffset = holder.horizontalPos - 128;
            int spriteLine = (line - realY) % verSizePixels;
            int pointVert = holder.vertFlip ? (spriteLine - (verSizePixels - 1)) * -1 : spriteLine;

            for (int cellHor = 0; cellHor < (holder.horSize + 1); cellHor++) {
                //	16 bytes for a 8x8 cell
                //	cada linea dentro de una cell de 8 pixeles, ocupa 4 bytes (o sea, la mitad del ancho en bytes)
                int currentVerticalCell = pointVert / 8;
                int vertLining = (currentVerticalCell * 32) + ((pointVert % 8) * 4);

                int cellH = holder.horFlip ? (cellHor * -1) + holder.horSize : cellHor;
                int horLining = vertLining + (cellH * ((holder.verSize + 1) * 32));
                int tileBytePointerBase = (holder.tileIndex * 0x20) + horLining;
                renderSprite(line, holder, tileBytePointerBase, horOffset);
                //8 pixels
                horOffset += 8;
            }
            ind++;
            currSprite = spritesInLine[ind];
        }
    }

    private boolean renderSprite(int line, SpriteDataHolder holder, int tileBytePointerBase,
                                 int horOffset) {
        int paletteLine = holder.paletteLineIndex * 32;
        for (int i = 0; i < 4; i++) {
            int sliver = holder.horFlip ? (i * -1) + 3 : i;
            int tileBytePointer = tileBytePointerBase + sliver;
            if (tileBytePointer < 0) {
                LOG.warn("Sprite tileBytePointer < 0");
                continue;    //	FIXME guardar en cache de sprites yPos y otros atrib
            }
            int pixelIndexColor1 = getPixelIndexColor(tileBytePointer, 0, holder.horFlip);
            int pixelIndexColor2 = getPixelIndexColor(tileBytePointer, 1, holder.horFlip);
            int colorIndex1 = paletteLine + (pixelIndexColor1 * 2);
            int colorIndex2 = paletteLine + (pixelIndexColor2 * 2);

            storeSpriteData(pixelIndexColor1, horOffset, line, holder.priority, colorIndex1);
            storeSpriteData(pixelIndexColor2, horOffset + 1, line, holder.priority, colorIndex2);
            horOffset += 2;
        }
        return true;
    }

    //    The priority flag takes care of the order between each layer, but between sprites the situation is different
//    (since they're all in the same layer).
//    Sprites are sorted by their order in the sprite table (going by their link order).
//    Sprites earlier in the list show up on top of sprites later in the list (priority flag does nothing here).
// Whichever sprite ends up on top in a given pixel is what will
// end up in the sprite layer (and sorted against plane A and B).
    private void storeSpriteData(int pixel, int horOffset, int line, boolean priority, int colorIndex) {
        if (horOffset < 0 || horOffset >= COLS) {
            return;
        }
        boolean isSpriteAlreadyShown = pixelPriority[horOffset][line].getRenderType() == RenderType.SPRITE;
        if (isSpriteAlreadyShown) {
            return;
        }
        int theColor = getColorFromIndex(colorIndex);
        sprites[horOffset][line] = theColor;
        spritesIndex[horOffset][line] = pixel;
        spritesPrio[horOffset][line] = priority;

        if (pixel > 0) {
            updatePriority(horOffset, line, priority ? RenderPriority.SPRITE_PRIO : RenderPriority.SPRITE_NO_PRIO);
        }
    }

    private int[][] composeImage() {
        int height = videoMode.getDimension().height;
        int width = videoMode.getDimension().width;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                RenderPriority rp = pixelPriority[i][j];
                screenData[i][j] = getPixelFromLayer(rp.getRenderType(), i, j);
            }
        }
        return screenData;
    }

    private int getPixelFromLayer(RenderType rt, int i, int j) {
        int pixelColor = 0;
        switch (rt) {
            case BACK_PLANE:
                pixelColor = planeBack[i][j];
                break;
            case PLANE_A:
                pixelColor = planeA[i][j];
                break;
            case PLANE_B:
                pixelColor = planeB[i][j];
                break;
            case WINDOW_PLANE:
                pixelColor = window[i][j];
                break;
            case SPRITE:
                pixelColor = sprites[i][j];
                break;
        }
        return pixelColor;
    }

    private void renderBack(int line) {
        int limitHorTiles = getHorizontalTiles();
        int reg7 = vdpProvider.getRegisterData(0x7);
        int backLine = (reg7 >> 4) & 0x3;
        int backEntry = (reg7) & 0xF;
        int backIndex = (backLine * 32) + (backEntry * 2);
        int backColor = disp ? getColorFromIndex(backIndex) : 0;

        for (int pixel = 0; pixel < (limitHorTiles * 8); pixel++) {
            planeBack[pixel][line] = backColor;
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
        // TODO bit 3 for 128KB VRAM
        int nameTableLocation = (vdpProvider.getRegisterData(2) & 0x38) * 0x400;
        renderPlane(line, nameTableLocation, true);
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
//	SB16 is only valid if 128 KB mode is enabled, and allows for rebasing the Plane B nametable to the second 64 KB of VRAM.
    private void renderPlaneB(int line) {
        int nameTableLocation = (vdpProvider.getRegisterData(4) & 0x7) * 0x2000;
        renderPlane(line, nameTableLocation, false);
    }

    private void renderPlane(int line, int nameTableLocation, boolean isPlaneA) {
        int tileLocator = 0;
        int horizontalPlaneSize = getHorizontalPlaneSize();
        int verticalPlaneSize = getVerticalPlaneSize();
        int limitHorTiles = getHorizontalTiles();
        int hScrollDataLocation = getHScrollDataLocation();

        int regB = vdpProvider.getRegisterData(0xB);
        int HS = regB & 0x3;
        int VS = (regB >> 2) & 0x1;

        //horizontal scrolling
        int horizontalScrollingOffset = horizontalScrolling(line, HS, hScrollDataLocation, horizontalPlaneSize, isPlaneA);

        int[][] plane = isPlaneA ? planeA : planeB;
        boolean[][] planePriority = isPlaneA ? planePrioA : planePrioB;
        TileDataHolder tileDataHolder = new TileDataHolder();

        RenderType renderType = isPlaneA ? RenderType.PLANE_A : RenderType.PLANE_B;
        RenderPriority highPrio = RenderPriority.getRenderPriority(renderType, true);
        RenderPriority lowPrio = RenderPriority.getRenderPriority(renderType, false);

        for (int pixel = 0; pixel < (limitHorTiles * 8); pixel++) {
            int tileLocation = (((pixel + horizontalScrollingOffset)) % (horizontalPlaneSize * 8)) / 8;

            int[] res = verticalScrolling(line, VS, pixel, nameTableLocation, verticalPlaneSize, isPlaneA);
            int verticalScrollingOffset = res[0];
            int scrollMap = res[1];
            tileLocation = tileLocator + (tileLocation * 2);
            tileLocation += verticalScrollingOffset;

            int tileNameTable = memoryInterface.readVideoRamWord(VdpProvider.VdpRamType.VRAM, tileLocation);

            tileDataHolder = getTileData(tileNameTable, tileDataHolder);

            int paletteLine = tileDataHolder.paletteLineIndex * 32;    //	16 colors per line, 2 bytes per color

            int tileIndex = tileDataHolder.tileIndex * 0x20;

            int row = (scrollMap % 8);
            int pointVert = tileDataHolder.vertFlip ? (row - 7) * -1 : row;
            int pixelInTile = (pixel + horizontalScrollingOffset) % 8;

            int point = tileDataHolder.horFlip ? (pixelInTile - 7) * -1 : pixelInTile;

            point /= 2;

            int tileBytePointer = (tileIndex + point) + (pointVert * 4);
            int onePixelData = getPixelIndexColor(tileBytePointer, pixelInTile, tileDataHolder.horFlip);
            int theColor = getPixelColorValue(onePixelData, paletteLine);

            // index = 0 -> transparent pixel, but the color value could be any color
            plane[pixel][line] = theColor;
            planePriority[pixel][line] = tileDataHolder.priority;

            if (onePixelData > 0) {
                updatePriority(pixel, line, tileDataHolder.priority ? highPrio : lowPrio);
            }
        }
    }

    private int getPixelIndexColor(int tileBytePointer, int pixelInTile, boolean isHorizontalFlip) {
        //1 byte represents 2 pixels, 1 pixel = 4 bit = 16 color gamut
        int twoPixelsData = memoryInterface.readVramByte(tileBytePointer);
        boolean isFirstPixel = (pixelInTile % 2) == (isHorizontalFlip ? 0 : 1);
        return isFirstPixel ? twoPixelsData & 0x0F : (twoPixelsData & 0xF0) >> 4;
    }

    private int getPixelColorValue(int pixelIndexColor, int paletteLine) {
        int colorIndexCram = paletteLine + (pixelIndexColor * 2);
        return getColorFromIndex(colorIndexCram);
    }

    // This value is effectively the address divided by $400; however, the low
    // bit is ignored, so the Window nametable has to be located at a VRAM
    // address that's a multiple of $800. For example, if the Window nametable
    // was to be located at $F000 in VRAM, it would be divided by $400, which
    // results in $3C, the proper value for this register.
    private void renderWindow(int line) {
        int reg11 = vdpProvider.getRegisterData(0x11);
        int reg12 = vdpProvider.getRegisterData(0x12);

        int windowVert = reg12 & 0x1F;
        boolean down = (reg12 & 0x80) == 0x80;

        int windowHorizontal = reg11 & 0x1F;
        boolean right = (reg11 & 0x80) == 0x80;

        int horizontalLimit = windowHorizontal * 2 * 8; //2-cell = 2*8 pixels
        int vertLimit = (windowVert * 8);

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
        int limitHorTiles = getHorizontalTiles(isH40);
        boolean drawWindow = legalVertical || (!legalVertical && legalHorizontal);

        if (drawWindow) {
            //Ayrton Senna -> vertical
            //Bad Omen -> horizontal
            int tileStart = legalHorizontal ? (right ? windowHorizontal * 2 : 0) : 0;
            int tileEnd = legalHorizontal ? (right ? limitHorTiles : windowHorizontal * 2) : limitHorTiles;
            drawWindowPlane(line, tileStart, tileEnd, isH40);
        }
    }

    private int[] verticalScrolling(int line, int VS, int pixel, int tileLocator, int verticalPlaneSize,
                                    boolean isPlaneA) {
        //VS == 1 -> 2 cell scroll
        int scrollLine = VS == 1 ? (pixel / 16) * 4 : 0;
        int vsramOffset = isPlaneA ? scrollLine : scrollLine + 2;
        int scrollDataVer = memoryInterface.readVideoRamWord(VdpProvider.VdpRamType.VSRAM, vsramOffset);

        int verticalScrollMask = (verticalPlaneSize * 8) - 1;
        int scrollMap = (scrollDataVer + line) & verticalScrollMask;
        int tileLocatorFactor = getHorizontalPlaneSize() * 2;
        tileLocator += (scrollMap / 8) * tileLocatorFactor;
        verticalScrollRes[0] = tileLocator;
        verticalScrollRes[1] = scrollMap;
        return verticalScrollRes;
    }

    private int horizontalScrolling(int line, int HS, int hScrollBase, int horizontalPlaneSize, boolean isPlaneA) {
        int vramOffset = 0;
        int scrollDataShift = (horizontalPlaneSize * 8);
        int horScrollMask = scrollDataShift - 1;

        switch (HS) {
            case 0b00:
                //	entire screen is scrolled at once by one longword in the horizontal scroll table
                vramOffset = isPlaneA ? hScrollBase : hScrollBase + 2;
                break;
            case 0b10:
//                every long scrolls 8 pixels
                int scrollLine = hScrollBase + ((line / 8) * 32);    // 32 bytes x 8 scanlines
                vramOffset = isPlaneA ? scrollLine : scrollLine + 2;
                break;
            case 0b01:
//                A scroll mode setting of 01b is not valid; however the unlicensed version
//                of Populous uses it. This mode is identical to per-line scrolling, however
//                the VDP will only read the first sixteen entries in the scroll table for
//                every line of the display.
                LOG.warn("Invalid horizontalScrolling value: " + 0b01);
                //fall-through
            case 0b11:
//                every longword scrolls one scanline
                int scrollLine1 = hScrollBase + ((line) * 4);    // 4 bytes x 1 scanline
                vramOffset = isPlaneA ? scrollLine1 : scrollLine1 + 2;
                break;
        }
        int scrollDataHor = memoryInterface.readVideoRamWord(VdpProvider.VdpRamType.VRAM, vramOffset);
        scrollDataHor &= horScrollMask;
        scrollDataHor = scrollDataShift - scrollDataHor;
        return scrollDataHor;
    }

    static class TileDataHolder {
        int tileIndex;
        boolean horFlip;
        boolean vertFlip;
        int paletteLineIndex;
        boolean priority;
    }

    static class SpriteDataHolder extends TileDataHolder {
        int verticalPos;
        int horizontalPos;
        int horSize;
        int verSize;
        int linkData;
    }

    private SpriteDataHolder getPhase1SpriteData(int baseAddress, SpriteDataHolder holder) {

        int byte0 = memoryInterface.readVramByte(baseAddress);
        int byte1 = memoryInterface.readVramByte(baseAddress + 1);
        int byte2 = memoryInterface.readVramByte(baseAddress + 2);
        int byte3 = memoryInterface.readVramByte(baseAddress + 3);

        holder.linkData = byte3 & 0x7F;
        holder.verticalPos = ((byte0 & 0x1) << 8) | byte1;
        holder.verSize = byte2 & 0x3;
        return holder;
    }

    private SpriteDataHolder getSpriteData(int baseAddress, SpriteDataHolder holder) {
        int byte0 = memoryInterface.readVramByte(baseAddress);
        int byte1 = memoryInterface.readVramByte(baseAddress + 1);
        int byte2 = memoryInterface.readVramByte(baseAddress + 2);
        int byte3 = memoryInterface.readVramByte(baseAddress + 3);
        int byte4 = memoryInterface.readVramByte(baseAddress + 4);
        int byte5 = memoryInterface.readVramByte(baseAddress + 5);
        int byte6 = memoryInterface.readVramByte(baseAddress + 6);
        int byte7 = memoryInterface.readVramByte(baseAddress + 7);

        holder.verticalPos = ((byte0 & 0x1) << 8) | byte1;
        holder.horSize = (byte2 >> 2) & 0x3;
        holder.verSize = byte2 & 0x3;
        holder.linkData = byte3 & 0x7F;
        holder.tileIndex = ((byte4 & 0x7) << 8) | byte5;
        holder.paletteLineIndex = (byte4 >> 5) & 0x3;
        holder.priority = ((byte4 >> 7) & 0x1) == 1;
        holder.vertFlip = ((byte4 >> 4) & 0x1) == 1;
        holder.horFlip = ((byte4 >> 3) & 0x1) == 1;
        holder.horizontalPos = ((byte6 & 0x1) << 8) | byte7;
        return holder;
    }


    private void drawWindowPlane(int line, int tileStart, int tileEnd, boolean isH40) {
        int vertTile = (line / 8);
        int nameTableLocation = getWindowPlaneNameTableLocation(isH40);
        int tileShiftFactor = isH40 ? 128 : 64;
        int tileLocator = nameTableLocation + (tileShiftFactor * vertTile);

        int vramLocation = tileLocator;
        int rowInTile = (line % 8);
        TileDataHolder tileDataHolder = new TileDataHolder();

        for (int horTile = tileStart; horTile < tileEnd; horTile++) {
            int nameTable = memoryInterface.readVideoRamWord(VdpProvider.VdpRamType.VRAM, vramLocation);
            vramLocation += 2;

            tileDataHolder = getTileData(nameTable, tileDataHolder);

            int paletteLine = tileDataHolder.paletteLineIndex * 32;

            int tileIndex = tileDataHolder.tileIndex * 0x20;
            int pointVert = tileDataHolder.vertFlip ? (rowInTile - 7) * -1 : rowInTile;

            //two pixels at a time as they share a tile
            for (int k = 0; k < 4; k++) {
                int point = tileDataHolder.horFlip ? (k - 3) * -1 : k;
                int po = horTile * 8 + (k * 2);

                int tileBytePointer = (tileIndex + point) + (pointVert * 4);

                int pixelIndexColor1 = getPixelIndexColor(tileBytePointer, 0, tileDataHolder.horFlip);
                int pixelIndexColor2 = getPixelIndexColor(tileBytePointer, 1, tileDataHolder.horFlip);

                int pixelColor1 = getPixelColorValue(pixelIndexColor1, paletteLine);
                int pixelColor2 = getPixelColorValue(pixelIndexColor2, paletteLine);

                window[po][line] = pixelColor1;
                window[po + 1][line] = pixelColor2;

                windowPrio[po][line] = tileDataHolder.priority;
                windowPrio[po + 1][line] = tileDataHolder.priority;

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
        pixelPriority[x][y] = prev.compareTo(rp) > 0 ? prev : rp;
    }

    private int getColorFromIndex(int colorIndex) {
        //Each word has the following format:
        // ----bbb-ggg-rrr-
        int color1 = memoryInterface.readVideoRamWord(VdpProvider.VdpRamType.CRAM, colorIndex);

        int r = (color1 >> 1) & 0x7;
        int g = (color1 >> 5) & 0x7;
        int b = (color1 >> 9) & 0x7;

        return getColor(r, g, b);
    }

    private int getColor(int red, int green, int blue) {
        return colorMapper.getColor(red, green, blue);
    }

    private int[][] getPlaneData(RenderType type) {
        int[][] res = new int[0][0];
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
    public void dumpScreenData() {
        Arrays.stream(RenderType.values()).forEach(r -> renderDump.saveRenderToFile(getPlaneData(r), videoMode, r));
    }
}
