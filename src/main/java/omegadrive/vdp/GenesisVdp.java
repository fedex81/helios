package omegadrive.vdp;

import omegadrive.Genesis;
import omegadrive.bus.BusProvider;
import omegadrive.util.*;
import omegadrive.vdp.model.VdpDmaHandler;
import omegadrive.vdp.model.VdpHLineProvider;
import omegadrive.vdp.model.VdpMemoryInterface;
import omegadrive.vdp.model.VdpRenderHandler;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

    private VramMode vramMode;

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
    long firstWrite;
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
    boolean ste;  //shadow-highlight

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
    private VdpInterruptHandler interruptHandler;
    private VdpMemoryInterface memoryInterface;
    private VdpDmaHandler dmaHandler;
    private VdpRenderHandler renderHandler;
    private VideoMode videoMode;
    private RegionDetector.Region region;

    private int line;

    public GenesisVdp(BusProvider bus, VdpMemoryInterface memoryInterface, VdpDmaHandler dmaHandler) {
        this.bus = bus;
        this.memoryInterface = memoryInterface;
        this.dmaHandler = dmaHandler;
        setupVdp();
    }

    public GenesisVdp(BusProvider bus) {
        this.bus = bus;
        this.memoryInterface = new GenesisVdpMemoryInterface();
        this.dmaHandler = VdpDmaHandlerImpl.createInstance(this, memoryInterface, bus);
        setupVdp();
    }

    private void setupVdp() {
        this.interruptHandler = VdpInterruptHandler.createInstance(this);
        this.renderHandler = new VdpRenderHandlerImpl(this, memoryInterface);
    }

    @Override
    public void init() {
        empty = 1;
        vb = 1;

        //from TMSS
        writeRegister(0, 4);
        writeRegister(1, 20);
        writeRegister(2, 48);
        writeRegister(3, 60);
        writeRegister(4, 7);
        writeRegister(5, 108);
        writeRegister(6, 0);
        writeRegister(7, 0);
        writeRegister(8, 0);
        writeRegister(9, 0);
        writeRegister(10, 255);
        writeRegister(11, 0);
        writeRegister(12, 129);
        writeRegister(13, 55);
        writeRegister(14, 0);
        writeRegister(15, 1);
        writeRegister(16, 1);
        writeRegister(17, 0);
        writeRegister(18, 0);
        writeRegister(19, 255);
        writeRegister(20, 255);
        writeRegister(21, 0);
        writeRegister(22, 0);
        writeRegister(23, 128);

        region = bus.getEmulator().getRegion();
        resetMode();
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
            LogHelper.printLevel(LOG, Level.INFO, "readControl: {}", control, verbose);
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
    public boolean isDisplayEnabled() {
        return disp;
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

    public boolean isShadowHighlight() {
        return ste;
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
        LogHelper.printLevel(LOG, Level.INFO, "writeDataPort, data: {}, address: {}", data, addressRegister, verbose);
        memoryInterface.writeVideoRamWord(vramMode, data, addressRegister);
        addressRegister += autoIncrementData;
        setupDmaFillMaybe(data);
//        logInfo("After writeDataPort, data: {}, address: {}", data, addressRegister);
    }

    @Override
    public int readDataPort() {
        this.writePendingControlPort = false;
        int res = memoryInterface.readVideoRamWord(vramMode, addressRegister);
        if (vramMode == VdpProvider.VramMode.vramRead_8bit) {
            //The 8-bit VRAM read function reads a single byte from VRAM.
            // The returned value consists of the VRAM byte as the low byte, plus a byte from the FIFO as the high byte.
            res &= 0xFF;
        }
        LogHelper.printLevel(LOG, Level.INFO, "readDataPort, address {} , result {}, size {}", addressRegister, res,
                Size.WORD, verbose);
        addressRegister += autoIncrementData;
        return res;
    }

    private void writeRamAddress(int data) {
        if (!writePendingControlPort) {
            firstWrite = data;
            writePendingControlPort = true;
//            It is perfectly valid to write the first half of the command word only.
//            In this case, _only_ A13-A00 and CD1-CD0 are updated to reflect the new
//            values, while the remaining address and code bits _retain_ their former value.
            codeRegister = (int) ((codeRegister & 0x3C | firstWrite >> 14) & 0x3F);
            addressRegister = (int) ((addressRegister & 0xC000) | (firstWrite & 0x3FFF));
            vramMode = VramMode.getVramMode(codeRegister & 0xF);
            LogHelper.printLevel(LOG, Level.INFO, "writeAddr-1, firstWord: {}, address: {}, code: {}"
                    , firstWrite, addressRegister, codeRegister, verbose);
        } else {
            writePendingControlPort = false;
            all = ((firstWrite << 16) | data);

            codeRegister = (((data >> 4) & 0xF) << 2 | codeRegister & 0x3);
            //m1 masks CD5
//            codeRegister &= (((m1 ? 1 : 0)<< 5) | 0x1F); //breaks Andre Agassi
            addressRegister = ((data & 0x3) << 14 | (addressRegister & 0x3FFF));

            int addressMode = codeRegister & 0xF;    // CD0-CD3
            vramMode = VramMode.getVramMode(addressMode);
            LOG.debug("Video mode: " + Objects.toString(vramMode));
            LogHelper.printLevel(LOG, Level.INFO, "writeAddr-2: secondWord: {}, address: {}, code: {}, dataLong: {}"
                    , data, addressRegister, codeRegister, all, verbose);
            //	https://wiki.megadrive.org/index.php?title=VDP_DMA
            if ((codeRegister & 0b100000) > 0) { // DMA
                VdpDmaHandler.DmaMode dmaMode = dmaHandler.setupDma(vramMode, all, m1);
                if (dmaMode == VdpDmaHandler.DmaMode.MEM_TO_VRAM) {
                    bus.setStop68k(true);
                }
                LogHelper.printLevel(LOG, Level.INFO, "After DMA setup, writeAddr: {}, data: {}, firstWrite: {}"
                        , addressRegister, all, writePendingControlPort, verbose);
            }
        }
    }

    private void writeRegister(int data) {
        int dataControl = data & 0x00FF;
        int reg = (data >> 8) & 0x1F;
        writeRegister(reg, dataControl);
    }

    private void writeRegister(int reg, int dataControl) {
        if (reg >= VDP_REGISTERS_SIZE) {
            LOG.warn("Ignoring write to invalid VPD register: " + reg);
            return;
        }
        registers[reg] = dataControl;
        updateVariables(reg, dataControl);
        LogHelper.printLevel(LOG, Level.INFO, "writeReg: {}, data: {}", reg, dataControl, verbose);
    }

    int spriteTableLoc = 0;

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
        } else if (reg == 0x05) {
            if (data != spriteTableLoc) {
                LOG.info("Sprite table location changed from: {}, to: {}", spriteTableLoc, data);
            }
            spriteTableLoc = data;
        } else if (reg == 0x0C) {
            boolean rs0 = Util.bitSetTest(data, 7);
            boolean rs1 = Util.bitSetTest(data, 0);
            h40 = rs0 && rs1;
            boolean val = Util.bitSetTest(data, 3);
            if (val != ste) {
                LOG.info("Shadow highlight: " + val);
            }
            ste = val;
        } else if (reg == 0x0F) {
            autoIncrementData = data;
        } else if (reg == 0x0A) {
            updateReg10(data);
        }
    }

    private void updateReg10(long data) {
        if (data != registers[0x0A]) {
            interruptHandler.logVerbose("Update hLinePassed register: %s", (data & 0x00FF));
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
            interruptHandler.logVerbose("Update ie1 register: %s", newIe1);
        }
    }

    private void doDma(boolean isBlanking) {
        if (dma == 1) {
            boolean dmaDone;
            VdpDmaHandler.DmaMode mode = dmaHandler.getDmaMode();
            dmaDone = dmaHandler.doDma(videoMode, isBlanking);
            dma = dmaDone ? 0 : dma;
            if (dma == 0 && dmaDone) {
                LogHelper.printLevel(LOG, Level.INFO, "{}: OFF", mode, verbose);
                bus.setStop68k(false);
            }
        }
    }

    private boolean setupDmaFillMaybe(int data) {
        //this should proceed even with m1 =0
        if (dma == 1) {
            VdpDmaHandler.DmaMode mode = dmaHandler.getDmaMode();
            boolean dmaOk = mode == VdpDmaHandler.DmaMode.VRAM_FILL;
            if (dmaOk) {
                dmaHandler.setupDmaDataPort(data);
                return true;
            }
        }
        return false;
    }

    private void runNew() {
        interruptHandler.increaseHCounter();
        boolean displayEnable = disp;
        //disabling the display implies blanking
        hb = interruptHandler.ishBlankSet() ? 1 : 0;
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
                interruptHandler.logVerbose("Draw Screen");
                line = 0;
                int[][] screenData = renderHandler.renderFrame();
                bus.getEmulator().renderScreen(screenData);
                resetMode();
            }
        }
    }

    int dmaActiveScreenLine = -1;
    int dmaDisableScreenCounter = -1;

    //TODO
    private void runDma() {
        if (dma == 0) {
            return;
        }
        boolean isVBlank = vb == 1;
        boolean isHBlank = hb == 1;
        boolean dmaActiveLine = isHBlank && !isVBlank && line != dmaActiveScreenLine;
        boolean dmaPassiveLine = isVBlank && interruptHandler.getvCounter() != dmaDisableScreenCounter;
        if (dmaActiveLine) {
            doDma(isVBlank);
            dmaActiveScreenLine = line;
        } else if (dmaPassiveLine) {
            doDma(isVBlank);
            dmaDisableScreenCounter = interruptHandler.getvCounter();
        }
    }

    @Override
    public int getHLinesCounter() {
        return registers[0xA];
    }

    private void resetMode() {
        VideoMode newVideoMode = getVideoMode(region, isH40(), isV30());
        if (videoMode != newVideoMode) {
            this.videoMode = newVideoMode;
            LOG.info("Video mode changed: " + videoMode + ", " + videoMode.getDimension());
            interruptHandler.setMode(videoMode);
            renderHandler.setVideoMode(videoMode);
            pal = videoMode.isPal() ? 1 : 0;
        }
    }


    private void drawScanline(boolean displayEnable) {
        //draw line
        interruptHandler.logVeryVerbose("Draw Scanline: %s", line);
        int lineLimit = videoMode.getDimension().height;
        if (line < lineLimit) {
            renderHandler.renderLine(line);
        }
    }


    @Override
    public void dumpScreenData() {
        renderHandler.dumpScreenData();
    }

    @Override
    public void run(int cycles) {
        runNew();
    }

    @Override
    public void setDmaFlag(int value) {
        dma = value;
    }

}