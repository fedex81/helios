package omegadrive.vdp;

import omegadrive.util.Util;
import omegadrive.util.VideoMode;
import omegadrive.vdp.model.IVdpRenderHandler;
import omegadrive.vdp.model.RenderType;
import omegadrive.vdp.model.VdpMemoryInterface;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
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
 */
public class VdpRenderHandlerNew implements IVdpRenderHandler {

    private static Logger LOG = LogManager.getLogger(VdpRenderHandlerNew.class.getSimpleName());

    private VdpProvider vdpProvider;
    private VdpMemoryInterface memoryInterface;
    private VdpRenderDump renderDump;

    private int spritesFrame = 0;
    private int spritesLine = 0;
    private boolean disp;

    private final static int ROWS = VdpProvider.VDP_VIDEO_ROWS;
    private final static int COLS = VdpProvider.VDP_VIDEO_COLS;
    private final static int INDEXES_NUM = ROWS;
    private final static Dimension d = new Dimension(COLS, ROWS);

    private VideoMode videoMode;
    private VdpColorMapper colorMapper;

    private int[] lastIndexes = new int[INDEXES_NUM];
    private int[] priors = new int[COLS];
    private int[][] spritesPerLine = new int[INDEXES_NUM][VdpProvider.MAX_SPRITES_PER_FRAME_H40];
    private int[] verticalScrollRes = new int[2];

    private int[][] planeA = new int[COLS][ROWS];
    private int[][] planeB = new int[COLS][ROWS];
    private int[][] planeBack = new int[COLS][ROWS];

    private boolean[][] planePrioA = new boolean[COLS][ROWS];
    private boolean[][] planePrioB = new boolean[COLS][ROWS];

    private int[][] planeIndexColorA = new int[COLS][ROWS];
    private int[][] planeIndexColorB = new int[COLS][ROWS];

    private int[][] sprites = new int[COLS][ROWS];
    private int[][] spritesIndex = new int[COLS][ROWS];
    private boolean[][] spritesPrio = new boolean[COLS][ROWS];

    private int[][] window = new int[COLS][ROWS];
    private int[][] windowIndex = new int[COLS][ROWS];
    private boolean[][] windowPrio = new boolean[COLS][ROWS];

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

    private int getVerticalLines(boolean isV30) {
        return isV30 ? VdpProvider.VERTICAL_LINES_V30 : VdpProvider.VERTICAL_LINES_V28;
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
        if (line == 0) {
            clearData();
        }
        spritesLine = 0;
        disp = vdpProvider.isDisplayEnabled();
        if (!disp) { //breaks Gunstar Heroes
            return;
        }
        renderBack(line);
        renderPlaneA(line);
        renderPlaneB(line);
        renderWindow(line);
        renderSprites(line);
    }

    public int[][] renderFrame() {
        spritesFrame = 0;
        evaluateSprites();
        return compaginateImage();
    }


    //TODO why needed?
    private void clearData() {
        Arrays.stream(sprites).forEach(a -> Arrays.fill(a, 0));
        Arrays.stream(spritesIndex).forEach(a -> Arrays.fill(a, 0));
        Arrays.stream(window).forEach(a -> Arrays.fill(a, 0));
        Arrays.stream(windowIndex).forEach(a -> Arrays.fill(a, 0));
    }

    private void evaluateSprites() {
        //	AT16 is only valid if 128 KB mode is enabled,
        // and allows for rebasing the Sprite Attribute Table to the second 64 KB of VRAM.
        int spriteTableLoc = vdpProvider.getRegisterData(0x5) & 0x7F;
        int spriteTable = spriteTableLoc * 0x200;

        //reset
        int currSprite = 0;
        for (int i = 0; i < ROWS; i++) {
            lastIndexes[i] = 0;
            Arrays.fill(spritesPerLine[i], -1);
        }

        boolean isH40 = videoMode.isH40();
        int maxSprites = maxSpritesPerFrame(isH40);

        for (int i = 0; i < maxSprites; i++) {
            int baseAddress = spriteTable + (i * 8);

            int byte0 = memoryInterface.readVramByte(baseAddress);
            int byte1 = memoryInterface.readVramByte(baseAddress + 1);
            int byte2 = memoryInterface.readVramByte(baseAddress + 2);
            int byte3 = memoryInterface.readVramByte(baseAddress + 3);

            int linkData = byte3 & 0x7F;

            int verticalPos = ((byte0 & 0x1) << 8) | byte1;
            int verSize = byte2 & 0x3;

            int verSizePixels = (verSize + 1) * 8;
            int realY = verticalPos - 128;
            for (int j = realY; j < realY + verSizePixels; j++) {
                if (j < 0 || j >= INDEXES_NUM) {
                    continue;
                }

                int last = lastIndexes[j];
                if (last < maxSprites) { //TODO why??
                    spritesPerLine[j][last] = i;
                    lastIndexes[j] = last + 1;
                }
            }

            if (linkData == 0) {
                return;
            }
        }
    }

    private void renderSprites(int line) {
        //	AT16 is only valid if 128 KB mode is enabled, and allows
        // for rebasing the Sprite Attribute Table to the second 64 KB of VRAM.
        int spriteTableLoc = vdpProvider.getRegisterData(0x5) & 0x7F;
        int spriteTable = spriteTableLoc * 0x200;

        long linkData = 0xFF;
        long verticalPos;

        int baseAddress = spriteTable;
        int[] spritesInLine = spritesPerLine[line];
        int ind = 0;
        int currSprite = spritesInLine[0];


        boolean isH40 = videoMode.isH40();
        int maxSpritesPerLine = maxSpritesPerLine(isH40);
        int maxSpritesPerFrame = maxSpritesPerFrame(isH40);
        Arrays.fill(priors, 0);
        //Stop processing sprites, skip the remaining lines
//        if (spritesFrame > maxSpritesPerFrame) { //TODO breaks AyrtonSenna
//            return;
//        }

        while (currSprite != -1) {
            baseAddress = spriteTable + (currSprite * 8);

            int byte0 = memoryInterface.readVramByte(baseAddress);
            int byte1 = memoryInterface.readVramByte(baseAddress + 1);
            int byte2 = memoryInterface.readVramByte(baseAddress + 2);
            int byte3 = memoryInterface.readVramByte(baseAddress + 3);
            int byte4 = memoryInterface.readVramByte(baseAddress + 4);
            int byte5 = memoryInterface.readVramByte(baseAddress + 5);
            int byte6 = memoryInterface.readVramByte(baseAddress + 6);
            int byte7 = memoryInterface.readVramByte(baseAddress + 7);

            linkData = byte3 & 0x7F;
            verticalPos = ((byte0 & 0x1) << 8) | byte1;        //	bit 9 interlace mode only

//			if (linkData == 0) {
//				return;
//			}

            int horSize = (byte2 >> 2) & 0x3;
            int verSize = byte2 & 0x3;

            int horSizePixels = (horSize + 1) * 8;
            int verSizePixels = (verSize + 1) * 8;

            int nextSprite = (int) ((linkData * 8) + spriteTable);
            baseAddress = nextSprite;

            int realY = (int) (verticalPos - 128);

            spritesFrame++;
            spritesLine++;
            //Stop processing sprites, skip the remaining lines
            if (spritesLine > maxSpritesPerLine) {
// || spritesFrame > maxSpritesPerFrame) {  //TODO breaks AyrtonSenna
                return;
            }


            int pattern = ((byte4 & 0x7) << 8) | byte5;
            int palette = (byte4 >> 5) & 0x3;

            boolean priority = ((byte4 >> 7) & 0x1) == 1;
            boolean verFlip = ((byte4 >> 4) & 0x1) == 1;
            boolean horFlip = ((byte4 >> 3) & 0x1) == 1;

            int horizontalPos = ((byte6 & 0x1) << 8) | byte7;
            int horOffset = horizontalPos - 128;

            int spriteLine = (line - realY) % verSizePixels;

            int pointVert;
            if (verFlip) {
                pointVert = (spriteLine - (verSizePixels - 1)) * -1;
            } else {
                pointVert = spriteLine;
            }

            for (int cellHor = 0; cellHor < (horSize + 1); cellHor++) {
                //	16 bytes por cell de 8x8
                //	cada linea dentro de una cell de 8 pixeles, ocupa 4 bytes (o sea, la mitad del ancho en bytes)
                int currentVerticalCell = pointVert / 8;
                int vertLining = (currentVerticalCell * 32) + ((pointVert % 8) * 4);

                int cellH = cellHor;
                if (horFlip) {
                    cellH = (cellHor * -1) + horSize;
                }
                int horLining = vertLining + (cellH * ((verSize + 1) * 32));
                for (int i = 0; i < 4; i++) {
                    int sliver = i;
                    if (horFlip) {
                        sliver = (i * -1) + 3;
                    }

                    int grab = (pattern * 0x20) + (horLining) + sliver;
                    if (grab < 0) {
                        continue;    //	FIXME guardar en cache de sprites yPos y otros atrib
                    }
                    int data = memoryInterface.readVramByte(grab);
                    int pixel1, pixel2;
                    if (horFlip) {
                        pixel1 = data & 0x0F;
                        pixel2 = (data & 0xF0) >> 4;
                    } else {
                        pixel1 = (data & 0xF0) >> 4;
                        pixel2 = data & 0x0F;
                    }

                    int paletteLine = palette * 32;

                    int colorIndex1 = paletteLine + (pixel1 * 2);
                    int colorIndex2 = paletteLine + (pixel2 * 2);

                    storeSpriteData(pixel1, horOffset, line, priority, colorIndex1);
                    storeSpriteData(pixel2, horOffset + 1, line, priority, colorIndex2);
                    horOffset += 2;
                }
            }

            ind++;
            currSprite = spritesInLine[ind];
            if (currSprite > maxSpritesPerFrame) {
                return;
            }
        }
    }

    private void storeSpriteData(int pixel, int horOffset, int line, boolean priority, int colorIndex) {
        if (horOffset < 0 || horOffset >= COLS) {
            return;
        }
        if (pixel == 0 && spritesIndex[horOffset][line] == 0) {
            spritesPrio[horOffset][line] = priority;
        } else if (pixel != 0) {
            //TODO this seems to fix the sprite priority issue in Sonic
            if (priors[horOffset] == 0) {
                // || (priors[horOffset] == 1 && priority)) {
                if (priority) {
                    priors[horOffset] = 1;
                }
                int theColor = getColorFromIndex(colorIndex);
                sprites[horOffset][line] = theColor;
                spritesIndex[horOffset][line] = pixel;
                spritesPrio[horOffset][line] = priority;
            }
        }
    }

    //The VDP has a complex system of priorities that can be used to achieve several complex effects.
    // The priority order goes like follows, with the least priority being the first item in the list:
    //
    //Backdrop Colour
    //Plane B with priority bit clear
    //Plane A with priority bit clear
    //Sprites with priority bit clear
    //Window Plane with priority bit clear
    //Plane B with priority bit set
    //Plane A with priority bit set
    //Sprites with priority bit set
    //Window Plane with priority bit set
    private int[][] compaginateImage() {
        int limitHorTiles = getHorizontalTiles();
        for (int j = 0; j < ROWS; j++) {
            for (int i = 0; i < limitHorTiles * 8; i++) {
                int backColor = planeBack[i][j];

                boolean aPrio = planePrioA[i][j];
                boolean bPrio = planePrioB[i][j];
                boolean sPrio = spritesPrio[i][j];
                boolean wPrio = windowPrio[i][j];

                int aColor = planeIndexColorA[i][j];
                int bColor = planeIndexColorB[i][j];
                int wColor = windowIndex[i][j];
                int spriteIndex = spritesIndex[i][j];

                boolean aDraw = (aColor != 0);
                boolean bDraw = (bColor != 0);
                boolean sDraw = (spriteIndex != 0);
                boolean wDraw = (wColor != 0);

                boolean W = (wDraw && ((wPrio)
                        || (!wPrio
                        && (!sDraw || (sDraw && !sPrio))
                        && (!aDraw || (aDraw && !aPrio))
                        && (!bDraw || (bDraw && !bPrio))
                )));

                int pix = 0;
                if (W) {
                    pix = window[i][j];
                } else {
                    boolean S = (sDraw && ((sPrio)
                            || (!sPrio && !aPrio && !bPrio)
                            || (!sPrio && aPrio && !aDraw)
                            || (!bDraw && bPrio && !sPrio && !aPrio)));
                    if (S) {
                        pix = sprites[i][j];

                    } else {
                        boolean A = (aDraw && aPrio)
                                || (aDraw && ((!bPrio) || (!bDraw)));
                        if (A) {
                            pix = planeA[i][j];
                        } else if (bDraw) {
                            pix = planeB[i][j];
                        } else {
                            pix = backColor;
                        }
                    }
                }
                screenData[i][j] = pix;
            }
        }
//        clearData(); //TODO old style
        return screenData;
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
        renderPlane(line, true);
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
        renderPlane(line, false);
    }

    private void renderPlane(int line, boolean isPlaneA) {

        int nameTableLocation = isPlaneA ? vdpProvider.getRegisterData(2) & 0x38 :
                (vdpProvider.getRegisterData(4) & 0x7) << 3;    // TODO bit 3 for 128KB VRAM
        nameTableLocation *= 0x400;

        int tileLocator = 0;
        int horizontalPlaneSize = getHorizontalPlaneSize();
        int verticalPlaneSize = getVerticalPlaneSize();
        int limitHorTiles = getHorizontalTiles();
        int hScrollDataLocation = getHScrollDataLocation();

        int regB = vdpProvider.getRegisterData(0xB);
        int HS = regB & 0x3;
        int VS = (regB >> 2) & 0x1;

        //horizontal scrolling
        long horizontalScrollingOffset = horizontalScrolling(line, HS, hScrollDataLocation, horizontalPlaneSize, isPlaneA);

        int[][] plane = isPlaneA ? planeA : planeB;
        boolean[][] planePriority = isPlaneA ? planePrioA : planePrioB;
        int[][] planeIndexColor = isPlaneA ? planeIndexColorA : planeIndexColorB;
        TileDataHolder tileDataHolder = new TileDataHolder();

        for (int pixel = 0; pixel < (limitHorTiles * 8); pixel++) {
            int loc = (int) (((pixel + horizontalScrollingOffset)) % (horizontalPlaneSize * 8)) / 8;

            int[] res = verticalScrolling(line, VS, pixel, nameTableLocation, verticalPlaneSize, isPlaneA);
            int verticalScrollingOffset = res[0];
            int scrollMap = res[1];
            loc = tileLocator + (loc * 2);
            loc += verticalScrollingOffset;
            int nameTable = memoryInterface.readVideoRamWord(VdpProvider.VdpRamType.VRAM, loc);

            tileDataHolder = getTileData(nameTable, tileDataHolder);

            int paletteLine = tileDataHolder.paletteLineIndex * 32;    //	16 colors per line, 2 bytes per color

            int tileIndex = tileDataHolder.tileIndex * 0x20;

            int row = (scrollMap % 8);

            int pointVert = tileDataHolder.vertFlip ? (row - 7) * -1 : row;
            int pixelInTile = (int) ((pixel + horizontalScrollingOffset) % 8);

            int point = tileDataHolder.horFlip ? (pixelInTile - 7) * -1 : pixelInTile;
            int pixel1 = 0;
            boolean priorityValue = false;

            point /= 2;

            int grab = (tileIndex + point) + (pointVert * 4);
            int data = memoryInterface.readVramByte(grab);

            if ((pixelInTile % 2) == 0) {
                if (tileDataHolder.horFlip) {
                    pixel1 = data & 0x0F;
                } else {
                    pixel1 = (data & 0xF0) >> 4;
                }
            } else {
                if (tileDataHolder.horFlip) {
                    pixel1 = (data & 0xF0) >> 4;
                } else {
                    pixel1 = data & 0x0F;
                }
            }

            int colorIndex1 = paletteLine + (pixel1 * 2);
            int theColor1 = getColorFromIndex(colorIndex1);
            priorityValue = tileDataHolder.priority;

            plane[pixel][line] = theColor1;
            planePriority[pixel][line] = priorityValue;
            planeIndexColor[pixel][line] = pixel1;
        }
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

    private long horizontalScrolling(int line, int HS, int hScrollBase, int horizontalPlaneSize, boolean isPlaneA) {
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
        long scrollDataHor = memoryInterface.readVideoRamWord(VdpProvider.VdpRamType.VRAM, vramOffset);
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

            int paletteLine = tileDataHolder.paletteLineIndex * 32;    //	16 colores por linea, 2 bytes por color

            int tileIndex = tileDataHolder.tileIndex * 0x20;
            int pointVert;
            if (tileDataHolder.vertFlip) {
                pointVert = (rowInTile - 7) * -1;
            } else {
                pointVert = rowInTile;
            }
            for (int k = 0; k < 4; k++) {
                int point;
                if (tileDataHolder.horFlip) {
                    point = (k - 3) * -1;
                } else {
                    point = k;
                }

                int po = horTile * 8 + (k * 2);
                int pixel1 = 0, pixel2 = 0;

                int grab = (tileIndex + point) + (pointVert * 4);
                int data = memoryInterface.readVramByte(grab);

                if (tileDataHolder.horFlip) {
                    pixel1 = data & 0x0F;
                    pixel2 = (data & 0xF0) >> 4;
                } else {
                    pixel1 = (data & 0xF0) >> 4;
                    pixel2 = data & 0x0F;
                }

                int colorIndex1 = paletteLine + (pixel1 * 2);
                int colorIndex2 = paletteLine + (pixel2 * 2);

                int theColor1 = getColorFromIndex(colorIndex1);
                int theColor2 = getColorFromIndex(colorIndex2);
                boolean priorityValue = tileDataHolder.priority;


                window[po][line] = theColor1;
                window[po + 1][line] = theColor2;

                windowPrio[po][line] = priorityValue;
                windowPrio[po + 1][line] = priorityValue;

                windowIndex[po][line] = pixel1;
                windowIndex[po + 1][line] = pixel2;
            }
        }
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
