package mcd.cdd;

import mcd.dict.MegaCdDict;
import mcd.dict.MegaCdMemoryContext;
import omegadrive.util.BufferUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import static mcd.dict.MegaCdDict.RegSpecMcd.*;
import static mcd.dict.MegaCdDict.SUB_CPU_REGS_MASK;
import static omegadrive.util.BufferUtil.readBuffer;
import static omegadrive.util.BufferUtil.writeBufferRaw;
import static omegadrive.util.Util.th;

/**
 * Cdd
 * Adapted from the Ares emulator
 * <p>
 * NEC uPD75006 (G-631)
 * 4-bit MCU HLE
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public interface Cdd extends BufferUtil.StepDevice {

    enum CddStatus {
        Stopped,  //motor disabled
        Playing,  //data or audio playback in progress
        Seeking,  //move to specified time
        Scanning,  //skipping to a specified track
        Paused,  //paused at a specific time
        DoorOpened,  //drive tray is open
        ChecksumError,  //invalid communication checksum
        CommandError,  //missing command
        FunctionError,  //error during command execution
        ReadingTOC,  //reading table of contents
        Tracking,  //currently skipping tracks
        NoDisc,  //no disc in tray or cannot focus
        LeadOut,  //paused in the lead-out area of the disc
        LeadIn,  //paused in the lead-in area of the disc
        TrayMoving,  //drive tray is moving in response to open/close commands
        Test,  //in test mode
    }

    enum CddCommand {
        Idle,  //no operation
        Stop,  //stop motor
        Request,  //change report type
        SeekPlay,  //read ROM data
        SeekPause,  //seek to a specified location
        Pause,  //pause the drive
        Play,  //start playing from the current location
        Forward,  //forward skip and playback
        Reverse,  //reverse skip and playback
        TrackSkip,  //start track skipping
        TrackCue,  //start track cueing
        DoorClose,  //close the door
        DoorOpen,  //open the door
    }

    enum CddRequest {
        AbsoluteTime,
        RelativeTime,
        TrackInformation,
        DiscCompletionTime,
        DiscTracks,  //start/end track numbers
        TrackStartTime,  //start time of specific track
        ErrorInformation,
        SubcodeError,
        NotReady,  //not ready to comply with the current command
    }

    void write(MegaCdDict.RegSpecMcd regSpec, int address, int value, Size size);


    CddContext getCddContext();

    boolean isCommandPending();

    default void advance() {
        throw new RuntimeException("not implemented");
    }

    default void sample() {
        throw new RuntimeException("not implemented");
    }

    default double position(int sector) {
        throw new RuntimeException("not implemented");
    }

    default void process() {
        throw new RuntimeException("not implemented");
    }

    default boolean valid() {
        throw new RuntimeException("not implemented");
    }

    default void checksum() {
        throw new RuntimeException("not implemented");
    }

    default void insert() {
        throw new RuntimeException("not implemented");
    }

    default void eject() {
        throw new RuntimeException("not implemented");
    }

    default void power(boolean reset) {
        throw new RuntimeException("not implemented");
    }

    //status after seeking (Playing or Paused)
    //sector = current frame#
    //sample = current audio sample# within current frame
    //track = current track#
    class CddIo {
        public CddStatus status;
        public int seeking, latency, sector, sample, track, tocRead;

        static CddIo create() {
            CddIo c = new CddIo();
            c.status = CddStatus.NoDisc;
            return c;
        }
    }

    class CddContext {
        public CddIo io;
        public int hostClockEnable, statusPending;
        public int[] status = new int[10];
        public int[] command = new int[10];

        static CddContext create(CddIo cddIo) {
            CddContext c = new CddContext();
            c.io = cddIo;
            return c;
        }
    }

    static Cdd createInstance(MegaCdMemoryContext memoryContext) {
        return new CddImpl(memoryContext);
    }
}

class CddImpl implements Cdd {

    private final static Logger LOG = LogHelper.getLogger(CddImpl.class.getSimpleName());

    public final CddContext cddContext = CddContext.create(CddIo.create());

    private final MegaCdMemoryContext memoryContext;

    public CddImpl(MegaCdMemoryContext mc) {
        memoryContext = mc;
    }

    @Override
    public void write(MegaCdDict.RegSpecMcd regSpec, int address, int value, Size size) {
        writeBufferRaw(memoryContext.commonGateRegsBuf, address & SUB_CPU_REGS_MASK, value, size);
        switch (regSpec) {
            case MCD_CDD_CONTROL -> {
                int v = readBuffer(memoryContext.commonGateRegsBuf, regSpec.addr, Size.WORD);
                cddContext.hostClockEnable = (v & 4);
            }
            case MCD_CDD_COMM5, MCD_CDD_COMM6, MCD_CDD_COMM7, MCD_CDD_COMM8, MCD_CDD_COMM9 -> {
                switch (size) {
                    case BYTE -> writeCommByte(regSpec, address, value);
                    case WORD -> {
                        writeCommByte(regSpec, address, value >>> 8);
                        writeCommByte(regSpec, address + 1, value);
                    }
                    case LONG -> {
                        writeCommByte(regSpec, address, value >>> 24);
                        writeCommByte(regSpec, address + 1, value >>> 16);
                        writeCommByte(regSpec, address + 2, value >>> 8);
                        writeCommByte(regSpec, address + 3, value);
                    }
                }
            }
            case MCD_CD_FADER -> LogHelper.logWarnOnce(LOG, "Write {}: {} {}", regSpec, th(value), size);
            default -> LOG.error("S CDD Write {}: {} {} {}", regSpec, th(address), th(value), size);
        }
    }

    private void writeCommByte(MegaCdDict.RegSpecMcd regSpec, int addr, int value) {
        int index = (addr & SUB_CPU_REGS_MASK) - MCD_CDD_COMM5.addr;
        updateCommand(index, value & 0xF);
        switch (regSpec) {
            case MCD_CDD_COMM9 -> {
                //Transmission Command 9
                if ((addr & 1) == 1) { //unconfirmed
                    process();
                }
            }
        }
    }

    @Override
    public void checksum() {
        int checksum = 0;
        for (int i = 0; i < cddContext.command.length - 1; i++) {
            checksum += cddContext.status[i];
        }
        checksum = ~checksum;
        updateStatus(9, checksum & 0xF);
        assert readBuffer(memoryContext.commonGateRegsBuf, MCD_CDD_COMM4.addr, Size.WORD) == (checksum & 0xF);
    }

    @Override
    public boolean valid() {
        int checksum = 0;
        for (int i = 0; i < cddContext.command.length - 1; i++) {
            checksum += cddContext.command[i];
        }
        checksum = ~checksum;
        return (checksum & 0xF) == cddContext.command[9];
    }

    @Override
    public CddContext getCddContext() {
        return cddContext;
    }

    public void step(int cycles) {
        assert (readBuffer(memoryContext.commonGateRegsBuf, MCD_CDD_CONTROL.addr, Size.WORD) & 3) == 0;
        //from 0xff8036
        if (cddContext.hostClockEnable == 0) {
            return;
        }
        if (cddContext.statusPending > 0) {
            cddContext.statusPending = 0;
            //do irq
        }

        switch (cddContext.io.status) {
            case NoDisc -> {
                //do nothing
            }
            default -> LOG.error("CDD status: {}", cddContext.io.status);
        }
    }

    @Override
    public void process() {
        if (!valid()) {
            //unverified
            LOG.error("CDD checksum error");
            cddContext.io.status = CddStatus.ChecksumError;
            processDone();
            return;
        }
        CddCommand cddCommand = CddCommand.values()[cddContext.command[0]];
        switch (cddCommand) {
            case Idle -> {
                //fixes Lunar: The Silver Star
                if (cddContext.io.latency == 0 && cddContext.status[1] == 0xf) {
                    cddContext.status[1] = 0x2;
                    cddContext.status[2] = cddContext.io.track / 10;
                    cddContext.status[3] = cddContext.io.track % 10;
                }
            }
            case Stop -> {
                cddContext.io.status = /*hasFile ? Stop : */ CddStatus.NoDisc;
                for (int i = 1; i < 9; i++) {
                    updateStatus(i, 0);
                }
            }
            default -> LOG.error("Cdd command: {}", cddCommand);
        }
        processDone();
    }

    private void processDone() {
        updateStatus(0, cddContext.io.status.ordinal());
        checksum();
        cddContext.statusPending = 1;
    }

    public boolean isCommandPending() {
        return cddContext.statusPending > 0;
    }

    private void updateStatus(int pos, int val) {
        cddContext.status[pos] = val;
        writeBufferRaw(memoryContext.commonGateRegsBuf, MCD_CDD_COMM0.addr + pos, val, Size.BYTE);
    }

    private void updateCommand(int pos, int val) {
        cddContext.command[pos] = val;
        writeBufferRaw(memoryContext.commonGateRegsBuf, MCD_CDD_COMM5.addr + pos, val, Size.BYTE);
    }
}