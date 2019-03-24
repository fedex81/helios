package omegadrive.vdp;

import omegadrive.util.LogHelper;
import omegadrive.util.VideoMode;
import omegadrive.vdp.gen.VdpInterruptHandler;
import omegadrive.vdp.model.Tms9918a;
import omegadrive.vdp.model.VdpMemoryInterface;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.util.Arrays;

import static omegadrive.vdp.model.Tms9918a.TmsRegisterName.*;

/**
 * Sg1000Vdp
 *
 * @author Federico Berti
 * <p>
 * Modified version of:
 * https://github.com/jitze/TMSX/blob/master/MSXEMU/src/emu/TMS9918A.java
 * @author Tjitze Rienstra
 */
public class Sg1000Vdp implements Tms9918a {

    private static Logger LOG = LogManager.getLogger(Sg1000Vdp.class.getSimpleName());

    private static boolean verbose = false;

    public static final int
            VDP_WIDTH = 256,
            VDP_HEIGHT = 192,
            MODE0_OFFSET = 8;

    private TmsMode vdpMode;
    private VdpInterruptHandler interruptHandler;

    private int[][] screenData;

    /* VRAM */
    public byte[] mem;

    /* Registers */
    private byte[] registers = new byte[REGISTERS];
    private byte statusRegister = 0;

    /* I/O variables */
    private short readWriteAddr;
    private byte readAhead;
    private boolean secondByteFlag = false;
    private byte ioByte0, ioByte1;

    private int[] spriteLineCount = new int[VDP_HEIGHT + 16];
    private boolean[][] spritePriorityMatrix = new boolean[VDP_WIDTH + 16][VDP_HEIGHT + 16];
    private boolean[][] spriteCollisionMatrix = new boolean[VDP_WIDTH + 16][VDP_HEIGHT + 16];

    public Sg1000Vdp() {
        init();
    }

    public void reset() {
        Arrays.fill(registers, (byte) 0);
        Arrays.fill(mem, (byte) 0);

        readWriteAddr = 0;
        secondByteFlag = false;
        ioByte0 = 0;
        ioByte1 = 0;
        statusRegister = 0;

        updateMode();
        interruptHandler.setMode(getVideoMode());
    }

    @Override
    public void init() {
        mem = new byte[RAM_SIZE];
        screenData = new int[VDP_WIDTH][VDP_HEIGHT];
        interruptHandler = VdpInterruptHandler.createSg1000Instance();
        reset();
    }

    @Override
    public boolean run(int cycles) {
        boolean vBlank = interruptHandler.isvBlankSet();
        interruptHandler.increaseHCounter();
        boolean vBlankTrigger = !vBlank && interruptHandler.isvBlankSet();
        if (vBlankTrigger) {
            setStatusINT(true);
            drawScreen();
        }
        return interruptHandler.isEndOfFrameCounter();
    }

    @Override
    public int[][] getScreenData() {
        return screenData;
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
    public boolean isDisplayEnabled() {
        return true;
    }

    public VideoMode getVideoMode() {
        return VideoMode.NTSCJ_H32_V24;
    }

    @Override
    public VdpMemoryInterface getVdpMemory() {
        return null; //TODO fix
    }

    public VdpInterruptHandler getInterruptHandler() {
        return interruptHandler;
    }

    private void updateMode() {
        TmsMode mode = null;
        int val = (getM1() ? 1 : 0) << 0 | (getM2() ? 1 : 0) << 1 | (getM3() ? 1 : 0) << 2;
        switch (val) {
            case 0:
                mode = TmsMode.MODE_0;
                break;
            case 1:
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

    public boolean getBit(int reg, int bit) {
        return getBit(registers[reg], bit);
    }

    public void setBit(int reg, int bit, boolean value) {
        registers[reg] = setBit(registers[reg], bit, value);
        if (reg < 2) {
            updateMode();
        }
    }

    public static byte setBit(byte in, int bit, boolean v) {
        return v ? (byte) (in | (1 << bit)) : (byte) (in & ((~1) << bit));
    }

    public static boolean getBit(byte in, int bit) {
        return (in & (1 << bit)) != 0;
    }

    public boolean getEXTVID() {
        return getBit(0, 0);
    }

    public boolean getM2() {
        return getBit(0, 1);
    }

    public boolean getMAG() {
        return getBit(1, 0);
    }

    public boolean getSI() {
        return getBit(1, 1);
    }

    public boolean getM3() {
        return getBit(1, 3);
    }

    public boolean getM1() {
        return getBit(1, 4);
    }

    public boolean getGINT() {
        return getBit(1, 5);
    }

    public void setGINT(boolean b) {
        setBit(1, 5, b);
    }

    public boolean getBL() {
        return getBit(1, 6);
    }

    public boolean get416K() {
        return getBit(1, 7);
    }

    public boolean getPG13() {
        return getBit(4, 2);
    }

    public boolean getCT13() {
        return getBit(3, 7);
    }

    public int getNameTableAddr() {
        return (registers[TILEMAP_NAMETABLE.ordinal()] & 0xF) << 10;
    }

    public int getColorTableAddr() {
        return registers[COLORMAP_NAMETABLE.ordinal()] << 6;
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
        return (registers[BACKGROUND_COLOR.ordinal()] & 0xFF) >> 4;
    }

    public int getOffBitColor() {
        return (registers[BACKGROUND_COLOR.ordinal()] & 0x0F);
    }

    public int getFifthSpriteNr() {
        return (registers[BACKGROUND_COLOR.ordinal()] & 0x0F);
    }

    public byte getSpriteX(int sprite) {
        int attrTable = getSpriteAttrTable();
        return mem[(short) ((attrTable & 0xffff) + (sprite * 4) + 1)];
    }

    public byte getSpriteY(int sprite) {
        int attrTable = getSpriteAttrTable();
        return mem[(short) ((attrTable & 0xffff) + (sprite * 4) + 0)];
    }

    public byte getSpritePattern(int sprite) {
        int attrTable = getSpriteAttrTable();
        return mem[(short) ((attrTable & 0xffff) + (sprite * 4) + 2)];
    }

    public byte getSpriteColour(int sprite) {
        int attrTable = getSpriteAttrTable();
        return mem[(short) ((attrTable & 0xffff) + (sprite * 4) + 3)];
    }

    /**
     * Port 0 read.
     *
     * @return
     */
    public final byte readVRAMData() {
        byte result = readAhead;
        readAhead = mem[readWriteAddr];
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
                    readAhead = mem[readWriteAddr];
                    increaseReadWriteAddr();
                }
            }

            /* If not, we're doing register i/o */
            else {
                int regNum = ((int) ioByte1) & 0x07;
                updateRegister(regNum, ioByte0);
            }

            secondByteFlag = false;
        }

    }

    private void updateRegister(int regNum, byte value) {
        if (regNum < REGISTERS && registers[regNum] != value) {
            LogHelper.printLevel(LOG, Level.INFO, "vdpWriteReg {}, data: {}", regNum, value & 0xFF, verbose);
            registers[regNum] = value;
            if (regNum < 2) {
                updateMode();
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
                byte patternIdx = mem[nameTablePtr];
                int patternAddr = (patternTableBase & 0xffff) + ((patternIdx & 0xff) * 8);
                // For all lines of the character
                for (int charLine = 0; charLine < 8; charLine++) {
                    byte line = mem[(short) ((patternAddr & 0xFFFF) + charLine)];
                    // For all pixels of the line
                    for (int linePos = 0; linePos < 8; linePos++) {
                        // Calculate location of pixel
                        int px = 7 + ((x * 8) - linePos);
                        int py = ((y * 8) + charLine);
                        // Get foreground/background
                        int colorTableAddr = ((colorTableBase & 0xffff) + ((patternIdx & 0xff) / 8));
                        byte color = mem[(short) colorTableAddr & 0xFFFF];
                        Color fg = colors[(color & 0xf0) >> 4];
                        Color bg = colors[(color & 0x0f)];
                        setPixel(px, py, getBit(line, linePos) ? fg : bg);
                    }
                }
                // Update name table pointer
                nameTablePtr = (short) (nameTablePtr + 1);
            }
        }
    }

    private final void setPixel(int px, int py, Color color) {
        screenData[px][py] = color.getRGB() & 0xFF_FFFF; //24 bit RGB
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
                byte patternIdx = mem[nameTablePtr];
                int patternAddr = (patternTableBase & 0xffff) + ((patternIdx & 0xff) * 8);
                // For all lines of the character
                for (int charLine = 0; charLine < 8; charLine++) {
                    byte line = mem[(short) ((patternAddr & 0xFFFF) + charLine)];
                    // For all pixels of the line
                    for (int linePos = 0; linePos < 6; linePos++) {
                        // Calculate location of pixel
                        int px = 5 + ((x * 6) - linePos);
                        int py = ((y * 8) + charLine);
                        // Set pixel
                        setPixel(px + MODE0_OFFSET, py, getBit(line, linePos + 2) ? onBit : offBit);
                    }
                }
                nameTablePtr = (short) (nameTablePtr + 1);
            }
        }
    }

    /**
     * Draw pattern according to mode 1 (screen 2 / graphic 2)
     */
    public void drawMode2() {
        int nameTableBase = getNameTableAddr();
        int nameTableIdx = 0;
        short patternTableBase = getPG13() ? (short) 0x2000 : 0;
        boolean bit0 = this.getBit(4, 0);
        boolean bit1 = this.getBit(4, 1);
        int patternMask = ((bit0 ? 0 : (1 << 7)) | (bit1 ? 0 : (1 << 8)));
        short colorTableBase = getCT13() ? (short) 0x2000 : 0;
        // For all x/y positions
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 32; x++) {
                // Read index of pattern from name table address
                byte patternIdx = mem[(short) ((nameTableBase & 0xffff) + nameTableIdx)];
                int patternAddr = (patternTableBase & 0xffff) + ((patternIdx & 0xff) * 8);
                //patternAddr += (2048 * (nameTableIdx / 256));
                if (bit0 && (nameTableIdx / 256) == 1) patternAddr += 2048;
                if (bit1 && (nameTableIdx / 256) == 2) patternAddr += 4096;
                // For all lines of the character
                for (int charLine = 0; charLine < 8; charLine++) {
                    byte line = mem[(short) ((patternAddr & 0xffff) + charLine)];
                    int colorTableAddr = (colorTableBase & 0xffff) + ((patternIdx & 0xff) * 8);
                    //colorTableAddr += (2048 * (nameTableIdx / 256));
                    if (bit0 && (nameTableIdx / 256) == 1) colorTableAddr += 2048;
                    if (bit1 && (nameTableIdx / 256) == 2) colorTableAddr += 4096;
                    byte lineColor = mem[(short) ((colorTableAddr & 0xFFFF) + charLine)];
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
                nameTableIdx += 1;
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
            byte colour = getSpriteColour(i);

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

    public void drawMode3() {
        System.err.println("drawmode3 not implemented");
        drawMode0();
    }

    public int[][] drawScreen() {
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
        return screenData;
    }
}