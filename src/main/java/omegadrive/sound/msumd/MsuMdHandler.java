package omegadrive.sound.msumd;

import omegadrive.bus.md.GenesisBus;
import omegadrive.util.Size;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.AudioFormat;
import java.io.File;
import java.util.Optional;

/**
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public interface MsuMdHandler {

    Logger LOG = LogManager.getLogger(GenesisBus.class.getSimpleName());

    int CLOCK_ADDR = 0xa1201f;
    int CMD_ADDR = 0xa12010;
    int CMD_ARG_ADDR = CMD_ADDR + 2;
    int MCD_STATUS_ADDR = 0xA12020;
    int MCD_WRAM_START = 0x42_0000;
    int MCD_WRAM_END = 0x42_07FF; //probably wrong
    int MCD_GATE_ARRAY_START = 0xa12001;
    int MCD_MEMWP = 0xA12002;
    int MCD_MMOD = 0xa12003;
    int MCD_COMF = 0xa1200f;

    int CDDA_SAMPLE_RATE = 44100;

    AudioFormat CDDA_FORMAT = new AudioFormat(CDDA_SAMPLE_RATE,
            16, 2, true, false);

    MsuMdHandler NO_OP_HANDLER = new MsuMdHandler() {
        @Override
        public int handleMsuMdRead(int address, Size size) {
            return 0;
        }

        @Override
        public void handleMsuMdWrite(int address, int data, Size size) {
            //Do nothing
        }
    };

    int handleMsuMdRead(int address, Size size);

    void handleMsuMdWrite(int address, int data, Size size);

    default void close() {
        //do nothing
    }

    enum MsuCommand {
        PLAY(0x11),
        PLAY_LOOP(0x12),
        PAUSE(0x13),
        RESUME(0x14),
        VOL(0x15),
        NO_SEEK(0x16),
        PLAY_OFFSET(0x1A),
        UNKNOWN(-1);

        private int val;

        MsuCommand(int val) {
            this.val = val;
        }

        public static MsuCommand getMsuCommand(int val) {
            for (MsuCommand c : MsuCommand.values()) {
                if (c.val == val) {
                    return c;
                }
            }
            LOG.error("Unknown command code: {}", val);
            return UNKNOWN;
        }
    }

    enum CueFileDataType {
        BINARY,
        WAVE,
        OGG,
        UNKNOWN;

        static MsuMdHandlerImpl.CueFileDataType getFileType(String type) {
            for (MsuMdHandlerImpl.CueFileDataType c : MsuMdHandlerImpl.CueFileDataType.values()) {
                if (c.name().equalsIgnoreCase(type)) {
                    return c;
                }
            }
            return UNKNOWN;
        }
    }

    class MsuCommandArg {
        MsuCommand command = MsuCommand.UNKNOWN;
        int arg;
        int arg1;
    }

    TrackDataHolder NO_TRACK = new TrackDataHolder();

    class TrackDataHolder {
        MsuMdHandlerImpl.CueFileDataType type = CueFileDataType.UNKNOWN;
        Optional<Boolean> cueLoop = Optional.empty();
        Optional<Integer> cueLoopPoint = Optional.empty();
        Optional<File> waveFile = Optional.empty();
        Optional<Integer> numBytes = Optional.empty();
        Optional<Integer> startFrame = Optional.empty();
    }
}
