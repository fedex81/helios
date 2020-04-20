/*
 * SmsVdp
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 10:55
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
package omegadrive.vdp;

import omegadrive.SystemLoader;
import omegadrive.system.SystemProvider;
import omegadrive.ui.RenderingStrategy;
import omegadrive.util.RegionDetector;
import omegadrive.util.VideoMode;
import omegadrive.vdp.gen.VdpInterruptHandler;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.vdp.model.VdpMemory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static omegadrive.util.RegionDetector.Region.EUROPE;
import static omegadrive.util.RegionDetector.Region.USA;

/**
 * Vdp.java
 * <p>
 * SMS and GG VDP Emulation.
 *
 * @author (c) 2002-2008 Chris White
 * @version 19th May 2008
 * <p>
 * SMS uses 256x192 window (32x28)
 * GG  uses 160x140 window (20x17.5)
 * <p>
 * What's emulated:
 * Passes Flubba's VDPTEST.SMS utility
 * <p>
 * Notes:
 * - http://www.smspower.org/forums/viewtopic.php?p=44198
 */
public final class SmsVdp implements BaseVdpProvider {

    public static final int VDP_VRAM_SIZE = 0x4000;
    public static final int VDP_CRAM_SIZE = 0x20;
    public static final int VDP_REGISTERS_SIZE = 16;

    //https://www.smspower.org/Development/TilemapMirroring
    public static final boolean ENABLE_TILEMAP_MIRRORING = false;

    // --------------------------------------------------------------------------------------------
    // Screen Dimensions
    // --------------------------------------------------------------------------------------------

    public final static int
            NTSC = 0,
            PAL = 1;

    /**
     * NTSC / PAL Emulation
     */
    public static int palFlag = NTSC;

    /** SMS Visible Screen Width */
    private final static int SMS_WIDTH = BaseVdpProvider.H32;

    /** SMS Visible Screen Height */
    private final static int SMS_HEIGHT = BaseVdpProvider.V30_CELL;

    /** GG Visible Screen Width */
    public final static int GG_WIDTH   = 160;

    /** GG Visible Screen Height */
    public final static int GG_HEIGHT  = 144;

    /** GG Visible Window Starts Here (x) */
    public final static int GG_X_OFFSET = 48;

    /** GG Window Starts Here (y) */
    public final static int GG_Y_OFFSET = 24;

    // --------------------------------------------------------------------------------------------
    // VDP Emulation
    // --------------------------------------------------------------------------------------------

    /** Video RAM */
    private int[] VRAM;

    /** Colour RAM */
    private int[] CRAM;

    /** VDP Registers */
    private final int[] vdpreg;

    /** Status Register */
    private int status;

    public final static int
            STATUS_VINT      = 0x80, // Frame Interrupt Pending
            STATUS_OVERFLOW  = 0x40, // Sprite Overflow
            STATUS_COLLISION = 0x20, // Sprite Collision
            STATUS_HINT      = 0x04; // Line interrupt Pending

    /** First or Second Byte of Command Word */
    private boolean firstByte;

    /** Command Word First Byte Latch */
    private int commandByte;

    /** Location in VRAM */
    private int location;

    /** Store type of operation taking place */
    private int operation;

    /** Buffer VRAM Reads */
    private int readBuffer;

    /** Current Line Number to Render */
    public int line;

    /** Vertical Line Interrupt Counter */
    private int counter;

    /** Background Priorities */
    private final boolean[] bgPriority;
    /**
     * Decoded SAT by each scanline
     */
    private final int[][] lineSprites = new int[SMS_HEIGHT][1 + (3 * SPRITES_PER_LINE)];

    /** Address of background table (32x28x2 = 0x700 bytes) */
    private int bgt;

    /** This would be different in 224 line mode */
    private final static int BGT_LENGTH = 32 * 28 * 2;

    /** As vscroll cannot be changed during the active display period */
    private int vScrollLatch;

    // --------------------------------------------------------------------------------------------
    // Emulation Related
    // --------------------------------------------------------------------------------------------
    /**
     * Sprite Collisions
     */
    private boolean[] spriteCol;
    /**
     * Emulated display
     */
    private int[] display;
    private int[] ggDisplay; //only for GG mode

    /** SMS Colours converted to Java */
    private static int[] SMS_JAVA;

    /** GG Colours converted to Java */
    private static int[] GG_JAVA1, GG_JAVA2;

    /** Horizontal Viewport Start */
    public static int h_start;

    /** Horizontal Viewport End */
    public static int h_end;

    // --------------------------------------------------------------------------------------------
    // Decoded SAT Table
    // --------------------------------------------------------------------------------------------

    /** Address of sprite attribute table (256 bytes) */
    private int sat;

    /** Determine whether SAT has been written to */
    private boolean isSatDirty;

    /** Max number of sprites hardware can handle per scanline */
    private final static int SPRITES_PER_LINE = 8;
    private int[] screenData = new int[0];

    /** References into lineSprites table */
    private final static int
            SPRITE_COUNT = 0,   // Number of sprites on line

    SPRITE_X = 1,       // Sprite X Position
            SPRITE_Y = 2,       // Sprite Y Position
            SPRITE_N = 3;       // Sprite Pattern

    // --------------------------------------------------------------------------------------------
    // Decoded Tiles
    // --------------------------------------------------------------------------------------------

    /** Total number of tiles in VRAM */
    private final static int TOTAL_TILES = 512;

    /** Tile size */
    private final static int TILE_SIZE = 8;

    /** Decoded tile data */
    private int[][] tiles;

    /** Store whether tile has been written to */
    private boolean[] isTileDirty;

    /** Min / Max of dirty tile index */
    private int minDirty, maxDirty;

    // --------------------------------------------------------------------------------------------

    private VdpInterruptHandler interruptHandler;
    private VdpMemory memory;
    private final boolean isSms;
    private VideoMode ggVideoMode = VideoMode.NTSCU_H20_V18;
    private RegionDetector.Region region;
    private SystemLoader.SystemType systemType;
    private VideoMode videoMode;
    private List<VdpEventListener> list;

    /**
     *  Vdp Constructor
     */

    public SmsVdp(SystemProvider systemProvider) {
        this(systemProvider.getSystemType(), systemProvider.getRegion());
    }

    public SmsVdp(SystemLoader.SystemType systemType, RegionDetector.Region region) {
        isSms = systemType == SystemLoader.SystemType.SMS;
        setSystemType(systemType);

        // 15 Registers, (0-10) used by SMS, but some programs write > 10
        vdpreg = new int[VDP_REGISTERS_SIZE];

        bgPriority = new boolean[SMS_WIDTH];

        spriteCol = new boolean[SMS_WIDTH];

        createCachedImages();

        this.region = region;
        this.list = new ArrayList<>();
        interruptHandler = SmsVdpInterruptHandler.createSmsInstance(this);

        // Note, we don't directly emulate CRAM but actually store the converted Java palette
        // in it. Therefore the length is different to on the real GameGear where it's actually
        // 64 bytes.
        memory = SimpleVdpMemoryInterface.createInstance(VDP_VRAM_SIZE, VDP_CRAM_SIZE);
        VRAM = memory.getVram();
        CRAM = memory.getCram();
        resetVideoMode(true);
    }

    /**
     *  Reset VDP.
     */
    @Override
    public final void reset() {
        generateConvertedPals();

        firstByte = true;

        location = 0;
        counter = 0;
        status = 0;
        operation = 0;
        vdpreg[0] = 4;  //m2 = 0, m4 = 1
        vdpreg[1] = 0;
        vdpreg[2] = 0x0E;       // B1-B3 high on startup
        vdpreg[3] = 0;
        vdpreg[4] = 0;
        vdpreg[5] = 0x7E;       // B1-B6 high on startup
        vdpreg[6] = 0;
        vdpreg[7] = 0;
        vdpreg[8] = 0;
        vdpreg[9] = 0;
        vdpreg[10] = 0;

        vScrollLatch = 0;
        isSatDirty = true;

        minDirty = TOTAL_TILES;
        maxDirty = -1;

        resetVideoMode(true);
    }

    @Override
    public void init() {
        reset();
    }

    /**
     *  Set SMS/GG Mode
     */
    private void setSystemType(SystemLoader.SystemType type){
        systemType = type;

        h_start = isSms ? 0 : 5;
        h_end   = isSms ? 32 : 27;

        if (!isSms) {
            int nump = ggVideoMode.getDimension().width * ggVideoMode.getDimension().height;
            ggDisplay = new int[nump];
        }
        ggVideoMode = isSms ? videoMode : VideoMode.NTSCU_H20_V18;

        LOG.info("Setting {} mode", type);
    }

    public void setRegion(RegionDetector.Region region) {
        this.region = region;
    }

    public void resetVideoMode(boolean force) {
        //m4 | m3 | m2 | m1
        int data = (vdpreg[0] & 4) << 1 | (vdpreg[1] & 0x8) >> 1 | (vdpreg[0] & 2) | (vdpreg[1] & 0x10) >> 4;
        VideoMode newVideoMode;
        switch (data) {
            case 14:
                newVideoMode = region == EUROPE ? VideoMode.PAL_H32_V30 :
                        (region == USA ? VideoMode.NTSCU_H32_V30 : VideoMode.NTSCJ_H32_V30);
                break;
            case 11:
                newVideoMode = region == EUROPE ? VideoMode.PAL_H32_V28 :
                        (region == USA ? VideoMode.NTSCU_H32_V28 : VideoMode.NTSCJ_H32_V30);
                break;
            case 8:
            case 10:
            default:
                newVideoMode = region == EUROPE ? VideoMode.PAL_H32_V24 :
                        (region == USA ? VideoMode.NTSCU_H32_V24 : VideoMode.NTSCJ_H32_V24);
                break;

        }
        if (videoMode != newVideoMode || force) {
            this.videoMode = newVideoMode;
            palFlag = videoMode.isPal() ? PAL : NTSC;
            display = new int[videoMode.getDimension().width * videoMode.getDimension().height];
            screenData = isSms ? display : ggDisplay;
            forceFullRedraw();
            LOG.info("Video mode changed: " + videoMode + ", " + videoMode.getDimension());
            list.forEach(l -> l.onVdpEvent(VdpEvent.VIDEO_MODE, newVideoMode));
        }
    }

    /**
     * Force full redraw of entire cache
     */

    public final void forceFullRedraw() {
        refreshBgtAddress(vdpreg[2]);
        minDirty = 0;
        maxDirty = TOTAL_TILES - 1;
        Arrays.fill(isTileDirty, true);
        refreshSatAddress(vdpreg[5]);
        isSatDirty = true;
    }

    public int getHCount(){
        return interruptHandler.getHCounterExternal();
    }

    public int getVCount(){
        return interruptHandler.getVCounterExternal();
    }

    public int getStatus() {
        return status;
    }

    /**
     *  Read VDP Control Port (0xBF)
     *
     *  @return     Copy of Status Register
     */

    public final int controlRead() {
        // Reset flag
        firstByte = true;

        // Create copy, as we'll need to clear bits of status reg
        int statuscopy = status;

        // Clear b7, b6, b5 when status register read
        status = 0; // other bits never used anyway

        interruptHandler.setHIntPending(false);
        interruptHandler.setvIntPending(false);

        return statuscopy;
    }

    /**
     *  Write to VDP Control Port (0xBF)
     *
     *  @param  value   Value to Write
     */

    public final void controlWrite(int value) {
        // Store First Byte of Command Word
        if (firstByte) {
            firstByte = false;
            commandByte = value;
            location = (location & 0x3F00) | value;
        } else {
            firstByte = true;
            operation = (value >> 6) & 3;
            location = commandByte | (value << 8);

            // Read value from VRAM
            if (operation == 0) {
                readBuffer = VRAM[(location++) & 0x3FFF];
            }
            // Set VDP Register
            else if (operation == 2) {
                registerWrite(value, commandByte);
            }
        }
    }

    public void registerWrite(int value, int commandByte) {
        int reg = (value & 0x0F);

        switch (reg) {
            // Interrupt Control 0 (Verified using Charles MacDonald test program)
            // Bit 4 of register $00 acts like a on/off switch for the VDP's IRQ line.

            // As long as the line interrupt pending flag is set, the VDP will assert the
            // IRQ line if bit 4 of register $00 is set, and it will de-assert the IRQ line
            // if the same bit is cleared.
            case 0:
                break;
            // Interrupt Control 1
            case 1:
                // By writing here we've updated the height of the sprites and need to update
                // the sprites on each line
                if ((commandByte & 3) != (vdpreg[reg] & 3))
                    isSatDirty = true;
                break;

            // BGT Written
            case 2:
                // Address of Background Table in VRAM
                refreshBgtAddress(commandByte);
                break;

            // SAT Written
            case 5: {
                int old = sat;
                // Address of Sprite Attribute Table in RAM
                refreshSatAddress(commandByte);
                if (old != sat) {
                    // Should also probably update tiles here?
                    isSatDirty = true;
                    //System.out.println("New address written to SAT: "+old + " -> " + sat);
                }

            }
            break;
            //hLine counter
            case 0xA:
                if (commandByte != vdpreg[reg]) {
                    list.forEach(l -> l.onVdpEvent(VdpEvent.H_LINE_COUNTER, commandByte));
                }
                break;
        }
        int prev = vdpreg[reg];
        vdpreg[reg] = commandByte; // Set reg to previous byte
        if (reg < 2 && prev != vdpreg[reg]) {
            resetVideoMode(false);
        }
    }

    /**
     *  Read VDP Data Port (0xBE)
     *
     *  @return     Buffered read from VRAM
     */

    public final int dataRead() // 0xBE
    {
        firstByte = true; // Reset flag

        int value = readBuffer; // Stores value to be returned
        readBuffer = VRAM[(location++) & 0x3FFF];

        return value;
    }

    /**
     *  Write to VDP Data Port (0xBE)
     *
     *  @param  value   Value to Write
     */

    public final void dataWrite(int value) {
        // Reset flag
        firstByte = true;
        value &= 0xFF;

        switch(operation) {
            // VRAM Write
            case 0x00:
            case 0x01:
            case 0x02: {
                int address = location & 0x3FFF;
                // Check VRAM value has actually changed
                if (value != VRAM[address]) {
                    //if (address >= bgt && address < bgt + BGT_LENGTH); // Don't write dirty to BGT
                    if (address >= sat && address < sat+64) // Don't write dirty to SAT
                        isSatDirty = true;
                    else if (address >= sat+128 && address < sat+256)
                        isSatDirty = true;
                    else {
                        int tileIndex = address >> 5;

                        // Get tile number that's being written to (divide VRAM location by 32)
                        isTileDirty[tileIndex] = true;
                        if (tileIndex < minDirty) minDirty = tileIndex;
                        if (tileIndex > maxDirty) maxDirty = tileIndex;
                    }

                    VRAM[address] = value;
                }
            }

            break;
            // CRAM Write
            // Instead of writing real colour to CRAM, write converted Java palette colours for speed.
            // Slightly inaccurate, as CRAM doesn't contain real values, but it is never read by software.
            case 0x03:
                if (isSms) {
                    CRAM[location & 0x1F] = SMS_JAVA[value & 0x3F];
                } else {
                    if ((location & 1) == 0) // first byte
                        CRAM[(location & 0x3F)>>1] = GG_JAVA1[value]; // GG
                    else
                        CRAM[(location & 0x3F)>>1] |= GG_JAVA2[value & 0x0F];

                }
                break;
        }
        readBuffer = value;

        location++;
    }

    public boolean isVINT(){
        return (vdpreg[1] & 0x20) > 0 && (status & STATUS_VINT) > 0;
    }

    public boolean isHINT(){
        return (vdpreg[0] & 0x10) > 0 && (status & STATUS_HINT) > 0;
    }

    public VdpInterruptHandler getInterruptHandler() {
        return interruptHandler;
    }

    private void refreshBgtAddress(int reg2) {
        bgt = videoMode.isV24() ? (reg2 & 0xE) << 10 : 0x700 + ((reg2 & 0xC) << 10);
    }

    private void refreshSatAddress(int reg5) {
        sat = (reg5 & 0x7E) << 7;
    }

    /**
     *  Render Line of SMS/GG Display
     *
     *  @param  lineno  Line Number to Render
     */

    public final void drawLine(int lineno) {
        // ----------------------------------------------------------------------------------------
        // Check we are in the visible drawing region
        // ----------------------------------------------------------------------------------------
        if (!isSms) {
            if (lineno < GG_Y_OFFSET || lineno >= GG_Y_OFFSET + GG_HEIGHT)
                return;
        }

        // ----------------------------------------------------------------------------------------
        // Clear sprite collision array if enabled
        // ----------------------------------------------------------------------------------------
        for (int i = spriteCol.length; i-- != 0;)
            spriteCol[i] = false;
        // ----------------------------------------------------------------------------------------
        // Check Screen is switched on
        // ----------------------------------------------------------------------------------------
        if ((vdpreg[1] & 0x40) != 0) {
            // ------------------------------------------------------------------------------------
            // Draw Background Layer
            // ------------------------------------------------------------------------------------
            if (maxDirty != -1)
                decodeTiles();

            drawBg(lineno);

            // ------------------------------------------------------------------------------------
            // Draw Sprite Layer
            // ------------------------------------------------------------------------------------
            if (isSatDirty)
                decodeSat();

            if (lineSprites[lineno][SPRITE_COUNT] != 0)
                drawSprite(lineno);

            // ------------------------------------------------------------------------------------
            // Blank Leftmost Column (SMS Only)
            // ------------------------------------------------------------------------------------
            if (isSms && (vdpreg[0] & 0x20) != 0) {
                int colour = CRAM[16 + (vdpreg[7] & 0x0F)];
                int location = lineno << 8;

                // Don't use a loop here for speed purposes
                display[location++] = colour;
                display[location++] = colour;
                display[location++] = colour;
                display[location++] = colour;
                display[location++] = colour;
                display[location++] = colour;
                display[location++] = colour;
                display[location] = colour;
            }
        }
        // ----------------------------------------------------------------------------------------
        // Blank Display
        // ----------------------------------------------------------------------------------------
        else {
            drawBGColour(lineno);
        }
    }

    private final void drawBg(int lineno) {
        // Horizontal Scroll
        int hscroll = vdpreg[8];

        // Vertical Scroll
        int vscroll = vScrollLatch;

        // Top Two Rows Not Affected by Horizontal Scrolling (SMS Only)
        // We don't actually need the SMS check here as we don't draw this line for GG now
        if (lineno < 16 && ((vdpreg[0] & 0x40) != 0) /*&& isSms*/)
            hscroll = 0;

        // Lock Right eight columns
        int lock = vdpreg[0] & 0x80;

        // Column to start drawing at (0 - 31) [Add extra columns for GG]
        int tile_column = (32 - (hscroll >> 3)) + h_start;

        // Row to start drawing at (0 - 27) for v24, (0 - 31) otherwise
        int vscrollShift = videoMode.isV24() ? 0x1C : 0x20;
        int tile_row = ((lineno + vscroll) >> 3);
        if (ENABLE_TILEMAP_MIRRORING) {
            tile_row &= ((vdpreg[2] & 1) << 4) | 0xF;
        }
        tile_row = tile_row >= vscrollShift ? tile_row - vscrollShift : tile_row;

        // Actual y position in tile (0 - 7) (Also times by 8 here for quick access to pixel)
        int tile_y = ((lineno + (vscroll & 7)) & 7) << 3;

        // Array Position
        int rowprecal = lineno << 8;

        // Cycle through background table
        for (int tx = h_start; tx < h_end; tx++) {
            int tile_props = bgt + ((tile_column & 0x1F) << 1) + (tile_row << 6);
            int secondbyte = VRAM[tile_props+1];

            // Select Palette (Either 0 or 16)
            int pal = (secondbyte & 0x08) << 1;

            // Screen X Position
            int sx = (tx << 3) + (hscroll & 7);

            // Do V-Flip (take into account the fact that everything is times 8)
            int pixY = ((secondbyte & 0x04) == 0) ? tile_y : ((7 << 3) - tile_y);

            // Pattern Number (0 - 512)
            int[] tile = tiles[VRAM[tile_props] + ((secondbyte & 0x01) << 8)];

            // -----------------------------------------------------------------------------------
            // Plot 8 Pixel Row (No H-Flip)
            // -----------------------------------------------------------------------------------
            if ((secondbyte & 0x02) == 0) {
                for (int pixX = 0; pixX < 8 && sx < SMS_WIDTH; pixX++, sx++) {
                    int colour = tile[pixX + pixY];

                    // Set Priority Array (Sprites over/under background tile)
                    bgPriority[sx] = ((secondbyte & 0x10) != 0) && (colour != 0);
                    display[sx + rowprecal] = CRAM[colour+pal];
                }
            }
            // -----------------------------------------------------------------------------------
            // Plot 8 Pixel Row (H-Flip)
            // -----------------------------------------------------------------------------------
            else {
                for (int pixX = 7; pixX >= 0 && sx < SMS_WIDTH; pixX--, sx++) {
                    int colour = tile[pixX + pixY];

                    // Set Priority Array (Sprites over/under background tile)
                    bgPriority[sx] = ((secondbyte & 0x10) != 0) && (colour != 0);
                    display[sx + rowprecal] = CRAM[colour+pal];
                }
            }
            tile_column++;

            // ------------------------------------------------------------------------------------
            // Rightmost 8 columns Not Affected by Vertical Scrolling
            // ------------------------------------------------------------------------------------
            if (lock != 0 && tx == 23) {
                tile_row = lineno >> 3;
                tile_y = (lineno & 7) << 3;
            }
        }
    }

    /**
     *  Render Line of Sprite Layer
     *
     * - Notes: Sprites do not wrap on the x-axis.
     *
     *  @param  lineno  Line Number to Render
     */

    private final void drawSprite(int lineno) {
        // Reference to the sprites that should appear on this line
        int[] sprites = lineSprites[lineno];

        // Number of sprites to draw on this scanline
        int count = Math.min(SPRITES_PER_LINE, sprites[SPRITE_COUNT]);

        // Zoom Sprites (0 = off, 1 = on)
        int zoomed = vdpreg[1] & 0x01;

        int row_precal = lineno << 8;

        // Get offset into array
        int off = (count * 3);

        // Have to iterate backwards here as we've already cached tiles
        for (int i = count; i-- != 0;) {
            // Sprite Pattern Index
            // Also mask on Pattern Index from 100 - 1FFh (if reg 6 bit 3 set)
            int n = sprites[off--] | ((vdpreg[6] & 0x04) << 6);

            // Sprite Y Position
            int y = sprites[off--];

            // Sprite X Position
            // Shift pixels left by 8 if necessary
            int x = sprites[off--] - (vdpreg[0] & 0x08);

            // Row of tile data to render (0-7)
            int tileRow = (lineno - y) >> zoomed;

            // When using 8x16 sprites LSB has no effect
            if ((vdpreg[1] & 0x02) != 0)
                n &= ~0x01;

            // Pattern Number (0 - 512)
            int[] tile = tiles[n + ((tileRow & 0x08) >> 3)];

            // If X Co-ordinate is negative, do a fix to draw from position 0
            int pix = 0;

            if (x < 0) {
                pix = (-x);
                x = 0;
            }

            // Offset into decoded tile data
            int offset = pix + ((tileRow & 7) << 3);

            // --------------------------------------------------------------------------------
            // Plot Normal Sprites (Width = 8)
            // --------------------------------------------------------------------------------
            if (zoomed == 0) {
                for (; pix < 8 && x < SMS_WIDTH; pix++, x++) {
                    int colour = tile[offset++];

                    if (colour != 0 && !bgPriority[x]) {
                        display[x + row_precal] = CRAM[colour+16];

                        // Emulate sprite collision (when two opaque pixels overlap)
                        if (!spriteCol[x])
                            spriteCol[x] = true;
                        else
                            status |= 0x20; // Bit 5 of status flag indicates collision
                    }
                }
            }
            // --------------------------------------------------------------------------------
            // Plot Zoomed Sprites (Width = 16)
            // --------------------------------------------------------------------------------
            else {
                for (; pix < 8 && x < SMS_WIDTH; pix++, x += 2) {
                    int colour = tile[offset++];

                    // Plot first pixel
                    if (colour != 0 && !bgPriority[x]) {
                        display[x + row_precal] = CRAM[colour+16];
                        if (!spriteCol[x])
                            spriteCol[x] = true;
                        else
                            status |= 0x20; // Bit 5 of status flag indicates collision
                    }

                    // Plot second pixel
                    if (colour != 0 && !bgPriority[x+1]) {
                        display[x + row_precal + 1] = CRAM[colour+16];
                        if (!spriteCol[x+1])
                            spriteCol[x+1] = true;
                        else
                            status |= 0x20; // Bit 5 of status flag indicates collision
                    }
                }
            }
        }

        // Sprite Overflow (more than 8 sprites on line)
        if (sprites[SPRITE_COUNT] >= SPRITES_PER_LINE) {
            status |= 0x40;
        }
    }


    /**
     *  Draw a Line of the current Background Colour
     *
     *  @param  lineno  Line Number to Render
     */

    private final void drawBGColour(int lineno) {
        int colour = CRAM[16 + (vdpreg[7]&0x0F)];
        int row_precal = lineno << 8;

        for (int x = SMS_WIDTH; x-- != 0;)
            display[row_precal++] = colour;
    }


    // --------------------------------------------------------------------------------------------
    // Generated pre-converted palettes.
    //
    // SMS and GG colours are converted to Java RGB for speed purposes
    //
    // Java: 0xAARRGGBB (4 bytes) Java colour
    //
    // SMS : 00BBGGRR   (1 byte)
    // GG  : GGGGRRRR   (1st byte)
    //       0000BBBB   (2nd byte)
    // --------------------------------------------------------------------------------------------

    private final void generateConvertedPals() {
        if (isSms && SMS_JAVA == null) {
            SMS_JAVA = new int[0x40];

            for (int i = 0; i < SMS_JAVA.length; i++) {
                int r = i & 0x03;
                int g = (i >> 2) & 0x03;
                int b = (i >> 4) & 0x03;

                SMS_JAVA[i] = ((r * 85) << 16) | ((g * 85) << 8) | (b * 85);
            }
        } else if (!isSms && GG_JAVA1 == null) {
            GG_JAVA1 = new int[0x100];
            GG_JAVA2 = new int[0x10];

            // Green & Blue
            for (int i = 0; i < GG_JAVA1.length; i++) {
                int g = i & 0x0F;
                int b = (i >> 4) & 0x0F;

                // Shift and fill with the original bitpattern
                // so %1111 becomes %11111111, %1010 becomes %10101010
                GG_JAVA1[i] = (g << 20) | (g << 16) | (b << 12) | (b << 8);
            }

            // Red
            for (int i = 0; i < GG_JAVA2.length; i++) {
                GG_JAVA2[i] = (i << 4) | i;
            }
        }
    }

    // --------------------------------------------------------------------------------------------
    // Decode all background tiles
    //
    // Tiles are 8x8
    //
    // Background table is a 32x28 matrix of words stored in VRAM
    //
    //  MSB          LSB
    //  ---pcvhnnnnnnnnn
    //
    // p = priority
    // c = palette
    // v = vertical flip
    // h = horizontal flip
    // n = pattern index (0 - 512)
    // --------------------------------------------------------------------------------------------

    private final void createCachedImages() {
        tiles = new int[TOTAL_TILES][TILE_SIZE * TILE_SIZE];
        isTileDirty = new boolean[TOTAL_TILES];
    }

    // Note we should try not to update the bgt/sat locations?
    private final void decodeTiles() {
        //System.out.println("["+line+"]"+" min dirty:" +minDirty+" max: "+maxDirty);

        for (int i = minDirty; i <= maxDirty; i++) {
            // Only decode tiles that have changed since the last iteration
            if (!isTileDirty[i]) continue;

            // Note that we've updated the tile
            isTileDirty[i] = false;

            //System.out.println("tile "+i+" is dirty");
            int[] tile = tiles[i];

            int pixel_index = 0;

            // 4 bytes per row, total of 32 bytes per tile
            int address = (i << 5);

            // Plot column of 8 pixels
            for (int y = 0; y < TILE_SIZE; y++) {
                int address0 = VRAM[address++];
                int address1 = VRAM[address++];
                int address2 = VRAM[address++];
                int address3 = VRAM[address++];

                // Plot row of 8 pixels
                for (int bit = 0x80; bit != 0; bit>>=1) {
                    int colour = 0;

                    // Set Colour of Pixel (0-15)
                    if ((address0 & bit) != 0) colour |= 0x01;
                    if ((address1 & bit) != 0) colour |= 0x02;
                    if ((address2 & bit) != 0) colour |= 0x04;
                    if ((address3 & bit) != 0) colour |= 0x08;

                    tile[pixel_index++] = colour;
                }
            }
        }

        // Reset min/max dirty counters
        minDirty = TOTAL_TILES;
        maxDirty = -1;
    }

    // --------------------------------------------------------------------------------------------
    //
    //  DECODE SAT TABLE
    //
    //   Each sprite is defined in the sprite attribute table (SAT), a 256-byte
    //   table located in VRAM. The SAT has the following layout:
    //
    //      00: yyyyyyyyyyyyyyyy
    //      10: yyyyyyyyyyyyyyyy
    //      20: yyyyyyyyyyyyyyyy
    //      30: yyyyyyyyyyyyyyyy
    //      40: ????????????????
    //      50: ????????????????
    //      60: ????????????????
    //      70: ????????????????
    //      80: xnxnxnxnxnxnxnxn
    //      90: xnxnxnxnxnxnxnxn
    //      A0: xnxnxnxnxnxnxnxn
    //      B0: xnxnxnxnxnxnxnxn
    //      C0: xnxnxnxnxnxnxnxn
    //      D0: xnxnxnxnxnxnxnxn
    //      E0: xnxnxnxnxnxnxnxn
    //      F0: xnxnxnxnxnxnxnxn
    //
    //   y = Y coordinate + 1
    //   x = X coordinate
    //   n = Pattern index
    //   ? = Unused
    // --------------------------------------------------------------------------------------------


    /**
     * Creates a list of sprites per scanline
     */

    private final void decodeSat() {
        isSatDirty = false;

        // ----------------------------------------------------------------------------------------
        // Clear Existing Table
        // ----------------------------------------------------------------------------------------

        for (int i = lineSprites.length; i-- != 0;)
            lineSprites[i][SPRITE_COUNT] = 0;

        // Height of Sprites (8x8 or 8x16)
        int height = (vdpreg[1] & 0x02) == 0 ? 8 : 16;

        // Enable Zoomed Sprites
        if ((vdpreg[1] & 0x01) == 0x01) {
            height <<= 1;
        }
        boolean isV24 = videoMode.isV24();
        // ----------------------------------------------------------------------------------------
        // Search Sprite Attribute Table (64 Bytes)
        // ----------------------------------------------------------------------------------------
        for (int spriteno = 0; spriteno < 0x40; spriteno++) {
            // Sprite Y Position
            int y = VRAM[sat + spriteno];

            // VDP stops drawing if y == 208, only for v24
            if (isV24 && y == 208) {
                return;
            }

            // y is actually at +1 of value
            y++;

            //TODO check this
            // If off screen, draw from negative 16 onwards
            if (y > 240) {
                y -= 256;
            }

            for (int lineno = 0; lineno < SMS_HEIGHT; lineno++) {
                // --------------------------------------------------------------------------------
                // Does Sprite fall on this line?
                // --------------------------------------------------------------------------------
                if ((lineno >= y) && ((lineno-y) < height)) {
                    int[] sprites = lineSprites[lineno];

                    if (sprites[SPRITE_COUNT] < SPRITES_PER_LINE) {
                        // Get offset into array
                        int off = (sprites[SPRITE_COUNT] * 3) + SPRITE_X;

                        // Address of Sprite in Sprite Attribute Table
                        int address = sat + (spriteno<<1) + 0x80;

                        // Sprite X Position
                        sprites[off++] = VRAM[address++];

                        // Sprite Y Position
                        sprites[off++] = y;

                        // Sprite Pattern Index
                        sprites[off++] = VRAM[address];

                        // Increment number of sprites on this scanline
                        sprites[SPRITE_COUNT]++;
                    }
                }
            }
        }
    }

    // --------------------------------------------------------------------------------------------
    // VDP State Saving
    // --------------------------------------------------------------------------------------------

    public int[] getState() {
        int[] state = new int[3 + vdpreg.length + CRAM.length];

        state[0] = palFlag | (status << 8) | (firstByte ? (1 << 16) : 0) | (commandByte << 24);
        state[1] = location | (operation << 16) | (readBuffer << 24);
        state[2] = counter | (vScrollLatch << 8) | (line << 16);

        System.arraycopy(vdpreg, 0, state, 3, vdpreg.length);
        System.arraycopy(CRAM, 0, state, 3 + vdpreg.length, CRAM.length);

        return state;
    }

    public int[] getStateSimple(int[] state) {
        state[0] = palFlag | (status << 8) | (firstByte ? (1 << 16) : 0) | (commandByte << 24);
        state[1] = location | (operation << 16) | (readBuffer << 24);
        state[2] = counter | (vScrollLatch << 8) | (line << 16);
        return state;
    }

    public void setStateSimple(int[] state) {
        int temp = state[0];
        palFlag = temp & 0xFF;
        status = (temp >> 8) & 0xFF;
        firstByte = ((temp >> 16) & 0xFF) != 0;
        commandByte = (temp >> 24) & 0xFF;

        temp = state[1];
        location = temp & 0xFFFF;
        operation = (temp >> 16) & 0xFF;
        readBuffer = (temp >> 24) & 0xFF;

        temp = state[2];
        counter = temp & 0xFF;
        vScrollLatch = (temp >> 8) & 0xFF;
        line = (temp >> 16) & 0xFFFF;
    }

    public void setState(int[] state) {
        setStateSimple(state);

        System.arraycopy(state, 3, vdpreg, 0, vdpreg.length);
        System.arraycopy(state, 3 + vdpreg.length, CRAM, 0, CRAM.length);

        // Force redraw of all cached tile data
        forceFullRedraw();
    }

    /**
     * Run
     *
     * @return 1 - vblank has been triggered, 0 - otherwise
     */
    @Override
    public int runSlot() {
        line = interruptHandler.getvCounterInternal();
        boolean vBlank = interruptHandler.isvBlankSet();
        interruptHandler.increaseHCounter();
        boolean vBlankTrigger = !vBlank && interruptHandler.isvBlankSet();
        int newLine = interruptHandler.getvCounterInternal();

        int h = videoMode.getDimension().height;
        if(line != newLine && !vBlank && line < h){
//            System.out.println("DrawLine: " + line);
            if(line == 0){
                vScrollLatch = vdpreg[9];
            }
            drawLine(line);
        }
        //http://www.smspower.org/forums/viewtopic.php?t=9366&highlight=chicago
        status |= vBlankTrigger ? STATUS_VINT : 0;
        status |= interruptHandler.isHIntPending() ? STATUS_HINT : 0;
        if (vBlankTrigger) {
            resizeGG(!isSms);
            list.forEach(VdpEventListener::onNewFrame);
        }
        return 0;
    }

    private void resizeGG(boolean doResize) {
        if (!doResize) {
            return;
        }
        RenderingStrategy.subImageWithOffset(display, ggDisplay, videoMode.getDimension(),
                ggVideoMode.getDimension(), SmsVdp.GG_X_OFFSET,
                SmsVdp.GG_Y_OFFSET);
    }

    @Override
    public List<VdpEventListener> getVdpEventListenerList() {
        return list;
    }

    @Override
    public void dumpScreenData() {
        //TODO
    }

    @Override
    public int getRegisterData(int reg) {
        return vdpreg[reg];
    }

    @Override
    public void updateRegisterData(int reg, int data) {
        vdpreg[reg] = data;
    }

    @Override
    public boolean isDisplayEnabled() {
        return (vdpreg[1] & 0x40) != 0;
    }

    @Override
    public VideoMode getVideoMode() {
        return isSms ? videoMode : ggVideoMode;
    }

    @Override
    public VdpMemory getVdpMemory() {
        return memory;
    }

    @Override
    public int[] getScreenDataLinear() {
        return screenData;
    }
}
