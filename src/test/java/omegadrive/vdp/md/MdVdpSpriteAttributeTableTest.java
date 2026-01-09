package omegadrive.vdp.md;

import omegadrive.vdp.MdVdpTestUtil;
import omegadrive.vdp.model.MdVdpProvider;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static omegadrive.vdp.MdVdpTestUtil.CTRL_PORT_MODE_4;
import static omegadrive.vdp.model.MdVdpProvider.VdpRegisterName.MODE_4;
import static omegadrive.vdp.model.VdpRenderHandler.SPRITE_TABLE_SHIFT;

/**
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class MdVdpSpriteAttributeTableTest extends BaseVdpDmaHandlerTest {

    @BeforeEach
    public void setupLocal() {
        super.setup();
    }

    /**
     * Changes the SAT location, but the sat cache keeps its contents from the previous location, only
     * updating when a vram write hits the new cache location
     * <p>
     * see CastlevaniaBloodlines lev2
     */
    @Test
    public void testSatLocationChange() {
        byte[] vram = vdpProvider.getVdpMemory().getVram().array();
        checkSatHashcode(-1128615935);

        //fill vram, sat cache changed
        writeVramRange(0, vram.length, (byte) 0x12);
        checkSatHashcode(771194881);

        int len = 640;
        int satAddr = 0xF000;
        //update sat location, sat cache unchanged
        vdpProvider.updateRegisterData(MdVdpProvider.VdpRegisterName.SPRITE_TABLE_LOC, satAddr >> SPRITE_TABLE_SHIFT);
        checkSatHashcode(771194881);

        //change byte outside the sat cache, sat cache unchanged
        writeVramByte(0x10, (byte) 0xFF);
        checkSatHashcode(771194881);

        //change byte within the sat cache, sat cache changed
        writeVramByte(satAddr + 0x10, (byte) 0x56);
        checkSatHashcode(-1871470787);

        //change bytes within the sat cache, sat cache changed
        writeVramRange(satAddr, satAddr + len, (byte) 0x34);
        checkSatHashcode(-1366896639);

        //change sat location, sat cache unchanged
        int satAddr2 = 0xE000;
        vdpProvider.updateRegisterData(MdVdpProvider.VdpRegisterName.SPRITE_TABLE_LOC, satAddr2 >> SPRITE_TABLE_SHIFT);
        checkSatHashcode(-1366896639);

        //change byte within the old sat cache range, sat cache unchanged
        writeVramByte(satAddr + 0x10, (byte) 0x56);
        checkSatHashcode(-1366896639);
    }

    /**
     * Check that when changing the videoMode H40 -> H32, the sat location changes immediately too (due to different masking
     * being applied)
     * TODO fix
     */
//    @Test
    public void testSatCacheAddressMasking2() {
        MdVdpTestUtil.setH40(vdpProvider);
        int regValue = 0x7F;
        //clear mem
        writeVramRange(0xFC00, 0x400, (byte) 0);
        vdpProvider.updateRegisterData(MdVdpProvider.VdpRegisterName.SPRITE_TABLE_LOC, regValue);
        setH32_NoVideoModeUpdate(vdpProvider);
        int satAddr = 0xFFFF_0000 | 0xFE00;
        byte val = 0x55;
        writeVramRange(satAddr, 8, val);
        Assert.assertEquals(val, memoryInterface.getSatCache()[0]);
    }

    private void checkSatHashcode(int hc) {
        Assert.assertEquals(hc, Arrays.hashCode(memoryInterface.getSatCache()));
    }

    private void writeVramRange(int start, int len, byte data) {
        for (int i = start; i < start + len; i++) {
            writeVramByte(i, data);
        }
    }

    private void writeVramByte(int addr, byte b) {
        memoryInterface.writeVideoRamByte(MdVdpProvider.VdpRamType.VRAM, addr, b);
    }

    public static void setH32_NoVideoModeUpdate(MdVdpProvider vdp) {
        int val = vdp.getRegisterData(MODE_4);
        vdp.writeControlPort(CTRL_PORT_MODE_4 | (val & 0x7E));
    }
}
