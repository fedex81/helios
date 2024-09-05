package mcd.cdd;

import com.google.common.base.MoreObjects;
import mcd.bus.McdSubInterruptHandler;
import mcd.cdc.Cdc;
import mcd.dict.MegaCdDict;
import mcd.dict.MegaCdMemoryContext;
import omegadrive.util.BufferUtil;
import omegadrive.util.Size;
import omegadrive.util.VideoMode;

import java.util.Arrays;

import static mcd.cdd.Cdd.CddStatus.NoDisc;

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

    //status, command registers
    int CDD_REG_NUM = 10;

    int CDD_CHECKSUM_BYTE = 9;

    int PREGAP_LEN_LBA = 150;

    int LBA_READAHEAD_LEN = 3;

    enum CddStatus {
        Stopped,  //0, motor disabled
        Playing,  //1, data or audio playback in progress
        Seeking,  //2, move to specified time
        Scanning,  //3, skipping to a specified track
        Paused,  //4, paused at a specific time
        DoorOpened,  //5, drive tray is open
        ChecksumError,  //6, invalid communication checksum
        CommandError,  //7, missing command
        FunctionError,  //8, error during command execution
        ReadingTOC,  //9, reading table of contents
        Tracking,  //A, currently skipping tracks
        NoDisc,  //B, no disc in tray or cannot focus
        LeadOut,  //C, paused in the lead-out area of the disc
        LeadIn,  //D, paused in the lead-in area of the disc
        TrayMoving,  //E, drive tray is moving in response to open/close commands
        Test,  //F, in test mode
    }

    enum CddCommand {
        DriveStatus,  //0, Drive Status
        Stop,  //1, stop motor
        Request,  //2, change report type
        SeekPlay,  //3, read ROM data
        SeekPause,  //4, seek to a specified location
        None_5,
        Pause,  //6, pause the drive
        Play,  //7, start playing from the current location
        Forward,  //8, forward skip and playback
        Reverse,  //9, reverse skip and playback
        TrackSkip,  //A,start track skipping
        TrackCue,  //B, start track cueing
        DoorClose,  //C, close the door
        DoorOpen,  //D, open the door
    }

    enum CddRequest {
        AbsoluteTime, //0
        RelativeTime, //1
        TrackInformation, //2
        DiscCompletionTime, //3
        DiscTracks,  //4 start/end track numbers
        TrackStartTime,  //5 start time of specific track
        ErrorInformation, //6
        SubcodeError, //7
        NotReady,  //8 not ready to comply with the current command
    }

    enum CddControl_DM_bit {MUSIC_0, DATA_1}

    void tryInsert(ExtendedCueSheet cueSheet);
    void write(MegaCdDict.RegSpecMcd regSpec, int address, int value, Size size);

    int read(MegaCdDict.RegSpecMcd regSpec, int address, Size size);

    //should be called at 44.1 khz
    void stepCdda();


    CddContext getCddContext();

    void updateVideoMode(VideoMode videoMode);

    void newFrame();

    void logStatus();

    //status after seeking (Playing or Paused)
    //sector = current frame#
    //sample = current audio sample# within current frame
    //track = current track#
    class CddIo {
        public CddStatus status;
        public int seeking, latency, sector, sample, track, tocRead;

        public static CddIo create() {
            CddIo c = new CddIo();
            c.status = NoDisc;
            return c;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("status", status)
                    .add("seeking", seeking)
                    .add("latency", latency)
                    .add("sector", sector)
                    .add("sample", sample)
                    .add("track", track)
                    .add("tocRead", tocRead)
                    .toString();
        }
    }

    class CddContext {
        public CddIo io;
        public int hostClockEnable;
        public int[] statusRegs = new int[CDD_REG_NUM];
        public int[] commandRegs = new int[CDD_REG_NUM];

        public static CddContext create(CddIo cddIo) {
            CddContext c = new CddContext();
            c.io = cddIo;
            return c;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("io", io)
                    .add("hostClockEnable_HOCK", hostClockEnable)
                    .add("statusRegs", Arrays.toString(statusRegs))
                    .add("commandRegs", Arrays.toString(commandRegs))
                    .toString();
        }
    }

    CddStatus[] statusVals = CddStatus.values();
    CddCommand[] commandVals = CddCommand.values();
    CddRequest[] requestVals = CddRequest.values();


    static Cdd createInstance(MegaCdMemoryContext memoryContext, McdSubInterruptHandler ih, Cdc cdc) {
        return new CddImpl(memoryContext, ih, cdc);
    }

    static int getCddChecksum(int[] vals) {
        int checksum = 0;
        for (int i = 0; i < vals.length - 1; i++) {
            checksum += vals[i];
        }
        return ~checksum & 0xF;
    }
}