package mcd.pcm;

import mcd.dict.MegaCdDict;
import mcd.dict.MegaCdDict.RegSpecMcd;
import omegadrive.util.BufferUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;
import s32x.util.blipbuffer.BlipBufferHelper;

import java.nio.ByteBuffer;

import static mcd.bus.MegaCdSubCpuBus.logAccessReg;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;
import static omegadrive.util.Util.th;

/**
 * Ricoh RF5C68 (also known as RF5C164 and RF5C105)
 * Inspired by: https://github.com/michelgerritse/TritonCore/blob/master/Devices/Sound/RF5C68.cpp
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class McdPcm implements BufferUtil.StepDevice {

    private static final Logger LOG = LogHelper.getLogger(McdPcm.class.getSimpleName());

    public static final int PCM_NUM_CHANNELS = 8;
    public static final int PCM_REG_MASK = 0x2F;

    public static final int PCM_LOOP_MARKER = 0xFF;

    public static final int PCM_START_WAVE_DATA_WINDOW = 0x1000;
    public static final int PCM_END_WAVE_DATA_WINDOW = 0x2000;
    public static final int PCM_WAVE_DATA_SIZE = 0x10000; //64Kb
    public static final int PCM_ADDRESS_MASK = 0x1FFF;

    private ByteBuffer waveData, pcmRegs;
    private PcmChannelContext[] chan;

    private int channelBank, waveBank, active, chanControl;
    private int ls, rs;

    static class PcmChannelContext {
        public int num, on, env, panl, panr, freqDelta, loopAddr, startAddr;
        public int addrCounter, factorl, factorr;

        protected void updateChannelFactors() {
            factorl = env * panl;
            factorr = env * panr;
        }
    }


    public McdPcm() {
        waveData = ByteBuffer.allocate(PCM_WAVE_DATA_SIZE);
        pcmRegs = ByteBuffer.allocate(0x2F);
        chan = new PcmChannelContext[PCM_NUM_CHANNELS];
        for (int i = 0; i < PCM_NUM_CHANNELS; i++) {
            chan[i] = new PcmChannelContext();
            chan[i].num = i;
        }
    }

    public int read(int address, Size size) {
        assert size == Size.BYTE;
        address &= PCM_ADDRESS_MASK;
        if (address >= PCM_START_WAVE_DATA_WINDOW && address < PCM_END_WAVE_DATA_WINDOW) {
            LogHelper.logWarnOnce(LOG, "Reading from PCM wave data");
            BufferUtil.readBuffer(waveData, address, size);
            assert false; //TODO anyone doing this??
        } else if (address < PCM_REG_MASK) {
            RegSpecMcd regSpec = getPcmReg(address);
            logAccessReg(regSpec, SUB_M68K, address, 0, size, true);
            return BufferUtil.readBuffer(pcmRegs, address, Size.BYTE);
        }
        return size.getMask();
    }

    public void write(int address, int value, Size size) {
        assert size == Size.BYTE;
        address &= PCM_ADDRESS_MASK;
        value &= size.getMask();
        if (address >= PCM_START_WAVE_DATA_WINDOW && address < PCM_END_WAVE_DATA_WINDOW) {
            address = waveBank | (address & 0xFFF);
            LogHelper.logWarnOnce(LOG, "Writing to PCM wave data: {}", th(address));
            BufferUtil.writeBufferRaw(waveData, address, value, size);
        } else if (address < PCM_REG_MASK) {
            RegSpecMcd regSpec = getPcmReg(address);
            logAccessReg(regSpec, SUB_M68K, address, value, size, false);
            writeReg(regSpec, address, value);
        }
    }

    private void writeReg(RegSpecMcd regSpec, int address, int value) {
        PcmChannelContext channel = chan[channelBank];
        switch (regSpec) {
            case MCD_PCM_ENV -> {
                channel.env = value;
                channel.updateChannelFactors();
            }
            case MCD_PCM_PAN -> {
                channel.panl = value >>> 4;
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
                    waveBank = (value & 0xf) << 12;
                }
            }
            case MCD_PCM_ON_OFF -> {
                chanControl = ~value & 0xFF; //ON=0, OFF=1
                for (int i = 0; i < PCM_NUM_CHANNELS; i++) {
                    PcmChannelContext ct = chan[i];
                    int chanOn = (chanControl >> i) & 1;
                    if (ct.on == 0 && chanOn > 0) {
                        ct.addrCounter = channel.startAddr << 11;
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

    //MD M68K 7.6Mhz
    //pcm sample rate: 44.1 Khz
    //m68kCyclesPerSample = 172.33

    private static final double m68kCyclesPerSample = 172.33;
    private double cycleAccumulator = m68kCyclesPerSample;

    @Override
    public void step(int cycles) {
        if (chanControl == 0 && active == 0) {
            return;
        }
        cycleAccumulator -= cycles;
        if (cycleAccumulator < 0) {
            generateOneSample();
            cycleAccumulator += m68kCyclesPerSample;
        }
    }

    private void generateOneSample() {
        ls = 0;
        rs = 0;

        for (PcmChannelContext channel : chan) {
            if (channel.on > 0) {
                /* Read wave data from current address */
                int pcm = waveData.get(channel.addrCounter >> 11) & 0xFF;
                if (pcm == PCM_LOOP_MARKER) /* Loop stop data */ {
                    /* Set to loop start address */
                    channel.addrCounter = channel.loopAddr << 11;

                    /* Re-read wave data */
                    pcm = waveData.get(channel.addrCounter >> 11) & 0xFF;

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
                    ls += (pcm * channel.factorl) >> 5;
                    rs += (pcm * channel.factorr) >> 5;
                } else {
                    ls -= (pcm * channel.factorl) >> 5;
                    rs -= (pcm * channel.factorr) >> 5;
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
    }
}
