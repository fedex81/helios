/*
 * Tms9918aVdp
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

package omegadrive.vdp;

import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import omegadrive.util.VideoMode;
import omegadrive.vdp.gen.VdpInterruptHandler;
import omegadrive.vdp.model.RenderType;
import omegadrive.vdp.model.Tms9918a;
import omegadrive.vdp.model.VdpMemory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static omegadrive.vdp.model.Tms9918a.TmsRegisterName.*;

/**
 * Modified version of:
 * https://github.com/jitze/TMSX/blob/master/MSXEMU/src/emu/TMS9918A.java
 * @author Tjitze Rienstra
 */
public class Tms9918aVdp implements Tms9918a {

    private static final Logger LOG = LogManager.getLogger(Tms9918aVdp.class.getSimpleName());

    private static boolean verbose = false;

    public static final int
            VDP_WIDTH = 256,
            VDP_HEIGHT = 192,
            MODE0_OFFSET = 8;

    private TmsMode vdpMode;
    private VdpInterruptHandler interruptHandler;
    private VdpMemory memory;
    private VdpRenderDump renderDump;
    private java.util.List<VdpEventListener> list;
    private int[] screenDataLinear;

    /* VRAM */
    public int[] mem;

    /* Registers */
    private int[] registers = new int[REGISTERS];
    private byte statusRegister = 0;

    /* I/O variables */
    private short readWriteAddr;
    private byte readAhead;
    private boolean secondByteFlag = false;
    private byte ioByte0, ioByte1;

    private int[] spriteLineCount = new int[VDP_HEIGHT + 16];
    private boolean[][] spritePriorityMatrix = new boolean[VDP_WIDTH + 16][VDP_HEIGHT + 16];
    private boolean[][] spriteCollisionMatrix = new boolean[VDP_WIDTH + 16][VDP_HEIGHT + 16];

    public Tms9918aVdp() {
        setupVdp();
        init();
    }

    public void reset() {
        Arrays.fill(registers, 0);
        Arrays.fill(mem, 0);
        registers[1] = 0x10;

        readWriteAddr = 0;
        secondByteFlag = false;
        ioByte0 = 0;
        ioByte1 = 0;
        statusRegister = 0;

        updateTmsMode();
    }

    @Override
    public void init() {
        reset();
    }

    private void setupVdp() {
        this.list = new ArrayList<>();
        memory = SimpleVdpMemoryInterface.createInstance(RAM_SIZE);
        screenDataLinear = new int[VDP_WIDTH * VDP_HEIGHT];
        interruptHandler = SmsVdpInterruptHandler.createTmsInstance(getVideoMode());
        mem = memory.getVram();
        renderDump = new VdpRenderDump();
    }

    @Override
    public int runSlot() {
        boolean vBlank = interruptHandler.isvBlankSet();
        interruptHandler.increaseHCounter();
        boolean vBlankTrigger = !vBlank && interruptHandler.isvBlankSet();
        if (vBlankTrigger) {
            setStatusINT(true);
            drawScreen();
        }
        if (interruptHandler.isEndOfFrameCounter()) {
            list.forEach(VdpEventListener::onNewFrame);
        }
        return 0;
    }

    @Override
    public int[] getScreenDataLinear() {
        return screenDataLinear;
    }

    @Override
    public int getRegisterData(int reg) {
        return registers[reg];
    }

    @Override
    public void updateRegisterData(int reg, int data) {
        registers[reg] = (byte) data;
    }

    @Override
    public VideoMode getVideoMode() {
        return VideoMode.NTSCJ_H32_V24;
    }

    @Override
    public void setRegion(RegionDetector.Region region) {
        //do nothing
    }

    @Override
    public VdpMemory getVdpMemory() {
        return memory;
    }

    public VdpInterruptHandler getInterruptHandler() {
        return interruptHandler;
    }

    private void updateTmsMode() {
        TmsMode mode = null;
        int val = (getM1() ? 1 : 0) << 0 | (getM2() ? 1 : 0) << 1 | (getM3() ? 1 : 0) << 2;
        switch (val) {
            case 0:
                mode = TmsMode.MODE_0;
                break;
            case 1:
            case 3:
                mode = TmsMode.MODE_1;
                break;
            case 2:
                mode = TmsMode.MODE_2;
                break;
            case 4:
                mode = TmsMode.MODE_3;
                break;
            default:
                LOG.error("Unsupported mode selected: {}", val);
                mode = vdpMode;
                break;
        }
        if (mode != vdpMode) {
            LOG.info("Mode change from: {}, to: {}", vdpMode, mode);
            vdpMode = mode;
        }

    }

    public boolean getStatusBit(int bit) {
        return getBit(statusRegister, bit);
    }

    public void setStatusBit(int bit, boolean v) {
        statusRegister = setBit(statusRegister, bit, v);
    }

    public boolean getStatusINT() {
        return getStatusBit(7);
    }

    public void setStatusINT(boolean v) {
        setStatusBit(7, v);
    }

    public void setStatus5S(boolean v) {
        setStatusBit(6, v);
    }

    public void setStatusC(boolean v) {
        setStatusBit(5, v);
    }

    public boolean getRegisterBit(int reg, int bit) {
        return getBit(registers[reg], bit);
    }

    public void setRegisterBit(int reg, int bit, boolean value) {
        registers[reg] = setBit(registers[reg], bit, value);
        if (reg < 2) {
            updateTmsMode();
        }
    }

    public static int setBit(int in, int bit, boolean v) {
        return v ? (in | (1 << bit)) : (in & ((~1) << bit));
    }

    public static byte setBit(byte in, int bit, boolean v) {
        return v ? (byte) (in | (1 << bit)) : (byte) (in & ((~1) << bit));
    }

    public static boolean getBit(byte in, int bit) {
        return (in & (1 << bit)) != 0;
    }

    public static boolean getBit(int in, int bit) {
        return (in & (1 << bit)) != 0;
    }

    public boolean getEXTVID() {
        return getRegisterBit(0, 0);
    }

    public boolean getM2() {
        return getRegisterBit(0, 1);
    }

    public boolean getMAG() {
        return getRegisterBit(1, 0);
    }

    public boolean getSI() {
        return getRegisterBit(1, 1);
    }

    public boolean getM3() {
        return getRegisterBit(1, 3);
    }

    public boolean getM1() {
        return getRegisterBit(1, 4);
    }

    public boolean getGINT() {
        return getRegisterBit(1, 5);
    }

    public void setGINT(boolean b) {
        setRegisterBit(1, 5, b);
    }

    public boolean getBL() {
        return getRegisterBit(1, 6);
    }

    public boolean get416K() {
        return getRegisterBit(1, 7);
    }

    public boolean getPG13() {
        return getRegisterBit(4, 2);
    }

    public boolean getCT13() {
        return getRegisterBit(3, 7);
    }

    public int getNameTableAddr() {
        return (registers[TILEMAP_NAMETABLE.ordinal()] & 0xF) << 10;
    }

    public int getColorTableAddr() {
        return (registers[COLORMAP_NAMETABLE.ordinal()] << 6);
    }

    public int getPatternTableAddr() {
        return (registers[TILE_START_ADDRESS.ordinal()] & 0x7) << 11;
    }

    public int getSpriteAttrTable() {
        return (registers[SPRITE_TABLE_LOC.ordinal()] & 0x7F) << 7;
    }

    public int getSpriteGenTable() {
        return (registers[SPRITE_TILE_BASE_ADDR.ordinal()] & 0x7) << 11;
    }

    public int getOnBitColor() {
        return registers[BACKGROUND_COLOR.ordinal()] >> 4;
    }

    public int getOffBitColor() {
        return registers[BACKGROUND_COLOR.ordinal()] & 0x0F;
    }

    public int getFifthSpriteNr() {
        return registers[BACKGROUND_COLOR.ordinal()] & 0x0F;
    }

    public int getSpriteX(int sprite) {
        int attrTable = getSpriteAttrTable();
        return mem[attrTable + (sprite * 4) + 1];
    }

    public int getSpriteY(int sprite) {
        int attrTable = getSpriteAttrTable();
        return mem[attrTable + (sprite * 4) + 0];
    }

    public int getSpritePattern(int sprite) {
        int attrTable = getSpriteAttrTable();
        return mem[attrTable + (sprite * 4) + 2];
    }

    public int getSpriteColour(int sprite) {
        int attrTable = getSpriteAttrTable();
        return mem[attrTable + (sprite * 4) + 3];
    }

    /**
     * Port 0 read.
     *
     * @return
     */
    public final byte readVRAMData() {
        byte result = readAhead;
        readAhead = (byte) mem[readWriteAddr];
        LogHelper.printLevel(LOG, Level.INFO, "vdpRead addr: {}, data: {}", readWriteAddr, readAhead, verbose);
        increaseReadWriteAddr();
        secondByteFlag = false;
        return result;
    }

    /**
     * Port 0 write.
     *
     * @param value
     */
    public final void writeVRAMData(byte value) {
        readAhead = value;
        mem[readWriteAddr] = readAhead;
        LogHelper.printLevel(LOG, Level.INFO, "vdpWrite addr: {} , data: {}", readWriteAddr, value & 0xFF, verbose);
        increaseReadWriteAddr();
        secondByteFlag = false;
    }

    /**
     * Write port one. See documentation.
     *
     * @param value
     */
    public final void writeRegister(byte value) {
        if (!secondByteFlag) {
            ioByte0 = value;
            secondByteFlag = true;
        } else {
            ioByte1 = value;
            boolean isReg = ((ioByte1 & 0x80) >> 7) == 1;

            /* Are we doing memory i/o? */
            if (!isReg) {

                /* If so, set the read/write address to value stored in ioBytes */
                readWriteAddr = (short) ((ioByte0 & 0xFF) | ((ioByte1 & 0x003F) << 8));

                /* In case of read: fill read ahead buffer and increase read/write address */
                if (((ioByte1 & 0xC0) >> 6) == 0) {
                    readAhead = (byte) mem[readWriteAddr];
                    increaseReadWriteAddr();
                }
            }

            /* If not, we're doing register i/o */
            else {
                int regNum = ((int) ioByte1) & 0x07;
                updateRegister(regNum, ioByte0 & 0xFF);
            }

            secondByteFlag = false;
        }

    }

    private void updateRegister(int regNum, int value) {
        if (regNum < REGISTERS && registers[regNum] != value) {
            LogHelper.printLevel(LOG, Level.INFO, "vdpWriteReg {}, data: {}", regNum, value & 0xFF, verbose);
            registers[regNum] = value;
            if (regNum < 2) {
                updateTmsMode();
            }
        }
    }

    public final void increaseReadWriteAddr() {
        readWriteAddr = (short) ((readWriteAddr + 1) & 0x3fff);
    }

    /*
     * Return value of status bit. The side-effects of doing this is
     * that the INT and C flags are reset, and that the second byte
     * flag is reset, which affects the writeRegister behaviour.
     */
    public final byte readStatus() {
        secondByteFlag = false;
        byte value = statusRegister;
        setStatusINT(false);
        setStatusC(false);
        return value;
    }

    /**
     * Draw the backdrop
     */
    public void drawBackDrop() {
        for (int x = 0; x < 256; x++) {
            for (int y = 0; y < 192; y++) {
                setPixel(x, y, colors[1]); //black
            }
        }
    }

    /**
     * Draw pattern according to mode 0 (screen 1 / graphic 1)
     */
    public void drawMode0() {
        int nameTablePtr = getNameTableAddr();
        int patternTableBase = getPatternTableAddr();
        int colorTableBase = getColorTableAddr();
        // For all x/y positions
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 32; x++) {
                // Read index of pattern from name table address
                int patternIdx = mem[nameTablePtr] & 0xFF;
                int patternAddr = patternTableBase + (patternIdx << 3);
                // For all lines of the character
                for (int charLine = 0; charLine < 8; charLine++) {
                    int line = mem[patternAddr + charLine] & 0xFF;
                    // For all pixels of the line
                    for (int linePos = 0; linePos < 8; linePos++) {
                        // Calculate location of pixel
                        int px = 7 + ((x * 8) - linePos);
                        int py = ((y * 8) + charLine);
                        // Get foreground/background
                        int colorTableAddr = colorTableBase + (patternIdx >> 3);
                        int color = mem[colorTableAddr] & 0xFF;
                        Color fg = colors[(color & 0xf0) >> 4];
                        Color bg = colors[(color & 0x0f)];
                        setPixel(px, py, getBit(line, linePos) ? fg : bg);
                    }
                }
                // Update name table pointer
                nameTablePtr++;
            }
        }
    }

    private final void setPixel(int px, int py, Color color) {
        screenDataLinear[py * VDP_WIDTH + px] = color.getRGB() & 0xFF_FFFF; //24 bit RGB
        if (verbose) {
            LOG.info("{},{}: {}", px, py, screenDataLinear[py * VDP_WIDTH + px]);
        }
    }

    /**
     * Draw pattern according to mode 1 (screen 0 / text 1)
     */
    public void drawMode1() {
        int nameTablePtr = getNameTableAddr();
        int patternTableBase = getPatternTableAddr();
        Color offBit = colors[getOffBitColor()];
        Color onBit = colors[getOnBitColor()];
        // For all x/y positions
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 40; x++) {
                // Read index of pattern from name table address
                int patternIdx = mem[nameTablePtr] & 0xFF;
                int patternAddr = patternTableBase + (patternIdx << 3);
                // For all lines of the character
                for (int charLine = 0; charLine < 8; charLine++) {
                    int line = mem[patternAddr + charLine] & 0xFF;
                    // For all pixels of the line
                    for (int linePos = 0; linePos < 6; linePos++) {
                        // Calculate location of pixel
                        int px = 5 + ((x * 6) - linePos);
                        int py = ((y * 8) + charLine);
                        // Set pixel
                        setPixel(px + MODE0_OFFSET, py, getBit(line, linePos + 2) ? onBit : offBit);
                    }
                }
                nameTablePtr++;
            }
        }
    }

    /**
     * Draw pattern according to mode 1 (screen 2 / graphic 2)
     */
    public void drawMode2() {
        int nameTableBase = getNameTableAddr();
        int nameTableIdx = 0;
        int patternTableBase = getPG13() ? 0x2000 : 0;
        boolean bit0 = this.getRegisterBit(4, 0);
        boolean bit1 = this.getRegisterBit(4, 1);
        int colorTableBase = getCT13() ? 0x2000 : 0;
        // For all x/y positions
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 32; x++) {
                // Read index of pattern from name table address
                int patternIdx = mem[nameTableBase + nameTableIdx] & 0xFF;
                int patternAddr = patternTableBase + (patternIdx << 3);
                //patternAddr += (2048 * (nameTableIdx / 256));
                if (bit0 && (nameTableIdx / 256) == 1) patternAddr += 2048;
                if (bit1 && (nameTableIdx / 256) == 2) patternAddr += 4096;
                // For all lines of the character
                for (int charLine = 0; charLine < 8; charLine++) {
                    int line = mem[patternAddr + charLine] & 0xFF;
                    int colorTableAddr = colorTableBase + (patternIdx << 3);
                    //colorTableAddr += (2048 * (nameTableIdx / 256));
                    if (bit0 && (nameTableIdx / 256) == 1) colorTableAddr += 2048;
                    if (bit1 && (nameTableIdx / 256) == 2) colorTableAddr += 4096;
                    int lineColor = mem[colorTableAddr + charLine] & 0xFF;
                    Color fg = colors[(lineColor & 0xf0) >> 4];
                    Color bg = colors[(lineColor & 0x0f)];
                    // For all pixels of the line
                    int py = ((y * 8) + charLine);
                    for (int linePos = 0; linePos < 8; linePos++) {
                        // Calculate location of pixel
                        int px = 7 + ((x * 8) - linePos);
                        setPixel(px, py, getBit(line, linePos) ? fg : bg);
                    }
                }
                nameTableIdx++;
            }
        }
    }

    public void drawSprites() {
        int patternTableAddr = getSpriteGenTable();
        boolean siFlag = getSI();

        // Clear sprite per line count array
        for (int i = 0; i < VDP_HEIGHT; i++) spriteLineCount[i] = 0;

        // Clear priority array
        for (int x = 0; x < VDP_WIDTH; x++) for (int y = 0; y < VDP_HEIGHT; y++) spritePriorityMatrix[x][y] = false;

        // Clear collision array
        for (int x = 0; x < VDP_WIDTH; x++) for (int y = 0; y < VDP_HEIGHT; y++) spriteCollisionMatrix[x][y] = false;

        // For each sprite ...
        for (int i = 0; i < 32; i++) {

            // Break if 0xD0 encountered
            if ((getSpriteY(i) & 0xff) == 0xD0) break;

            // Get sprite info
            int sx = getSpriteX(i) & 0xff;
            int sy = (getSpriteY(i) & 0xff) + 1;
            int patternIdx = getSpritePattern(i) & 0xff;
            int colour = getSpriteColour(i);

            // If EC bit set: place sprite 32 pixels to the left
            if ((colour & 0x80) != 0) sx -= 32;

            // Keep track of number of sprites per line
            for (int yPos = sy; yPos < sy + (siFlag ? 16 : 8) && yPos < VDP_HEIGHT; yPos++) spriteLineCount[yPos]++;

            // If sprite is transparent then skip
            if ((colour & 0x0f) == 0) continue;

            // Draw sprite (qx/qy range over quadrants for 16x16 mode. In 8x8 mode, qx = qy = 0)
            for (int qx = 0; qx < (siFlag ? 2 : 1); qx++) {
                for (int qy = 0; qy < (siFlag ? 2 : 1); qy++) {
                    int quadrantNumber = (2 * qx) + qy;
                    for (int y = 0; y < 8; y++) {
                        for (int x = 0; x < 8; x++) {
                            int xPos = sx + x + (qx * 8), yPos = sy + y + (qy * 8);
                            if (xPos >= VDP_WIDTH || yPos >= VDP_HEIGHT || xPos < 0 || yPos < 0)
                                continue; // Out of bounds, skip

                            // Mark coincidence (TODO: is this correct timing-wise?)
                            if (spriteCollisionMatrix[xPos][yPos]) setStatusC(true);
                            spriteCollisionMatrix[xPos][yPos] = true;

                            // Do we need to fill this pixel?
                            boolean fill = (mem[(short) (patternTableAddr + (8 * (patternIdx + quadrantNumber) + y))] & (1 << (7 - x))) != 0;
                            if (!fill) continue;

                            // Are there already 4 sprites drawn on the current line? I so, mark 5th sprite flag and status register, and skip
                            if (spriteLineCount[yPos] > 4) {
                                setStatus5S(true);
                                statusRegister = (byte) ((statusRegister & 0xE0) | (i & 0x1F));
                                continue;
                            }

                            // We iterate from high priority to low priority. Skip if a higher priority sprite was already drawn here.
                            if (spritePriorityMatrix[xPos][yPos]) continue;
                            spritePriorityMatrix[xPos][yPos] = true;

                            // Draw the pixel
                            setPixel(xPos, yPos, colors[colour & 0x0f]);
                        }
                    }
                }
            }
        }
    }

    private void drawMode3() {
        if (!getBL()) {
            return;
        }
        int nameTableBase = getNameTableAddr();
        int patternTableBase = getPatternTableAddr();
        int nameTableAddr = nameTableBase;
        int patternTableAddr;

        for (int y = 0; y < 24; y++) {
            int py = y << 3;
            int ptShift = patternTableBase + ((y & 0x03) << 1);
            for (int x = 0; x < 32; x++) {
                int px = x << 3;
                patternTableAddr = ptShift + ((mem[nameTableAddr] & 0xFF) << 3);

                //top 2 blocks and bottom 2 blocks
                for (int i = 0; i < 2; i++) {
                    int byteColor = mem[patternTableAddr] & 0xFF;
                    Color c1 = colors[(byteColor >> 4) & 0x0F];
                    Color c2 = colors[byteColor & 0x0F];
                    int by = py + i * 4;
                    for (int blockIdx = 0; blockIdx < 4; blockIdx++) {
                        int uy = by + blockIdx;
                        setPixel(px, uy, c1);
                        setPixel(px + 1, uy, c1);
                        setPixel(px + 2, uy, c1);
                        setPixel(px + 3, uy, c1);

                        setPixel(px + 4, uy, c2);
                        setPixel(px + 5, uy, c2);
                        setPixel(px + 6, uy, c2);
                        setPixel(px + 7, uy, c2);
                    }
                    patternTableAddr++;
                }
                nameTableAddr++;
            }
        }
    }

    private void drawScreen() {
        // Draw backdrop
        drawBackDrop();

        // Draw pattern
        switch (vdpMode) {
            case MODE_0:
                drawMode0();
                break;
            case MODE_1:
                drawMode1();
                break;
            case MODE_2:
                drawMode2();
                break;
            case MODE_3:
                drawMode3();
                break;
        }
        // Draw sprites
        if (!getM1() && getBL()) {
            drawSprites();
        }
    }

    @Override
    public List<VdpEventListener> getVdpEventListenerList() {
        return list;
    }

    @Override
    public void dumpScreenData() {
        renderDump.saveRenderToFile(screenDataLinear, getVideoMode(), RenderType.FULL);
    }
}