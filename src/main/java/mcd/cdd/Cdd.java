package mcd.cdd;

import mcd.bus.McdSubInterruptHandler;
import mcd.cdc.Cdc;
import mcd.dict.MegaCdDict;
import mcd.dict.MegaCdMemoryContext;
import omegadrive.util.BufferUtil;
import omegadrive.util.Size;
import omegadrive.util.VideoMode;

import java.nio.file.Path;

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

    enum CddControl_DM_bit {MUSIC_0, DATA_1}

    void tryInsert(Path cueSheet);
    void write(MegaCdDict.RegSpecMcd regSpec, int address, int value, Size size);

    int read(MegaCdDict.RegSpecMcd regSpec, int address, Size size);

    default double position(int sector) {
        //convert sector# to normalized sector position on the CD-ROM surface for seek latency calculation

        double sectors = 7500.0 + 330000.0 + 6750.0;
        double radius = 0.058 - 0.024;
        double innerRadius = 0.024 * 0.024;  //in mm
        double outerRadius = 0.058 * 0.058;  //in mm

        sector += 7500; //session.leadIn.lba;  //convert to natural
        return Math.sqrt(sector / sectors * (outerRadius - innerRadius) + innerRadius) / radius;
    }

    //should be called at 44.1 khz
    void stepCdda();


    void updateVideoMode(VideoMode videoMode);

    void newFrame();

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
    }

    class CddContext {
        public CddIo io;
        public int hostClockEnable, statusPending;
        public int[] statusRegs = new int[10];
        public int[] commandRegs = new int[10];

        public static CddContext create(CddIo cddIo) {
            CddContext c = new CddContext();
            c.io = cddIo;
            return c;
        }
    }

    CddStatus[] statusVals = CddStatus.values();

    static Cdd createInstance(MegaCdMemoryContext memoryContext, McdSubInterruptHandler ih, Cdc cdc) {
        return new CddImpl(memoryContext, ih, cdc);
    }
}