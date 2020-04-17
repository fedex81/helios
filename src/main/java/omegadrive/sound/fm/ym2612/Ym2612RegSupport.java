package omegadrive.sound.fm.ym2612;

import static omegadrive.sound.fm.MdFmProvider.FM_ADDRESS_PORT0;
import static omegadrive.sound.fm.MdFmProvider.FM_ADDRESS_PORT1;

/**
 * Ym2612RegSupport
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class Ym2612RegSupport {
    protected final int[][] ym2612Reg = new int[2][0x100];
    protected int addressLatch;

    public int readRegister(int type, int regNumber) {
        return ym2612Reg[type][regNumber];
    }

    public void write(int addr, int data) {
        addr = addr & 0x3;
        switch (addr) {
            case FM_ADDRESS_PORT0:
                addressLatch = data;
                break;
            case FM_ADDRESS_PORT1:
                addressLatch = data + 0x100;
                break;
            default:
                writeDataPort(data);
                break;
        }
    }

    protected void writeDataPort(int data) {
        int realAddr = addressLatch;
        int regPart = realAddr >= 0x100 ? 1 : 0;
        int realAddrReg = regPart > 0 ? realAddr - 0x100 : realAddr;

        if (realAddr < 0x30) {
            ym2612Reg[regPart][realAddrReg] = data;
        }
    }
}
