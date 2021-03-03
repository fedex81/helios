/*
 * GenesisVdp
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 25/10/19 14:10
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

package omegadrive.vdp.md;

import omegadrive.bus.model.GenesisBusProvider;
import omegadrive.util.*;
import omegadrive.vdp.model.*;
import omegadrive.vdp.util.UpdatableViewer;
import omegadrive.vdp.util.VdpDebugView;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static omegadrive.vdp.model.GenesisVdpProvider.VdpRegisterName.getRegisterName;

/**
 * Initially Based on genefusto GenVdp
 * https://github.com/DarkMoe/genefusto
 * <p>
 * TODO: fifoDelay, fix read prefetch
 *
 * @author Federico Berti
 */
public class GenesisVdp implements GenesisVdpProvider {

    public final static boolean verbose = false;
    public final static boolean regVerbose = false;
    private final static Logger LOG = LogManager.getLogger(GenesisVdp.class.getSimpleName());

    //TODO true breaks a good number of VdpFifoTests
    private static boolean ENABLE_READ_AHEAD = Boolean.parseBoolean(System.getProperty("vdp.enable.read.ahead", "false"));

    private VramMode vramMode;
    private InterlaceMode interlaceMode;

    int[] registers = new int[VDP_REGISTERS_SIZE];

    IVdpFifo.VdpFifoEntry pendingReadEntry = new IVdpFifo.VdpFifoEntry();

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

    //	REG 1
    //	Extended VRAM, 128Kb
    boolean exVram;
    // 1- Display Enable, 0 - Only Display Background color
    //Bit 6 will blank the display when cleared. Any line that is blanked is
    //filled with the backdrop color. During this time, you can freely access
    //VDP memory with no limitations on the number of writes per line.
    boolean displayEnable;
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
    int satStart;
    int fifoEmpty = 1, fifoFull = 0;

    private GenesisBusProvider bus;
    protected VdpInterruptHandler interruptHandler;
    private VdpMemoryInterface memoryInterface;
    private VdpDmaHandler dmaHandler;
    private VdpRenderHandler renderHandler;
    private IVdpFifo fifo;
    private VideoMode videoMode;
    private RegionDetector.Region region;
    private List<VdpEventListener> list;
    private UpdatableViewer debugViewer;

    public static GenesisVdp createInstance(GenesisBusProvider bus, VdpMemoryInterface memoryInterface,
                                            VdpDmaHandler dmaHandler, RegionDetector.Region region) {
        GenesisVdp v = new GenesisVdp();
        v.bus = bus;
        v.memoryInterface = memoryInterface;
        v.dmaHandler = dmaHandler;
        v.region = region;
        v.setupVdp();
        return v;
    }

    public static GenesisVdp createInstance(GenesisBusProvider bus, VdpMemoryInterface memoryInterface) {
        GenesisVdp v = new GenesisVdp();
        v.bus = bus;
        v.memoryInterface = memoryInterface;
        v.dmaHandler = VdpDmaHandlerImpl.createInstance(v, v.memoryInterface, bus);
        v.region = RegionDetector.Region.EUROPE;
        v.setupVdp();
        return v;
    }

    public static GenesisVdp createInstance(GenesisBusProvider bus) {
        return createInstance(bus, GenesisVdpMemoryInterface.createInstance());
    }

    private GenesisVdp() {
    }

    private void setupVdp() {
        this.list = new ArrayList<>();
        this.interruptHandler = VdpInterruptHandler.createMdInstance(this);
        this.renderHandler = VdpRenderHandlerImpl.createInstance(this, memoryInterface);
        this.debugViewer = VdpDebugView.createInstance(this, memoryInterface, renderHandler);
        this.fifo = new VdpFifo();
        this.initMode();
    }

    @Override
    public void init() {
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
        //registers 11-23 are set to 0 on power-up, see GenTechBulletins
        IntStream.range(11, VDP_REGISTERS_SIZE).forEach(i -> writeRegister(i, 0));
        initMode();
    }

    private void initMode() {
        vramMode = VramMode.getVramMode(codeRegister & 0xF);
        resetVideoMode(true);
        reloadRegisters();
    }

    private void reloadRegisters() {
        IntStream.range(0, GenesisVdpProvider.VDP_REGISTERS_SIZE).forEach(i -> updateVariables(i, registers[i]));
    }

    private int lastControl = -1;

    private int readControl() {
        if (!bus.is68kRunning()) {
            LOG.warn("readControl with 68k stopped, address: {}", addressRegister);
        }
        // The value assigned to these bits will be whatever value these bits were set to from the
        // last read the M68000 performed.
        // Writes from the M68000 don't affect these bits, only reads.
        int control = (
                (fifoEmpty << 9) //fifo empty
                        | (fifoFull << 8) //fifo full
                        | (vip << 7)
//                        | (sovr << 6)
//                        | (scol << 5)
//                        | (odd << 4)
                        | (vb << 3)
                        | (hb << 2)
                        | (dma << 1)
                        | (pal << 0)
        );
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
    public int getAddressRegister() {
        return addressRegister;
    }

    @Override
    public void setAddressRegister(int value) {
        addressRegister = value;
    }

    @Override
    public void updateRegisterData(int reg, int data) {
        writeRegister(reg, data);
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
        return displayEnable;
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

    @Override
    public int readVdpPortWord(VdpPortType type) {
        writePendingControlPort = false;
        switch (type) {
            case DATA:
                return readDataPort();
            case CONTROL:
                return readControl();
        }
        LOG.error("Unexpected portType: {}", type);
        return 0;
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
    public void writeVdpPortWord(VdpPortType type, int data) {
        switch (type) {
            case DATA:
                writeDataPortInternal(data);
                break;
            case CONTROL:
                writeControlPortInternal(data);
                break;
        }
        evaluateVdpBusyState();
    }

    private void writeControlPortInternal(long dataL) {
        long mode = (dataL >> 14);
        int data = (int) dataL;
        //genvdp.txt writePendingControlPort has precedence,
        boolean isRegisterWrite = !writePendingControlPort && mode == 0b10;
        updateStateFromControlPortWrite(isRegisterWrite, data);
        if (isRegisterWrite) {
            writePendingControlPort = false;
            writeRegister(data);
        } else {
            writeControlReg(data);
        }
    }

    //check Sonic3d intro wrong colors, GoldenAxeII, MW4 intro, EA intro, CLUE and VdpFifoTesting
    //http://gendev.spritesmind.net/forum/viewtopic.php?f=15&t=627&p=10854&hilit=golden+axe#p10854
    private void updateStateFromControlPortWrite(boolean isRegisterWrite, int data) {
        if (writePendingControlPort) {
            codeRegister = ((data >> 2) & 0xFF) | (codeRegister & 0x3);
            addressRegister = (addressRegister & 0x3FFF) | ((data & 0x3) << 14);
        } else {
            codeRegister = (codeRegister & 0x3C) | ((data >> 14) & 3);
            addressRegister = (addressRegister & 0xC000) | (data & 0x3FFF);
            //TODO mona.md needs, but breaks EA intro:
//            addressRegister = data & 0x3FFF;

        }
        boolean verbose = !isRegisterWrite && writePendingControlPort;
        vramMode = VramMode.getVramMode(codeRegister & 0xF, verbose);
    }

    protected void writeDataPortInternal(int data) {
        writePendingControlPort = false;
        if (vramMode == null) {
            LogHelper.printLevel(LOG, Level.WARN, "Invalid writeDataPort, vramMode {}, data: {}, address: {}",
                    vramMode, data, addressRegister, true);
        }
        fifoPush(addressRegister, data);
        addressRegister += autoIncrementData;
        dmaHandler.setupDmaFillMaybe(dma == 1, data);
    }

    @Override
    public void fifoPush(int addressRegister, int data) {
        int a = addressRegister;
        if (exVram) {
            a = (((a & 2) >> 1) ^ 1) | ((a & 0x400) >> 9) | a & 0x3FC | ((a & 0x1F800) >> 1);
        }

        fifo.push(vramMode, a, data);
        updateFifoState(fifo);
    }

    private void fifoPop() {
        fifo.pop();
        updateFifoState(fifo);
    }

    private void writeDataToVram(boolean vramSlot) {
        if (!vramSlot || fifo.isEmpty()) {
            return;
        }
        boolean wasFull = fifo.isFull();
        boolean doWrite = true;
        VdpFifo.VdpFifoEntry entry = fifo.peek();
        boolean invalidEntry = entry.vdpRamMode == null || !entry.vdpRamMode.isWriteMode();

        if (invalidEntry) {
            LOG.printf(Level.WARN, "FIFO write on invalid target: %s, data: %x, address: %x",
                    entry.vdpRamMode, entry.data, entry.addressRegister);
            fifoPop();
            doWrite = false;
            evaluateVdpBusyState();
        }
        boolean byteWide = entry.vdpRamMode == VramMode.vramWrite;
        if (byteWide && !entry.firstByteWritten) {
            entry.firstByteWritten = true;
            doWrite = false;
            LogHelper.printLevel(LOG, Level.INFO, "writeVram first byte: {}, data: {}, address: {}",
                    entry.vdpRamMode, entry.data, entry.addressRegister, verbose);
        }
        if (doWrite) {
            fifoPop();
            LogHelper.printLevel(LOG, Level.INFO, "writeVram: {}, data: {}, address: {}",
                    entry.vdpRamMode, entry.data, entry.addressRegister, verbose);
            if (exVram && entry.vdpRamMode == VramMode.vramWrite) {
                memoryInterface.writeVramByte(entry.addressRegister, entry.data & 0xFF);
            } else {
                memoryInterface.writeVideoRamWord(entry.vdpRamMode, entry.data, entry.addressRegister);
            }

            if (wasFull && !fifo.isFull()) {
                evaluateVdpBusyState();
            }
        }
//        logInfo("After writeDataPort, data: {}, address: {}", data, addressRegister);
    }

    private int readDataPort() {
        if (!bus.is68kRunning()) {
            LOG.warn("readDataPort with 68k stopped, address: {}", addressRegister);
        }
        if (fifo.isFull()) {
            LOG.warn("readDataPort with FIFO full, address: {}", addressRegister);
        }
        if (!fifo.isEmpty()) {
            //Bonkers, Subterrania
            LOG.debug("readDataPort with FIFO not empty {}, address: {}", vramMode, addressRegister);
        }
        //TODO need to stop 68k until the result is available
        int value = readDataPortInternal();
        if(ENABLE_READ_AHEAD) {
            int readAhead = pendingReadEntry.data;
            pendingReadEntry.data = value;
            pendingReadEntry.vdpRamMode = vramMode;
            value = readAhead;
        }
        return value;
    }

    private int readDataPortInternal() {
        int res = memoryInterface.readVideoRamWord(vramMode, addressRegister);
        if (vramMode == null) {
            return res;
        }
        int fifoData = fifo.peek().data;
        switch (vramMode) {
            case vramRead_8bit:
                res = memoryInterface.readVramByte(addressRegister ^ 1);
                res = (fifoData & 0xFF00) | (res & 0xFF);
                break;
            case cramRead:
                res = (fifoData & 0xF111) | (res & 0xEEE);
                break;
            case vsramRead:
                res = (fifoData & 0xF800) | (res & 0x7FF);
                break;
            case vramRead:
                break; //do nothing
            default:
                LOG.error("Unexpected vramMode: {}", vramMode);
                break;
        }
        LogHelper.printLevel(LOG, Level.INFO, "readDataPort, address {} , result {}, size {}", addressRegister, res,
                Size.WORD, verbose);
        addressRegister += autoIncrementData;
        return res;
    }

    private void writeControlReg(int data) {
        if (!writePendingControlPort) {
            firstWrite = data;
            writePendingControlPort = true;
            LogHelper.printLevel(LOG, Level.INFO, "writeAddr-1, firstWord: {}, address: {}, code: {}"
                    , firstWrite, addressRegister, codeRegister, verbose);
        } else {
            writePendingControlPort = false;
            long all = ((firstWrite << 16) | data);
            LogHelper.printLevel(LOG, Level.INFO,
                    "writeAddr-2: secondWord: {}, address: {}, code: {}, dataLong: {}, mode: {}"
                    , data, addressRegister, codeRegister, all, vramMode, verbose);
            if ((codeRegister & 0b10_0000) > 0) { // DMA
                VdpDmaHandler.DmaMode dmaMode = dmaHandler.setupDma(vramMode, all, m1);
                if (dmaMode == VdpDmaHandler.DmaMode.MEM_TO_VRAM) {
                    bus.setVdpBusyState(VdpBusyState.MEM_TO_VRAM);
                }
                LogHelper.printLevel(LOG, Level.INFO, "After DMA setup, writeAddr: {}, data: {}, firstWrite: {}"
                        , addressRegister, all, writePendingControlPort, verbose);
            } else if(ENABLE_READ_AHEAD && (codeRegister & 1) == 0){ //vdp read
                pendingReadEntry.data = readDataPortInternal();
                pendingReadEntry.vdpRamMode = vramMode;
            }
        }
    }

    private void writeRegister(int data) {
        writeRegister((data >> 8) & 0x1F, data & 0xFF);
    }

    private void writeRegister(int reg, int dataControl) {
        //sms mode only affects reg [0 - 0xA]
        boolean invalidWrite = reg >= VDP_REGISTERS_SIZE || (!m5 && reg > 0xA);
        if (invalidWrite) {
            LOG.warn("Ignoring write to invalid VPD register: {}, mode5: {}, value: {}", reg, m5, dataControl);
            return;
        }
        LogHelper.printLevel(LOG, Level.INFO, "writeReg: {}, data: {}", reg, dataControl, verbose);
        logRegisterChange(reg, dataControl);
        registers[reg] = dataControl;
        updateVariables(reg, dataControl);
    }

    private void updateVariables(int regNumber, int data) {
        VdpRegisterName reg = getRegisterName(regNumber);
        switch (reg) {
            case MODE_1:
                boolean newLcb = ((data >> 5) & 1) == 1;
                updateLcb(newLcb);
                boolean newM3 = ((data >> 1) & 1) == 1;
                updateM3(newM3);
                boolean newIe1 = ((data >> 4) & 1) == 1;
                updateIe1(newIe1);
                break;
            case MODE_2:
                boolean ext = ((data >> 7) & 1) == 1;
                if (exVram != ext) {
                    exVram = ext;
                    LOG.debug("128kb VRAM: {}", exVram);
                }
                displayEnable = ((data >> 6) & 1) == 1;
                ie0 = ((data >> 5) & 1) == 1;
                m1 = ((data >> 4) & 1) == 1;
                m2 = ((data >> 3) & 1) == 1;
                boolean mode5 = ((data >> 2) & 1) == 1;
                if (m5 != mode5) {
                    LOG.info("Mode5: {}", mode5);
                    m5 = mode5;
                }
                break;
            case MODE_4:
                boolean rs0 = Util.bitSetTest(data, 7);
                boolean rs1 = Util.bitSetTest(data, 0);
                h40 = rs0 && rs1;
                boolean val = Util.bitSetTest(data, 3);
                if (val != ste) {
                    LOG.debug("Shadow highlight: {}", val);
                }
                ste = val;
                InterlaceMode prev = interlaceMode;
                interlaceMode = InterlaceMode.getInterlaceMode((data & 0x7) >> 1);
                if (prev != interlaceMode) {
                    LOG.info("InterlaceMode: {}", interlaceMode);
                }
                break;
            case AUTO_INCREMENT:
                autoIncrementData = data;
                break;
            case HCOUNTER_VALUE:
                logVerbose("Update hLinePassed register: %s", (data & 0x00FF));
                fireVdpEvent(VdpEvent.REG_H_LINE_COUNTER_CHANGE, data);
                break;
            case SPRITE_TABLE_LOC:
                updateSatLocation();
                break;
            default:
                break;
        }
    }

    private void updateSatLocation() {
        int res = VdpRenderHandler.getSpriteTableLocation(this, videoMode.isH40());
        if (res != satStart) {
//            LOG.info("Sat location: {} -> {}", Integer.toHexString(satStart), Integer.toHexString(res));
            satStart = res;
            memoryInterface.setSatBaseAddress(satStart);
        }
    }

    private void logRegisterChange(int reg, int data) {
        int current = registers[reg];
        //&& reg < 0x13 && interruptHandler.isActiveScreen()
        if (regVerbose && current != data && reg < 0x13) {
            String msg = new ParameterizedMessage("{} changed from: {}, to: {} -- de{}",
                    getRegisterName(reg), Long.toHexString(current), Long.toHexString(data), (displayEnable ? 1 : 0)).getFormattedMessage();
            LOG.info(getVdpStateString(msg));
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
            logVerbose("Update ie1 register: %s", newIe1 ? 1 : 0);
        }
    }

    private void updateLcb(boolean newLcb) {
        if (newLcb != lcb) {
            lcb = newLcb;
            logVerbose("Update lcb: %s", lcb ? 1 : 0);
            fireVdpEvent(VdpEvent.LEFT_COL_BLANK, lcb);
        }
    }

    private void doDma(boolean externalSlot) {
        if (externalSlot && dma == 1) {
            boolean dmaDone = dmaHandler.doDmaSlot(videoMode);
            dma = dmaDone ? 0 : dma;
            if (dma == 0 && dmaDone) {
                LogHelper.printLevel(LOG, Level.INFO, "{}: OFF", dmaHandler.getDmaMode(), verbose);
                evaluateVdpBusyState();
            }
        }
    }

    /**
     * When doing 68K -> VDP RAM transfers, the 68000 is frozen. For VRAM fills
     * and copies, the 68000 runs normally, but you can only read the control
     * port, HV counter, and write to the PSG register.
     */
    private void evaluateVdpBusyState() {
        VdpBusyState state = fifo.isFull() ? VdpBusyState.FIFO_FULL : VdpBusyState.NOT_BUSY;
        if (state == VdpBusyState.NOT_BUSY && (dma == 1 && dmaHandler.dmaInProgress())) {
            state = VdpBusyState.getVdpBusyState(dmaHandler.getDmaMode());
        }
        bus.setVdpBusyState(state);
    }

    @Override
    public int runSlot() {
//        LogHelper.printLevel(LOG, Level.INFO, "Start slot: {}", interruptHandler.getSlotNumber(), verbose);
        //slot granularity -> 2 H counter increases per cycle
        interruptHandler.increaseHCounterSlot();

        //vblank bit is set during all of vblank (and while display is disabled)
        //VdpFifoTesting !disp -> vb = 1, but not for hb
        hb = interruptHandler.ishBlankSet() ? 1 : 0;
        vb = interruptHandler.isvBlankSet() || !displayEnable ? 1 : 0;
        vip = interruptHandler.isvIntPending() ? 1 : vip;

        processExternalSlot();

        //draw the frame
        if (interruptHandler.isDrawFrameSlot()) {
            logVerbose("Draw Screen");
            debugViewer.update();
            list.forEach(VdpEventListener::onNewFrame);
            resetVideoMode(false);
        }
        if (interruptHandler.isDrawLineSlot()) {
            logVerbose("Draw Scanline: %s", interruptHandler.vCounterInternal);
            renderHandler.renderLine(interruptHandler.vCounterInternal);
            debugViewer.updateLine(interruptHandler.vCounterInternal);
        }
        return interruptHandler.getVdpClockSpeed();
    }


    private void processExternalSlot() {
        //vb = 1 implies !displayEnable
        boolean isExternalSlot = interruptHandler.isExternalSlot(vb == 1);
        //fifo has priority over DMA
        if (fifo.isEmpty()) {
            doDma(isExternalSlot);
        }
        writeDataToVram(isExternalSlot);
    }

    private void updateFifoState(IVdpFifo fifo) {
        fifoFull = fifo.isFull() ? 1 : 0;
        fifoEmpty = fifo.isEmpty() ? 1 : 0;
    }

    @Override
    public void resetVideoMode(boolean force) {
        VideoMode newVideoMode = getVideoMode(region, h40, m2);
        if (videoMode != newVideoMode || force) {
            this.videoMode = newVideoMode;
            LOG.info("Video mode changed: {}, {}", videoMode, videoMode.getDimension());
            pal = videoMode.isPal() ? 1 : 0;
            updateSatLocation();
            fireVdpEvent(VdpEvent.VIDEO_MODE, newVideoMode);
        }
    }

    @Override
    public int[] getScreenDataLinear() {
        return renderHandler.getScreenDataLinear();
    }

    @Override
    public List<VdpEventListener> getVdpEventListenerList() {
        return list;
    }

    @Override
    public void dumpScreenData() {
        renderHandler.dumpScreenData();
    }

    @Override
    public void setDmaFlag(int value) {
        dma = value;
    }

    @Override
    public IVdpFifo getFifo() {
        return fifo;
    }

    @Override
    public VdpMemory getVdpMemory() {
        return memoryInterface;
    }

    @Override
    public VramMode getVramMode() {
        return vramMode;
    }

    @Override
    public InterlaceMode getInterlaceMode() {
        return interlaceMode;
    }

    @Override
    public String getVdpStateString() {
        return interruptHandler.getStateString(" - ") + ", ieVINT" + (ie0 ? 1 : 0) + ",ieHINT" + (ie1 ? 1 : 0);
    }

    @Override
    public void setRegion(RegionDetector.Region region) {
        this.region = region;
    }

    @Override
    public void reload() {
        initMode();
        //force javaPalette update
        IntStream.range(0, VDP_CRAM_SIZE).forEach(i -> memoryInterface.writeCramByte(i, memoryInterface.readCramByte(i)));
        renderHandler.initLineData(0);
    }

    @Override
    public void reset() {
        this.debugViewer.reset();
        this.list.clear();
    }

    private final void logVerbose(String str, int arg) {
        if (verbose) {
            LOG.info(getVdpStateString(String.format(str, arg)));
        }
    }

    private final void logVerbose(String str) {
        if (verbose) {
            LOG.info(getVdpStateString(str));
        }
    }

    private final String getVdpStateString(String head) {
        return interruptHandler.getStateString(head + " - ") + ", ieVINT" + (ie0 ? 1 : 0) + ",ieHINT" + (ie1 ? 1 : 0);
    }
}