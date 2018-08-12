package omegadrive.vdp;

import omegadrive.Genesis;
import omegadrive.bus.BusProvider;
import omegadrive.util.RegionDetector;
import omegadrive.util.Size;
import omegadrive.util.Util;
import omegadrive.util.VideoMode;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Objects;

/**
 * GenesisVdp
 *
 * @author Federico Berti
 * <p>
 * Based on genefusto GenVdp
 * https://github.com/DarkMoe/genefusto
 * @author DarkMoe
 */
public class GenesisVdp implements VdpProvider, VdpHLineProvider {

    private static Logger LOG = LogManager.getLogger(GenesisVdp.class.getSimpleName());

    public static boolean verbose = false || Genesis.verbose;

    public static int ROWS = VDP_VIDEO_ROWS;
    public static int COLS = VDP_VIDEO_COLS;

    int[] vram = new int[VDP_VRAM_SIZE];
    int[] cram = new int[VDP_CRAM_SIZE];
    int[] vsram = new int[VDP_VSRAM_SIZE];

    VramMode vramMode;

//	VSRAM
//	 The VDP has 40x10 bits of on-chip vertical scroll RAM. It is accessed as
//	 40 16-bit words through the data port. Each word has the following format:
//
//	 ------yyyyyyyyyy
//
//	 y = Vertical scroll factor (0-3FFh)
//
//	 When accessing VSRAM, only address bits 6 through 1 are valid.
//	 The high-order address bits are ignored. Since VSRAM is word-wide, address
//	 bit zero has no effect.
//
//	 Even though there are 40 words of VSRAM, the address register will wrap
//	 when it passes 7Fh. Writes to the addresses beyond 50h are ignored.

    int[] registers = new int[VDP_REGISTERS_SIZE];

    //    This flag is updated when these conditions are met:
//
// - It is set when the first half of the command word is written.
// - It is cleared when the second half of the command word is written.
// - It is cleared when the data port is written to or read from.
// - It is cleared when the control port is read.
    boolean writePendingControlPort = false;
    int firstWrite;

    int dataPort;
    int addressRegister;
    int codeRegister;

    //	Reg 0
    //	Left Column Blank
    boolean lcb;
    //	Enable HINT
    boolean ie1;
    //	HV Counter Latch
    boolean m3;
    //	Display Enable
    boolean de;

    //	REG 1
    //	Extended VRAM
    boolean evram;
    //	Enable Display
    boolean disp;
    //	Enable VINT
    boolean ie0;
    //	Enable DMA
    boolean m1;
    //	Enable V30 Mode
    boolean m2;
    //	Enable Mode 5	(si esta inactivo, es mode = 4, compatibilidad con SMS)
    boolean m5;

    //	REG 0xC
    boolean h40;

    //	REG 0xF
    int autoIncrementData;

    //	Status register:
//	15	14	13	12	11	10	9		8			7	6		5		4	3	2	1	0
//	0	0	1	1	0	1	EMPTY	FULL		VIP	SOVR	SCOL	ODD	VB	HB	DMA	PAL

    //	EMPTY and FULL indicate the status of the FIFO.
//	When EMPTY is set, the FIFO is empty.
//	When FULL is set, the FIFO is full.
//	If the FIFO has items but is not full, both EMPTY and FULL will be clear.
//	The FIFO can hold 4 16-bit words for the VDP to process. If the M68K attempts to write another word once the FIFO has become full, it will be frozen until the first word can be delivered.
    int empty = 1;
    int full = 0;

    //	VIP indicates that a vertical interrupt has occurred, approximately at line $E0. It seems to be cleared at the end of the frame.
    int vip;

    //	SOVR is set when there are too many sprites on the current scanline. The 17th sprite in 32 cell mode and the 21st sprite on one scanline in 40 cell mode will cause this.
    int sovr;
    //	SCOL is set when any sprites have non-transparent pixels overlapping. This is cleared when the Control Port is read.
    int scol;

    //	ODD is set if the VDP is currently showing an odd-numbered frame while Interlaced Mode is enabled.
    int odd;
    //	VB returns the real-time status of the V-Blank signal. It is presumably set on line $E0 and unset at $FF.
    int vb;
    //	HB returns the real-time status of the H-Blank signal.
    int hb;
    //	DMA is set for the duration of a DMA operation. This is only useful for fills and copies, since the M68K is frozen during M68K to VRAM transfers.
    int dma;
    //	PAL seems to be set when the system's display is PAL, and possibly reflects the state of having 240 line display enabled.
    // The same information can be obtained from the version register.
    int pal;

    long all;

    private BusProvider bus;
    private VdpColorMapper colorMapper;
    private VdpInterruptHandler interruptHandler;
    private VdpDmaHandler dmaHandler;
    private VideoMode videoMode;


    public GenesisVdp(BusProvider bus) {
        this.bus = bus;
        this.colorMapper = new VdpColorMapper();
        this.interruptHandler = VdpInterruptHandler.createInstance(this);
        this.dmaHandler = VdpDmaHandler.createInstance(this, bus);
    }

    @Override
    public void init() {
        empty = 1;
        vb = 1;

        for (int i = 0; i < cram.length; i++) {
            if (i % 2 == 0) {
                cram[i] = 0x0E;
            } else {
                cram[i] = 0xEE;
            }
        }
        for (int i = 0; i < vsram.length; i++) {
            if (i % 2 == 0) {
                vsram[i] = 0x07;
            } else {
                vsram[i] = 0xFF;
            }
        }
        Arrays.fill(vram, 0x10);

        registers[23] = 0x80;
        this.videoMode = getVideoMode(bus.getEmulator().getRegion(), false, false);
        this.interruptHandler.setMode(videoMode);
        this.pal = videoMode.isPal() ? 1 : 0;
    }

    private int lastControl = -1;

    @Override
    public int readControl() {
        // The value assigned to these bits will be whatever value these bits were set to from the
        // last read the M68000 performed.
        // Writes from the M68000 don't affect these bits, only reads.
        int control = (
                (empty << 9)
                        | (full << 8)
                        | (vip << 7)
                        | (sovr << 6)
                        | (scol << 5)
                        | (odd << 4)
                        | (vb << 3)
                        | (hb << 2)
                        | (dma << 1)
                        | (pal << 0)
        );
        writePendingControlPort = false;
        if (control != lastControl) {
            lastControl = control;
            logInfo("readControl: {}", control);
        }
        return control;
    }

    //Sunset riders
    private int lastVCounter = 0;
    private int lastHCounter = 0;

    @Override
    public int getVCounter() {
        if (m3) {
            return lastVCounter;
        }
        return interruptHandler.getVCounterExternal();
    }

    @Override
    public int getHCounter() {
        if (m3) {
            return lastHCounter;
        }
        return interruptHandler.getHCounterExternal();
    }

    @Override
    public boolean isIe0() {
        return ie0;
    }

    @Override
    public boolean isIe1() {
        return ie1;
    }

    @Override
    public int getRegisterData(int reg) {
        return registers[reg];
    }

    @Override
    public void updateRegisterData(int reg, int data) {
        registers[reg] = data;
    }

    @Override
    public boolean getVip() {
        return interruptHandler.isvIntPending();
    }

    @Override
    public void setVip(boolean value) {
        interruptHandler.setvIntPending(value);
        vip = value ? 1 : 0;
    }

    @Override
    public boolean getHip() {
        return interruptHandler.isHIntPending();
    }

    @Override
    public void setHip(boolean value) {
        interruptHandler.setHIntPending(false);
    }

    @Override
    public VideoMode getVideoMode() {
        return videoMode;
    }

    private VideoMode getVideoMode(RegionDetector.Region region, boolean isH40, boolean isV30) {
        return VideoMode.getVideoMode(region, isH40, isV30, videoMode);
    }

    private boolean isV30() {
        return m2;
    }

    private boolean isH40() {
        return h40;
    }

    private int maxSpritesPerFrame(boolean isH40) {
        return isH40 ? MAX_SPRITES_PER_FRAME_H40 : MAX_SPRITES_PER_FRAME_H32;
    }

    private int maxSpritesPerLine(boolean isH40) {
        return isH40 ? MAX_SPRITES_PER_LINE_H40 : MAX_SPRITES_PER_LiNE_H32;
    }

    private int getVerticalLines(boolean isV30) {
        return isV30 ? VERTICAL_LINES_V30 : VERTICAL_LINES_V28;
    }


    //	https://wiki.megadrive.org/index.php?title=VDP_Ports#Write_2_-_Setting_RAM_address
//	First word
//	Bit	15	14	13	12	11	10	9	8	7	6	5	4	3	2	1	0
//	Def	CD1-CD0	A13		-										   A0

//	Second word
//	Bit	15	14	13	12	11	10	9	8	7	6	5	4	3	2	1	0
//	Def	0	 0	 0	 0	 0	 0	0	0	CD5		- CD2	0	A15	 -A14

//	Access mode	CD5	CD4	CD3	CD2	CD1	CD0
//	VRAM Write	0	0	0	0	0	1
//	CRAM Write	0	0	0	0	1	1
//	VSRAM Write	0	0	0	1	0	1
//	VRAM Read	0	0	0	0	0	0
//	CRAM Read	0	0	1	0	0	0
//	VSRAM Read	0	0	0	1	0	0
    @Override
    public void writeControlPort(long dataL) {
        long mode = (dataL >> 14);
        int data = (int) dataL;

        if (mode == 0b10) {        //	Write 1 - Setting Register
            writeRegister(data);
            //Writing to a VDP register will clear the code register.
            codeRegister = 0;

        } else { // Write 2 - Setting RAM address
            writeRamAddress(data);
        }
    }

    @Override
    public void writeDataPort(long dataL) {
        int data = (int) dataL;
        writePendingControlPort = false;
        logInfo("writeDataPort, data: {}, address: {}", data, addressRegister);
        if (setupDmaFillMaybe(data)) {
            return;
        }
        writeVideoRamWord(vramMode, data, addressRegister);
        addressRegister += autoIncrementData;
//        logInfo("After writeDataPort, data: {}, address: {}", data, addressRegister);
    }

    @Override
    public int readDataPort() {
        this.writePendingControlPort = false;
        int res = readVideoRam(vramMode);
        logInfo("readDataPort, address {} , size {}, result {}", addressRegister, Size.WORD, res);
        addressRegister += autoIncrementData;
        return res;
    }

    private void writeRamAddress(int data) {
        if (!writePendingControlPort) {
            firstWrite = data;
            writePendingControlPort = true;
            logInfo("writeAddr, data: {}, firstWrite: {}", firstWrite, writePendingControlPort);
//            It is perfectly valid to write the first half of the command word only.
//            In this case, _only_ A13-A00 and CD1-CD0 are updated to reflect the new
//            values, while the remaining address and code bits _retain_ their former value.
            codeRegister = (int) (codeRegister << 2 | firstWrite >> 14) & 0x3F;
            addressRegister = (int) (addressRegister << 14 | (firstWrite & 0x3FFF));
        } else {
            writePendingControlPort = false;
            all = (firstWrite << 16) | data;

            codeRegister = (((data >> 4) & 0xF) << 2 | codeRegister & 0x3);
            //m1 masks CD5
//            codeRegister &= (((m1 ? 1 : 0)<< 5) | 0x1F); //breaks Andre Agassi
            addressRegister = ((data & 0x3) << 14 | (addressRegister & 0x3FFF));

            int addressMode = codeRegister & 0xF;    // CD0-CD3
            vramMode = VramMode.getVramMode(addressMode);
            LOG.debug("Video mode: " + Objects.toString(vramMode));
            logInfo("writeAddr: {}, data: {}, firstWrite: {}", addressRegister, all, writePendingControlPort);
            //	https://wiki.megadrive.org/index.php?title=VDP_DMA
            if ((codeRegister & 0b100000) > 0) { // DMA
                VdpDmaHandler.DmaMode dmaMode = dmaHandler.setupDma(vramMode, all, m1);
                if (dmaMode == VdpDmaHandler.DmaMode.MEM_TO_VRAM) {
                    bus.setStop68k(true);
                }
                logInfo("After DMA setup, writeAddr: {}, data: {}, firstWrite: {}", addressRegister, all, writePendingControlPort);
            }
        }
    }

    private void writeRegister(int data) {
        int dataControl = data & 0x00FF;
        int reg = (data >> 8) & 0x1F;

        if (reg >= VDP_REGISTERS_SIZE) {
            LOG.warn("Ignoring write to invalid VPD register: " + reg);
            return;
        }
        registers[reg] = dataControl;
        updateVariables(reg, dataControl);
        logInfo("writeReg: {}, data: {}", reg, dataControl);
    }

    private void updateVariables(int reg, int data) {
        if (reg == 0x00) {
            lcb = ((data >> 5) & 1) == 1;
            de = ((data >> 0) & 1) == 1;
            boolean newM3 = ((data >> 1) & 1) == 1;
            updateM3(newM3);
            boolean newIe1 = ((data >> 4) & 1) == 1;
            updateIe1(newIe1);
        } else if (reg == 0x01) {
            evram = ((data >> 7) & 1) == 1;
            disp = ((data >> 6) & 1) == 1;
            ie0 = ((data >> 5) & 1) == 1;
            m1 = ((data >> 4) & 1) == 1;
            m2 = ((data >> 3) & 1) == 1;
            m5 = ((data >> 2) & 1) == 1;
        } else if (reg == 0x0C) {
            boolean rs0 = Util.bitSetTest(data, 7);
            boolean rs1 = Util.bitSetTest(data, 0);
            h40 = rs0 && rs1;
        } else if (reg == 0x0F) {
            autoIncrementData = data;
        } else if (reg == 0x0A) {
            updateReg10(data);
        }
    }

    private void updateReg10(long data) {
        if (data != registers[0x0A]) {
            interruptHandler.printState("Update hLinePassed register: %s", (data & 0x00FF));
        }
    }

    private void updateM3(boolean newM3) {
        if (newM3 && !m3) {
            lastVCounter = getVCounter();
            lastHCounter = getHCounter();
        }
        m3 = newM3;
    }

    private void updateIe1(boolean newIe1) {
        if (ie1 != newIe1) {
            ie1 = newIe1;
            interruptHandler.printState("Update ie1 register: %s", newIe1);
        }
    }

    private void doDma(int byteSlots) {
        if (dma == 1) {
            boolean dmaDone;
            VdpDmaHandler.DmaMode mode = dmaHandler.getDmaMode();
            dmaDone = dmaHandler.doDma(byteSlots);
            dma = dmaDone ? 0 : dma;
            if (dma == 0 && dmaDone) {
                Util.printLevelIfVerbose(LOG, Level.INFO, "{}: OFF", mode);
                bus.setStop68k(false);
            }
        }
    }

    private boolean setupDmaFillMaybe(int data) {
        if (m1) {
            VdpDmaHandler.DmaMode mode = dmaHandler.getDmaMode();
            boolean dmaOk = mode == VdpDmaHandler.DmaMode.VRAM_FILL;
            if (dmaOk) {
                dmaHandler.setupDmaDataPort(data);
                return true;
            }
        }
        return false;
    }

    private void logInfo(String str, Object... args) {
        if (verbose) {
            String dmaStr = ", DMA " + dma + ", dmaMode: " + dmaHandler.getDmaMode() + ", vramMode: " + Objects.toString(vramMode);
            Util.printLevel(LOG, Level.INFO, str + dmaStr, args);
        }
    }

    public int[][] screenData = new int[COLS][ROWS];

    public int[][] planeA = new int[COLS][ROWS];
    public int[][] planeB = new int[COLS][ROWS];
    public int[][] planeBack = new int[COLS][ROWS];

    public boolean[][] planePrioA = new boolean[COLS][ROWS];
    public boolean[][] planePrioB = new boolean[COLS][ROWS];

    public int[][] planeIndexColorA = new int[COLS][ROWS];
    public int[][] planeIndexColorB = new int[COLS][ROWS];

    public int[][] sprites = new int[COLS][ROWS];
    public int[][] spritesIndex = new int[COLS][ROWS];
    public boolean[][] spritesPrio = new boolean[COLS][ROWS];

    public int[][] window = new int[COLS][ROWS];
    public int[][] windowIndex = new int[COLS][ROWS];
    public boolean[][] windowPrio = new boolean[COLS][ROWS];

    int line;

    private void runNew() {
        interruptHandler.increaseHCounter();
        boolean displayEnable = disp;
        //disabling the display implies blanking
        hb = !displayEnable || interruptHandler.ishBlankSet() ? 1 : 0;
        vb = !displayEnable || interruptHandler.isvBlankSet() ? 1 : 0;
        vip = interruptHandler.isvIntPending() ? 1 : vip;
        runDma();

        //draw on the last counter (use 9bit internal counter value)
        if (interruptHandler.isLastHCounter()) {
            //draw the line
            drawScanline(displayEnable);
            line++;
            //draw the frame
            if (interruptHandler.isDrawFrameCounter()) {
                interruptHandler.printState("Draw Screen");
                spritesFrame = 0;
                line = 0;
                evaluateSprites();
                compaginateImage();
                bus.getEmulator().renderScreen(screenData);
                resetMode();
                LOG.debug("DmaSlots: {}", dmaSlots);
                dmaSlots = 0;
            }
        }
    }

    int dmaActiveScreenLine = -1;
    int dmaDisableScreenCounter = -1;
    int dmaSlots = 0;
    int DMA_SLOTS_ACTIVE_SCREEN_LINE = 13;
    int DMA_SLOTS_DISABLED_SCREEN_LINE = 200;

    //TODO
    private void runDma() {
        if (hb == 1 && line != dmaActiveScreenLine) {
            doDma(DMA_SLOTS_ACTIVE_SCREEN_LINE);
            dmaActiveScreenLine = line;
            dmaSlots += DMA_SLOTS_ACTIVE_SCREEN_LINE;
        }
        if (vb == 1 && interruptHandler.getvCounter() != dmaDisableScreenCounter) {
            doDma(DMA_SLOTS_DISABLED_SCREEN_LINE);
            dmaDisableScreenCounter = interruptHandler.getvCounter();
            dmaSlots += DMA_SLOTS_DISABLED_SCREEN_LINE;
        }
    }

    @Override
    public int getHLinesCounter() {
        return registers[0xA];
    }

    private void resetMode() {
        VideoMode newVideoMode = getVideoMode(videoMode.getRegion(), isH40(), isV30());
        if (videoMode != newVideoMode) {
            this.videoMode = newVideoMode;
            LOG.info("Video mode changed: " + videoMode + ", " + videoMode.getDimension());
            interruptHandler.setMode(videoMode);
            pal = videoMode.isPal() ? 1 : 0;
        }
    }


    private void drawScanline(boolean displayEnable) {
        //draw line
        interruptHandler.printState("Draw Scanline: %s", line);
        int lineLimit = videoMode.getDimension().height;
        if (line < lineLimit) {
            if (displayEnable) {
                spritesLine = 0;
                renderBack();
                renderPlaneA();
                renderPlaneB();
                renderWindow();
                renderSprites();
            }
        }
    }

    @Override
    public void run(int cycles) {
        runNew();
    }

    @Override
    public int readVideoRamWord(VramMode mode, int address) {
        int data = 0;
        //ignore A0, always use an even address
        address &= ~1;
        if (mode == VramMode.vramRead) {
            data = readVramWord(address);
        } else if (mode == VramMode.vsramRead) {
            data = readVsramWord(address);
        } else if (mode == VramMode.cramRead) {
            data = readCramWord(address);
        } else {
            LOG.warn("Unexpected videoRam read: " + mode);
        }
        return data;
    }

    public int readVideoRam(VramMode mode) {
        return readVideoRamWord(mode, addressRegister);
    }

    private int readCramByte(int address) {
        address &= (VDP_CRAM_SIZE - 1);
        return cram[address];
    }

    private int readCramWord(int address) {
        return readCramByte(address) << 8 | readCramByte(address + 1);
    }

    private int readVsramByte(int address) {
        address &= 0x7F;
        if (address >= VDP_VSRAM_SIZE) {
            address = 0;
        }
        return vsram[address];
    }

    private int readVsramWord(int address) {
        return readVsramByte(address) << 8 | readVsramByte(address + 1);
    }

    @Override
    public int readVramByte(int address) {
        address &= (VDP_VRAM_SIZE - 1);
        return vram[address];
    }

    @Override
    public void setDmaFlag(int value) {
        dma = value;
    }

    private int readVramWord(int address) {
        return readVramByte(address) << 8 | readVramByte(address + 1);
    }

    //    The address register wraps past address 7Fh.
    private void writeCramByte(int address, int data) {
        address &= (VDP_CRAM_SIZE - 1);
        cram[address] = data & 0xFF;
    }

    //    The address register wraps past address FFFFh.
    @Override
    public void writeVramByte(int address, int data) {
        address &= (VDP_VRAM_SIZE - 1);
        vram[address] = data & 0xFF;
    }

    //    Even though there are 40 words of VSRAM, the address register will wrap
//    when it passes 7Fh. Writes to the addresses beyond 50h are ignored.
    private void writeVsramByte(int address, int data) {
        address &= 0x7F;
        if (address < VDP_VSRAM_SIZE) {
            vsram[address] = data & 0xFF;
        } else {
            //Arrow Flash
            LOG.debug("Ignoring vsram write to address: {}", Integer.toHexString(address));
        }
    }

    @Override
    public void writeVideoRamWord(VramMode vramMode, int data, int address) {
        int word = data;
        int data1 = (word >> 8);
        int data2 = word & 0xFF;
        //ignore A0
        int index = address & ~1;
        if (vramMode == VramMode.vsramWrite) {
            writeVsramByte(index, data1);
            writeVsramByte(index + 1, data2);
        } else if (vramMode == VramMode.cramWrite) {
            writeCramByte(index, data1);
            writeCramByte(index + 1, data2);
        } else if (vramMode == VramMode.vramWrite) {
            boolean byteSwap = (address & 1) == 1;
            writeVramByte(index, byteSwap ? data2 : data1);
            writeVramByte(index + 1, byteSwap ? data1 : data2);
        } else {
            LOG.warn("Unexpected videoRam write: " + vramMode);
        }
    }

    int spritesFrame = 0;
    int spritesLine = 0;


    static int INDEXES_NUM = ROWS;
    int[] lastIndexes = new int[INDEXES_NUM];
    int[][] spritesPerLine = new int[INDEXES_NUM][MAX_SPRITES_PER_FRAME_H40];

    private void evaluateSprites() {
        //	AT16 is only valid if 128 KB mode is enabled,
        // and allows for rebasing the Sprite Attribute Table to the second 64 KB of VRAM.
        int spriteTableLoc = registers[0x5] & 0x7F;
        int spriteTable = spriteTableLoc * 0x200;

        //reset
        int currSprite = 0;
        for (int i = 0; i < ROWS; i++) {
            lastIndexes[i] = 0;
            Arrays.fill(spritesPerLine[i], -1);
        }

        boolean isH40 = isH40();
        int maxSprites = maxSpritesPerFrame(isH40);

        for (int i = 0; i < maxSprites; i++) {
            int baseAddress = spriteTable + (i * 8);

            int byte0 = readVramByte(baseAddress);
            int byte1 = readVramByte(baseAddress + 1);
            int byte2 = readVramByte(baseAddress + 2);
            int byte3 = readVramByte(baseAddress + 3);
            int byte4 = readVramByte(baseAddress + 4);
            int byte5 = readVramByte(baseAddress + 5);
            int byte6 = readVramByte(baseAddress + 6);
            int byte7 = readVramByte(baseAddress + 7);

            int linkData = byte3 & 0x7F;

            int verticalPos = ((byte0 & 0x1) << 8) | byte1;
            int verSize = byte2 & 0x3;

            int verSizePixels = (verSize + 1) * 8;
            int realY = (int) (verticalPos - 128);
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

    int[] priors = new int[COLS];

    private void renderSprites() {
        int spriteTableLoc = registers[0x5] & 0x7F;    //	AT16 is only valid if 128 KB mode is enabled, and allows for rebasing the Sprite Attribute Table to the second 64 KB of VRAM.
        int spriteTable = spriteTableLoc * 0x200;

        long linkData = 0xFF;
        long verticalPos;

        int line = this.line;

        int baseAddress = spriteTable;
        int[] spritesInLine = spritesPerLine[line];
        int ind = 0;
        int currSprite = spritesInLine[0];


        boolean isH40 = isH40();
        int maxSpritesPerLine = maxSpritesPerLine(isH40);
        int maxSpritesPerFrame = maxSpritesPerFrame(isH40);
        Arrays.fill(priors, 0);
        //Stop processing sprites, skip the remaining lines
//        if (spritesFrame > maxSpritesPerFrame) { //TODO breaks AyrtonSenna
//            return;
//        }

        while (currSprite != -1) {
            baseAddress = spriteTable + (currSprite * 8);

            int byte0 = readVramByte(baseAddress);
            int byte1 = readVramByte(baseAddress + 1);
            int byte2 = readVramByte(baseAddress + 2);
            int byte3 = readVramByte(baseAddress + 3);
            int byte4 = readVramByte(baseAddress + 4);
            int byte5 = readVramByte(baseAddress + 5);
            int byte6 = readVramByte(baseAddress + 6);
            int byte7 = readVramByte(baseAddress + 7);

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
                    int data = readVramByte(grab);
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
    private void compaginateImage() {
        int limitHorTiles = getHorizontalTiles();

        //	TODO 256 en modo pal
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
    }

    private int getHorizontalTiles(boolean isH40) {
        return isH40 ? 40 : 32;
    }

    private int getHorizontalTiles() {
        return getHorizontalTiles(isH40());
    }

    private int getHorizontalPixelSize() {
        int reg10 = registers[0x10];
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

    private void renderBack() {
        int line = this.line;
        int limitHorTiles = getHorizontalTiles();

        int backLine = (registers[7] >> 4) & 0x3;
        int backEntry = (registers[7]) & 0xF;
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
    private void renderPlaneA() {
        renderPlane(true);
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
    private void renderPlaneB() {
        renderPlane(false);
    }

    private void renderPlane(boolean isPlaneA) {
        int nameTableLocation = isPlaneA ? registers[2] & 0x38 :
                (registers[4] & 0x7) << 3;    // TODO bit 3 for 128KB VRAM
        nameTableLocation *= 0x400;

        int tileLocator = nameTableLocation;
        int scrollMap = 0;

        int reg10 = registers[0x10];
        int horScrollSize = reg10 & 3;
        int verScrollSize = (reg10 >> 4) & 3;

        int horPixelsSize = getHorizontalPixelSize();
        int limitHorTiles = getHorizontalTiles();

        int line = this.line;

        int regD = registers[0xD];
        int hScrollBase = regD & 0x3F;    //	bit 6 = mode 128k
        hScrollBase *= 0x400;

        int regB = registers[0xB];
        int HS = regB & 0x3;
        int VS = (regB >> 2) & 0x1;

        //vertical scrolling
        if (VS == 0) {
            int[] res = fullScreenVerticalScrolling(verScrollSize, horScrollSize, tileLocator, isPlaneA);
            tileLocator = res[0];
            scrollMap = res[1];
        }

        //horizontal scrolling
        long scrollDataHor = horizontalScrolling(HS, hScrollBase, horScrollSize, isPlaneA);

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
                int[] res = verticalScrolling(scrollLine, verScrollSize, horScrollSize, vertOffset, isPlaneA);
                vertOffset = res[0];
                scrollMap = res[1];
            }


            loc = tileLocator + (loc * 2);
            loc += vertOffset;
            int nameTable = readVramWord(loc);

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
                int data = readVramByte(grab);

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
    private void renderWindow() {
        int reg11 = registers[0x11];
        int reg12 = registers[0x12];

        int windowVert = reg12 & 0x1F;
        boolean down = ((reg12 & 0x80) == 0x80) ? true : false;

        int windowHorizontal = reg11 & 0x1F;
        boolean right = ((reg11 & 0x80) == 0x80) ? true : false;

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

        boolean isH40 = isH40();
        int limitHorTiles = getHorizontalTiles(isH40);
        boolean drawWindow = legalVertical || (!legalVertical && legalHorizontal);

        if (drawWindow) {
            //Ayrton Senna -> vertical
            //Bad Omen -> horizontal
            int tileStart = legalHorizontal ? (right ? windowHorizontal * 2 : 0) : 0;
            int tileEnd = legalHorizontal ? (right ? limitHorTiles : windowHorizontal * 2) : limitHorTiles;
            drawWindowPlane(tileStart, tileEnd, isH40);
        }
    }

    private void drawWindowPlane(int tileStart, int tileEnd, boolean isH40) {
        int line = this.line;
        int vertTile = (line / 8);
        int nameTableLocation;
        int tileLocator;
        if (isH40) {
            nameTableLocation = registers[0x3] & 0x3C;    //	WD11 is ignored if the display resolution is 320px wide (H40), which limits the Window nametable address to multiples of $1000.
            nameTableLocation *= 0x400;
            tileLocator = nameTableLocation + (128 * vertTile);
        } else {
            nameTableLocation = registers[0x3] & 0x3E;    //	bit 6 = 128k mode
            nameTableLocation *= 0x400;
            tileLocator = nameTableLocation + (64 * vertTile);
        }


        for (int horTile = tileStart; horTile < tileEnd; horTile++) {
            int loc = tileLocator;

            int nameTable = readVramWord(loc);
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
                    int data = readVramByte(grab);

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
        int color1 = readCramWord(colorIndex);

        int r = (color1 >> 1) & 0x7;
        int g = (color1 >> 5) & 0x7;
        int b = (color1 >> 9) & 0x7;

        return getColor(r, g, b);
    }

    private long horizontalScrolling(int HS, int hScrollBase, int horScrollSize, boolean isPlaneA) {
        long scrollDataHor = 0;
        long scrollTile = 0;
        int vramOffset = 0;
        if (HS == 0b00) {    //	entire screen is scrolled at once by one longword in the horizontal scroll table
            vramOffset = isPlaneA ? hScrollBase : hScrollBase + 2;
            scrollDataHor = readVramWord(vramOffset);
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
            scrollDataHor = readVramWord(vramOffset);

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
            scrollDataHor = readVramWord(vramOffset);

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

    int[] verticalScrollRes = new int[2];

    private int[] verticalScrolling(int scrollLine, int verScrollSize, int horScrollSize,
                                    int tileLocator, boolean isPlaneA) {
        int scrollMap = 0;
        int vsramOffset = isPlaneA ? scrollLine : scrollLine + 2;
        int scrollDataVer = readVsramWord(vsramOffset);

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

    private int[] fullScreenVerticalScrolling(int verScrollSize, int horScrollSize,
                                              int tileLocator, boolean isPlaneA) {
        //when fullScreen vsram offset is 0 for planeA and 2 for planeB
        return verticalScrolling(0, verScrollSize, horScrollSize, tileLocator, isPlaneA);
    }

    private int getColor(int red, int green, int blue) {
        return colorMapper.getColor(red, green, blue);
    }
}