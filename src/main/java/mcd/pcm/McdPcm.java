package mcd.pcm;

import mcd.dict.MegaCdDict;
import mcd.dict.MegaCdDict.RegSpecMcd;
import omegadrive.sound.PcmProvider;
import omegadrive.util.*;
import org.slf4j.Logger;
import s32x.util.blipbuffer.BlipBufferHelper;

import java.nio.ByteBuffer;

import static mcd.MegaCd.MCD_SUB_68K_CLOCK_MHZ;
import static mcd.bus.MegaCdSubCpuBus.logAccessReg;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;
import static omegadrive.util.Util.th;

/**
 * Ricoh RF5C68 (also known as RF5C164 and RF5C105)
 * see Mame, GenPlusGx
 * <p>
 * According to docs:
 * Sample Rate = 19.8 Khz at 7.6Mhz (384 clock divider)
 * Mega Cd:
 * Sample Rate = 12.5 Mhz/384 = 32.552 Khz
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class McdPcm implements BufferUtil.StepDevice {

    private static final Logger LOG = LogHelper.getLogger(McdPcm.class.getSimpleName());

    public static final int PCM_NUM_CHANNELS = 8;

    public static final int PCM_REG_SIZE = 0x20;
    public static final int PCM_REG_MASK = PCM_REG_SIZE - 1;

    public static final int PCM_START_RAMPTR_REGS = 0x20;
    public static final int PCM_END_RAMPTR_REGS = 0x40;
    public static final int PCM_LOOP_MARKER = 0xFF;

    public static final int PCM_START_WAVE_DATA_WINDOW = 0x1000;
    public static final int PCM_END_WAVE_DATA_WINDOW = 0x2000;

    public static final int PCM_WAVE_DATA_WINDOW_MASK = PCM_END_WAVE_DATA_WINDOW - PCM_START_WAVE_DATA_WINDOW - 1;

    //external access doubles the address, adjust the mask accordingly
    public static final int PCM_WAVE_DATA_WINDOW_MASK_EXT = PCM_END_WAVE_DATA_WINDOW - 1;
    public static final int PCM_WAVE_DATA_SIZE = 0x10000; //64Kb
    public static final int PCM_ADDRESS_MASK = PCM_WAVE_DATA_SIZE - 1;

    //5.11 fixed point number, ie top most 5 bits are the integer part, bottom 11 bits are decimal
    //0000 1000 0000 0000 (2048 base10) -> 1.0
    //0000 0100 0000 0000 (1024 base10) -> 0.5
    public static final int PCM_FIX_POINT_DECIMAL = 11;


    public static final int PCM_WAVE_BANK_SHIFT = 12;


    public static final int MCD_PCM_DIVIDER = 384;
    public static final double pcmSampleRateHz = MCD_SUB_68K_CLOCK_MHZ / MCD_PCM_DIVIDER;

    public static final int PCM_SAMPLE_RATE_HZ = (int) pcmSampleRateHz;

    private ByteBuffer waveData, pcmRegs;
    private PcmChannelContext[] chan;

    private PcmProvider playSupport = PcmProvider.NO_SOUND;

    private int channelBank, waveBank, active, chanControl;
    private int ls, rs;

    int sampleNum = 0;

    //TODO
    public static McdPcm pcm;

    final boolean verbose = false;

    static class PcmChannelContext {
        public int num, on, env, panl, panr, freqDelta, loopAddr, startAddr;
        public int addrCounter, factorl, factorr;

        protected void updateChannelFactors() {
            factorl = env * panl;
            factorr = env * panr;
        }
    }

    public McdPcm(boolean enableSound) {
        waveData = ByteBuffer.allocate(PCM_WAVE_DATA_SIZE);
        pcmRegs = ByteBuffer.allocate(PCM_REG_SIZE);
        chan = new PcmChannelContext[PCM_NUM_CHANNELS];
        playSupport = enableSound ? new BlipPcmProvider("PCM", RegionDetector.Region.USA, pcmSampleRateHz) : PcmProvider.NO_SOUND;
        for (int i = 0; i < PCM_NUM_CHANNELS; i++) {
            chan[i] = new PcmChannelContext();
            chan[i].num = i;
        }
        pcm = this;
    }



    /**
     * Channel0 RAMPTR, 16 bit wide
     * 0x21 -> 0x10 LSB
     * 0x23 -> 0x11 MSB
     */
    private int readRamPointerRegs(int address, Size size) {
        return switch (size) {
            case BYTE -> readRamPointerRegsByte(address);
            case WORD -> readRamPointerRegsWord(address);
            case LONG -> (readRamPointerRegsWord(address) << 16) | readRamPointerRegsWord(address + 2);

        };
    }

    private int readRamPointerRegsWord(int address) {
        int val = readRamPointerRegsByte(address | 1);
        return (val << 8) | val;
    }

    private int readRamPointerRegsByte(int address) {
        /**
         * Channel0 RAMPTR, 16 bit wide
         * 0x21 -> 0x10 LSB
         * 0x23 -> 0x11 MSB
         */
        int chanIndex = (address >> 2) & 0x07;
        //msb right shift by 8, LSB no shi(f)t
        int msbShift = ((address >> 1) & 1) << 3;
        return (chan[chanIndex].addrCounter >> (11 + msbShift)) & 0xFF; //MSB or LSB
    }
    public int read(int address, Size size) {
        address &= PCM_ADDRESS_MASK;
        if ((address >= PCM_START_RAMPTR_REGS) && (address < PCM_END_RAMPTR_REGS)) {
            return readRamPointerRegs(address, size);
        } else if (address < PCM_REG_MASK) {
            RegSpecMcd regSpec = getPcmReg(address);
            logAccessReg(regSpec, SUB_M68K, address, size, true);
            return BufferUtil.readBuffer(pcmRegs, address, size);
        }
        assert size == Size.BYTE : th(address) + "," + size;
        if (address >= PCM_START_WAVE_DATA_WINDOW) {
            //NOTE, this only support reading from [0x2000-0x4000] which in turns maps to [0-0x1000] | waveBank
            int addr = address;
            assert address >= 0x2000 && address < 0x4000 : th(address);
            address = waveBank | ((address & PCM_WAVE_DATA_WINDOW_MASK_EXT) >> 1);
            int res = BufferUtil.readBuffer(waveData, address, size);
            if (verbose)
                LOG.info("PCM R {}(ext {}), val: {} {}", th(address), th(addr), th(res & size.getMask()), size);
            return res;
        }
        LogHelper.logWarnOnce(LOG, "Unhandled PCM read: {} {}", th(address), size);
        return size.getMask();
    }

    public void write(int address, int value, Size size) {
        assert size != Size.LONG;
        if (size == Size.WORD) {
            address |= 1; //write LSB only to odd byte
            size = Size.BYTE;
        }
        address &= PCM_ADDRESS_MASK;
        value &= size.getMask();
        if (address >= PCM_START_WAVE_DATA_WINDOW) {
            //NOTE, this only support writing to [0x2000-0x4000) which in turns maps to [0-0x1000) | waveBank
            assert address >= 0x2000 && address < 0x4000 : th(address);
            pcmDataWriteByte(address, value);
        } else if (address < PCM_REG_MASK) {
            RegSpecMcd regSpec = getPcmReg(address);
            logAccessReg(regSpec, SUB_M68K, address, size, false);
            writeRegByte(regSpec, address, value);
        } else {
            LOG.error("Unhandled write: {}, {} {}", th(address), th(value), size);
            assert false;
        }
    }

    public void pcmDataWriteByte(int addr, int value) {
        int address = waveBank | ((addr & PCM_WAVE_DATA_WINDOW_MASK_EXT) >> 1);
        if (verbose) LOG.info("PCM W {}(ext {}) val: {} {}", th(address), th(addr), th(value & 0xFF), Size.BYTE);
        BufferUtil.writeBufferRaw(waveData, address, value, Size.BYTE);
    }

    private void writeRegByte(RegSpecMcd regSpec, int address, int value) {
        PcmChannelContext channel = chan[channelBank];
        switch (regSpec) {
            case MCD_PCM_ENV -> {
                channel.env = value & 0xFF; //8-bit ENV
                channel.updateChannelFactors();
            }
            case MCD_PCM_PAN -> {
                channel.panl = (value >>> 4) & 0xF; //4-bit PAN
                channel.panr = value & 0xF;
                channel.updateChannelFactors();
            }
            case MCD_PCM_FDL -> channel.freqDelta = (channel.freqDelta & 0xFF00) | value;
            case MCD_PCM_FDH -> channel.freqDelta = (value << 8) | (channel.freqDelta & 0xFF);
            case MCD_PCM_LSL -> channel.loopAddr = (channel.loopAddr & 0xFF00) | value;
            case MCD_PCM_LSH -> channel.loopAddr = (value << 8) | (channel.loopAddr & 0xFF);
            case MCD_PCM_START -> channel.startAddr = value << 8;
            case MCD_PCM_CTRL -> {
                active = value >> 7;
                if ((value & 0x40) > 0) { //MOD = 1
                    channelBank = value & 7;
                } else { //MOD = 0
                    int wb = waveBank;
                    waveBank = (value & 0xf) << PCM_WAVE_BANK_SHIFT;
                    if (wb != waveBank) {
                        if (verbose) LOG.info("PCM bank: {}", th(waveBank));
                    }
                }
            }
            case MCD_PCM_ON_OFF -> {
                chanControl = ~value & 0xFF; //ON=0, OFF=1
                for (int i = 0; i < PCM_NUM_CHANNELS; i++) {
                    PcmChannelContext ct = chan[i];
                    int chanOn = (chanControl >> i) & 1;
                    if (ct.on == 0 && chanOn > 0) {
                        ct.addrCounter = channel.startAddr << PCM_FIX_POINT_DECIMAL;
                    }
                    ct.on = chanOn;
                }
            }
            default -> LogHelper.logWarnOnce(LOG, "PCM reserved reg write {}", th(address));
        }
        BufferUtil.writeBufferRaw(pcmRegs, address, value, Size.BYTE);
    }

    private RegSpecMcd getPcmReg(int address) {
        return MegaCdDict.getRegSpec(SUB_M68K, 0x100 + (address & 0xFF));
    }

    @Override
    public void step(int cycles) {
        //TODO check
        if (chanControl == 0 || active == 0) {
            return;
        }
        generateOneSample();
    }

    public ByteBuffer getWaveData() {
        return waveData;
    }


    private void generateOneSample() {
        ls = 0;
        rs = 0;
        sampleNum++;

        for (PcmChannelContext channel : chan) {
            if (channel.on > 0) {
                /* Read wave data from current address */
                int pcm = waveData.get(channel.addrCounter >>> PCM_FIX_POINT_DECIMAL) & 0xFF;
                if (pcm == PCM_LOOP_MARKER) /* Loop stop data */ {
                    /* Set to loop start address */
                    channel.addrCounter = channel.loopAddr << PCM_FIX_POINT_DECIMAL;

                    /* Re-read wave data */
                    pcm = waveData.get(channel.loopAddr) & 0xFF;

                    if (pcm == PCM_LOOP_MARKER) continue; /* Looped to loop stop data, move to next channel */
                }

                /* Advance address counter (limit to 27-bits) */
                channel.addrCounter = (channel.addrCounter + channel.freqDelta) & 0x07FFFFFF;

				/* Apply panning + envelope and add/sub to output buffer

					7-bit PCM x 8-bit ENV x 4-bit PAN = 19-bit
					The most significant 14-bits are accumulated
				*/
                if ((pcm & 0x80) > 0) {
                    pcm &= 0x7F;
                    ls += (pcm * channel.factorl) >>> 5;
                    rs += (pcm * channel.factorr) >>> 5;
                } else {
                    ls -= (pcm * channel.factorl) >>> 5;
                    rs -= (pcm * channel.factorr) >>> 5;
                }
            }
        }

        /* Limiter (signed 16-bit) */
        if (ls != (short) ls) {
            ls = BlipBufferHelper.clampToShort(ls);
        }
        if (rs != (short) rs) {
            rs = BlipBufferHelper.clampToShort(rs);
        }
        /* 16-bit DAC output (interleaved) */
        playSupport.playSample(ls, rs);
    }

    public void updateVideoMode(VideoMode videoMode) {
        playSupport.updateRegion(videoMode.getRegion());
    }

    public void newFrame() {
        playSupport.onNewFrame();
        sampleNum = 0;
    }

    @Override
    public void close() {
        playSupport.close();
    }

    @Override
    public void reset() {
        playSupport.reset();
    }
}