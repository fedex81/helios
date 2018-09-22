package omegadrive.vdp;

import omegadrive.util.Util;
import omegadrive.util.VideoMode;
import omegadrive.vdp.model.VdpMemoryInterface;

import java.util.Arrays;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Based on genefusto GenVdp
 * https://github.com/DarkMoe/genefusto
 * @author DarkMoe
 *
 * Copyright 2018
 */
public class VdpRenderHandler {

    private VdpProvider vdpProvider;
    private VdpMemoryInterface memoryInterface;

    private int spritesFrame = 0;
    private int spritesLine = 0;
    private boolean disp;

    private final static int ROWS = VdpProvider.VDP_VIDEO_ROWS;
    private final static int COLS = VdpProvider.VDP_VIDEO_COLS;
    private final static int INDEXES_NUM = ROWS;

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

    public VdpRenderHandler(VdpProvider vdpProvider, VdpMemoryInterface memoryInterface) {
        this.vdpProvider = vdpProvider;
        this.memoryInterface = memoryInterface;
        this.colorMapper = new VdpColorMapper();
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

    private int getHorizontalPixelSize() {
        int reg10 = vdpProvider.getRegisterData(0x10);
        //registers[0x10];
        int horScrollSize = reg10 & 3;
        int horPixelsSize = 0;
        if (horScrollSize == 0) {
            horPixelsSize = 32;
        } else if (horScrollSize == 1) {
            horPixelsSize = 64;
        } else {
            horPixelsSize = 128;
        }
        return horPixelsSize;
    }

    public void renderLine(int line) {
        spritesLine = 0;
        disp = vdpProvider.isDisplayEnabled();
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

            boolean priority = ((byte4 >> 7) & 0x1) == 1 ? true : false;
            boolean verFlip = ((byte4 >> 4) & 0x1) == 1 ? true : false;
            boolean horFlip = ((byte4 >> 3) & 0x1) == 1 ? true : false;

            int horizontalPos = ((byte6 & 0x1) << 8) | byte7;
            int horOffset = horizontalPos - 128;

            int spriteLine = (int) ((line - realY) % verSizePixels);

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

                    int color1;
                    if (pixel1 == 0) {
                        if (horOffset >= 0 && horOffset < COLS) {
                            if (spritesIndex[horOffset][line] == 0) {    // solo pisa si la prioridad anterior era 0
                                spritesIndex[horOffset][line] = pixel1;
                                spritesPrio[horOffset][line] = priority;
                            }
                        }
                    } else {
                        if (horOffset >= 0 && horOffset < COLS) {
                            if (priors[horOffset] == 0 || (priors[horOffset] == 1 && priority)) {
                                if (priority) {
                                    priors[horOffset] = 1;
                                }
                                int theColor1 = getColorFromIndex(colorIndex1);

                                sprites[horOffset][line] = theColor1;
                                spritesIndex[horOffset][line] = pixel1;
                                spritesPrio[horOffset][line] = priority;
                            }
                        }
                    }

                    int color2;
                    int horOffset2 = horOffset + 1;
                    if (pixel2 == 0) {
                        if (horOffset2 >= 0 && horOffset2 < COLS) {
                            if (spritesIndex[horOffset2][line] == 0) {    // solo pisa si la prioridad anterior era 0
                                spritesIndex[horOffset2][line] = pixel2;
                                spritesPrio[horOffset2][line] = priority;
                            }
                        }
                    } else {
                        if (horOffset2 >= 0 && horOffset2 < COLS) {
                            if (priors[horOffset2] == 0 || (priors[horOffset2] == 1 && priority)) {
                                if (priority) {
                                    priors[horOffset2] = 1;
                                }
                                int theColor2 = getColorFromIndex(colorIndex2);

                                sprites[horOffset2][line] = theColor2;
                                spritesIndex[horOffset2][line] = pixel2;
                                spritesPrio[horOffset2][line] = priority;
                            }
                        }
                    }

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

                //	TODO if a draw W, don't draw A over it
                boolean W = (wDraw && ((wPrio)
                        || (!wPrio
                        && (!sDraw || (sDraw && !sPrio))
                        && (!aDraw || (aDraw && !aPrio))
                        && (!bDraw || (bDraw && !bPrio))
                )));

                int pix = 0;
                if (W) {
                    pix = window[i][j];
                    window[i][j] = 0;
                    windowIndex[i][j] = 0;
                } else {
                    boolean S = (sDraw && ((sPrio)
                            || (!sPrio && !aPrio && !bPrio)
                            || (!sPrio && aPrio && !aDraw)
                            || (!bDraw && bPrio && !sPrio && !aPrio)));
                    if (S) {
                        pix = sprites[i][j];
                        sprites[i][j] = 0;
                        spritesIndex[i][j] = 0;
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

                window[i][j] = 0;
                windowIndex[i][j] = 0;
                sprites[i][j] = 0;
                spritesIndex[i][j] = 0;
            }
        }
        return screenData;
    }

    private void renderBack(int line) {
        int limitHorTiles = getHorizontalTiles();
        int reg7 = vdpProvider.getRegisterData(0x7);
        int backLine = (reg7 >> 4) & 0x3;
        int backEntry = (reg7) & 0xF;
        int backIndex = (backLine * 32) + (backEntry * 2);
        int backColor = getColorFromIndex(backIndex);

        for (int pixel = 0; pixel < (limitHorTiles * 8); pixel++) {
            if (!disp) {
                planeBack[pixel][line] = 0;
            } else {
                planeBack[pixel][line] = backColor;
            }
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

        int tileLocator = nameTableLocation;
        int scrollMap = 0;

        int reg10 = vdpProvider.getRegisterData(0x10);
        int horScrollSize = reg10 & 3;
        int verScrollSize = (reg10 >> 4) & 3;

        int horPixelsSize = getHorizontalPixelSize();
        int limitHorTiles = getHorizontalTiles();

        int regD = vdpProvider.getRegisterData(0xD);
        int hScrollBase = regD & 0x3F;    //	bit 6 = mode 128k
        hScrollBase *= 0x400;

        int regB = vdpProvider.getRegisterData(0xB);
        int HS = regB & 0x3;
        int VS = (regB >> 2) & 0x1;

        //vertical scrolling
        if (VS == 0) {
            int[] res = fullScreenVerticalScrolling(line, verScrollSize, horScrollSize, tileLocator, isPlaneA);
            tileLocator = res[0];
            scrollMap = res[1];
        }

        //horizontal scrolling
        long scrollDataHor = horizontalScrolling(line, HS, hScrollBase, horScrollSize, isPlaneA);

        int loc = tileLocator;
        int[][] plane = isPlaneA ? planeA : planeB;
        boolean[][] planePriority = isPlaneA ? planePrioA : planePrioB;
        int[][] planeIndexColor = isPlaneA ? planeIndexColorA : planeIndexColorB;

        for (int pixel = 0; pixel < (limitHorTiles * 8); pixel++) {
            loc = (int) (((pixel + scrollDataHor)) % (horPixelsSize * 8)) / 8;

            int vertOffset = 0;
            if (VS == 1) {
                int scrollLine = (pixel / 16) * 4;    // 32 bytes for 8 scanlines

                //vertical scrolling
                int[] res = verticalScrolling(line, scrollLine, verScrollSize, horScrollSize, vertOffset, isPlaneA);
                vertOffset = res[0];
                scrollMap = res[1];
            }


            loc = tileLocator + (loc * 2);
            loc += vertOffset;
            int nameTable = memoryInterface.readVideoRamWord(VdpProvider.VdpRamType.VRAM, loc);

//			An entry in a name table is 16 bits, and works as follows:
//			15			14 13	12				11		   			10 9 8 7 6 5 4 3 2 1 0
//			Priority	Palette	Vertical Flip	Horizontal Flip		Tile Index
            int tileIndex = (nameTable & 0x07FF);    // cada tile ocupa 32 bytes

            boolean horFlip = Util.bitSetTest(nameTable, 11);
            boolean vertFlip = Util.bitSetTest(nameTable, 12);
            int paletteLineIndex = (nameTable >> 13) & 0x3;
            boolean priority = Util.bitSetTest(nameTable, 15);

            int paletteLine = paletteLineIndex * 32;    //	16 colores por linea, 2 bytes por color

            tileIndex *= 0x20;

            int filas = (scrollMap % 8);

            int pointVert;
            if (vertFlip) {
                pointVert = (filas - 7) * -1;
            } else {
                pointVert = filas;
            }

            int pixelInTile = (int) ((pixel + scrollDataHor) % 8);

            int point = pixelInTile;
            if (horFlip) {
                point = (pixelInTile - 7) * -1;
            }

            if (!disp) {
                plane[pixel][line] = 0;
                planePriority[pixel][line] = false;
                planeIndexColor[pixel][line] = 0;
            } else {
                point /= 2;

                int grab = (tileIndex + point) + (pointVert * 4);
                int data = memoryInterface.readVramByte(grab);

                int pixel1;
                if ((pixelInTile % 2) == 0) {
                    if (horFlip) {
                        pixel1 = data & 0x0F;
                    } else {
                        pixel1 = (data & 0xF0) >> 4;
                    }
                } else {
                    if (horFlip) {
                        pixel1 = (data & 0xF0) >> 4;
                    } else {
                        pixel1 = data & 0x0F;
                    }
                }

                int colorIndex1 = paletteLine + (pixel1 * 2);
                int theColor1 = getColorFromIndex(colorIndex1);

                plane[pixel][line] = theColor1;
                planePriority[pixel][line] = priority;
                planeIndexColor[pixel][line] = pixel1;
            }
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

    private int[] fullScreenVerticalScrolling(int line, int verScrollSize, int horScrollSize,
                                              int tileLocator, boolean isPlaneA) {
        //when fullScreen vsram offset is 0 for planeA and 2 for planeB
        return verticalScrolling(line, 0, verScrollSize, horScrollSize, tileLocator, isPlaneA);
    }

    private int[] verticalScrolling(int line, int scrollLine, int verScrollSize, int horScrollSize,
                                    int tileLocator, boolean isPlaneA) {
        int scrollMap = 0;
        int vsramOffset = isPlaneA ? scrollLine : scrollLine + 2;
        int scrollDataVer = memoryInterface.readVideoRamWord(VdpProvider.VdpRamType.VSRAM, vsramOffset);

        if (verScrollSize == 0) {    // 32 tiles (0x20)
            scrollMap = (scrollDataVer + line) & 0xFF;    //	32 * 8 lineas = 0x100
            if (horScrollSize == 0) {
                tileLocator += ((scrollMap / 8) * (0x40));
            } else if (horScrollSize == 1) {
                tileLocator += ((scrollMap / 8) * (0x80));
            } else {
                tileLocator += ((scrollMap / 8) * (0x100));
            }

        } else if (verScrollSize == 1) {    // 64 tiles (0x40)
            scrollMap = (scrollDataVer + line) & 0x1FF;    //	64 * 8 lineas = 0x200
            tileLocator += ((scrollMap / 8) * 0x80);

        } else {
            scrollMap = (scrollDataVer + line) & 0x3FF;    //	128 * 8 lineas = 0x400
            tileLocator += ((scrollMap / 8) * 0x100);
        }
        verticalScrollRes[0] = tileLocator;
        verticalScrollRes[1] = scrollMap;
        return verticalScrollRes;
    }

    private long horizontalScrolling(int line, int HS, int hScrollBase, int horScrollSize, boolean isPlaneA) {
        long scrollDataHor = 0;
        long scrollTile = 0;
        int vramOffset = 0;
        if (HS == 0b00) {    //	entire screen is scrolled at once by one longword in the horizontal scroll table
            vramOffset = isPlaneA ? hScrollBase : hScrollBase + 2;
            scrollDataHor = memoryInterface.readVideoRamWord(VdpProvider.VdpRamType.VRAM, vramOffset);
            if (horScrollSize == 0) {    //	32 tiles
                scrollDataHor &= 0xFF;
                if (scrollDataHor != 0) {

                    scrollDataHor = 0x100 - scrollDataHor;
                    scrollTile = scrollDataHor / 8;
                }

            } else if (horScrollSize == 1) {    //	64 tiles
                scrollDataHor &= 0x1FF;
                if (scrollDataHor != 0) {

                    scrollDataHor = 0x200 - scrollDataHor;
                    scrollTile = scrollDataHor / 8;
                }
            } else {
                scrollDataHor &= 0xFFF;    //	128 tiles

                if (scrollDataHor != 0) {
                    scrollDataHor = 0x1000 - scrollDataHor;
                    scrollTile = scrollDataHor / 8;
                }
            }

        } else if (HS == 0b10) {    //	long scrolls 8 pixels
            int scrollLine = hScrollBase + ((line / 8) * 32);    // 32 bytes por 8 scanlines

            vramOffset = isPlaneA ? scrollLine : scrollLine + 2;
            scrollDataHor = memoryInterface.readVideoRamWord(VdpProvider.VdpRamType.VRAM, vramOffset);//readVramWord(vramOffset);

            if (scrollDataHor != 0) {
                if (horScrollSize == 0) {    //	32 tiles
                    scrollDataHor &= 0xFF;

                    scrollDataHor = 0x100 - scrollDataHor;
                    scrollTile = scrollDataHor / 8;

                } else if (horScrollSize == 1) {    //	64 tiles
                    scrollDataHor &= 0x1FF;

                    scrollDataHor = 0x200 - scrollDataHor;
                    scrollTile = scrollDataHor / 8;

                } else {        //	128 tiles
                    scrollDataHor &= 0x3FF;

                    scrollDataHor = 0x400 - scrollDataHor;
                    scrollTile = scrollDataHor / 8;
                }
            }

        } else if (HS == 0b11) {    //	scroll one scanline
            int scrollLine = hScrollBase + ((line) * 4);    // 4 bytes por 1 scanline
            vramOffset = isPlaneA ? scrollLine : scrollLine + 2;
            scrollDataHor = memoryInterface.readVideoRamWord(VdpProvider.VdpRamType.VRAM, vramOffset);//readVramWord(vramOffset);

            if (horScrollSize == 0) {    //	32 tiles
                scrollDataHor &= 0xFF;

                if (scrollDataHor != 0) {
                    scrollDataHor = 0x100 - scrollDataHor;
                    scrollTile = scrollDataHor / 8;
                }

            } else if (horScrollSize == 1) {    //	64 tiles
                scrollDataHor &= 0x1FF;

                if (scrollDataHor != 0) {
                    scrollDataHor = 0x200 - scrollDataHor;
                    scrollTile = scrollDataHor / 8;
                }

            } else {
                scrollDataHor &= 0x3FF;

                if (scrollDataHor != 0) {
                    scrollDataHor = 0x400 - scrollDataHor;
                    scrollTile = scrollDataHor / 8;
                }
            }
        }
        return scrollDataHor;
    }


    private void drawWindowPlane(int line, int tileStart, int tileEnd, boolean isH40) {
        int vertTile = (line / 8);
        int nameTableLocation;
        int tileLocator;
        int reg3 = vdpProvider.getRegisterData(0x3);
        if (isH40) {
            //	WD11 is ignored if the display resolution is 320px wide (H40), which limits the Window nametable address to multiples of $1000.
            nameTableLocation = reg3 & 0x3C;
            nameTableLocation *= 0x400;
            tileLocator = nameTableLocation + (128 * vertTile);
        } else {
            nameTableLocation = reg3 & 0x3E;    //	bit 6 = 128k mode
            nameTableLocation *= 0x400;
            tileLocator = nameTableLocation + (64 * vertTile);
        }


        for (int horTile = tileStart; horTile < tileEnd; horTile++) {
            int loc = tileLocator;

            int nameTable = memoryInterface.readVideoRamWord(VdpProvider.VdpRamType.VRAM, loc);
            tileLocator += 2;

//				An entry in a name table is 16 bits, and works as follows:
//				15			14 13	12				11		   			10 9 8 7 6 5 4 3 2 1 0
//				Priority	Palette	Vertical Flip	Horizontal Flip		Tile Index
            int tileIndex = (nameTable & 0x07FF);    // cada tile ocupa 32 bytes

            boolean horFlip = Util.bitSetTest(nameTable, 11);
            boolean vertFlip = Util.bitSetTest(nameTable, 12);
            int paletteLineIndex = (nameTable >> 13) & 0x3;
            boolean priority = Util.bitSetTest(nameTable, 15);

            int paletteLine = paletteLineIndex * 32;    //	16 colores por linea, 2 bytes por color

            tileIndex *= 0x20;

            int filas = (line % 8);

            int pointVert;
            if (vertFlip) {
                pointVert = (filas - 7) * -1;
            } else {
                pointVert = filas;
            }
            for (int k = 0; k < 4; k++) {
                int point;
                if (horFlip) {
                    point = (k - 3) * -1;
                } else {
                    point = k;
                }

                int po = horTile * 8 + (k * 2);

                if (!disp) {
                    window[po][line] = 0;
                    window[po + 1][line] = 0;

                    windowPrio[po][line] = false;
                    windowPrio[po + 1][line] = false;

                    windowIndex[po][line] = 0;
                    windowIndex[po + 1][line] = 0;
                } else {
                    int grab = (tileIndex + point) + (pointVert * 4);
                    int data = memoryInterface.readVramByte(grab);

                    int pixel1, pixel2;
                    if (horFlip) {
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

                    window[po][line] = theColor1;
                    window[po + 1][line] = theColor2;

                    windowPrio[po][line] = priority;
                    windowPrio[po + 1][line] = priority;

                    windowIndex[po][line] = pixel1;
                    windowIndex[po + 1][line] = pixel2;
                }
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
}
