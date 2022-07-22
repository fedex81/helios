/*
 * SmsMapper
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 25/10/19 16:41
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

package omegadrive.cart.mapper.sms;

import omegadrive.SystemLoader;
import omegadrive.bus.z80.SmsBus;
import omegadrive.cart.mapper.BackupMemoryMapper;
import omegadrive.cart.mapper.RomMapper;
import omegadrive.memory.IMemoryProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import java.util.Arrays;

public class SmsMapper {

    public enum Type{NONE, SEGA, CODEM, KOREA}

    private static final Type[] list = Type.values();

    private static final Logger LOG = LogHelper.getLogger(SmsMapper.class);

    private static final boolean verbose = false;

    private static final int DEFAULT_SRAM_SIZE = 0x4000; //16kb

    private static final int[] FRAME_REG_DEFAULT = {0,1,0};

    // 0 -> 0, 1 -> 24, 2 -> 16, 3 -> 8
    private static final int[] bankShiftMap = {0, 24, 16, 8};

    private IMemoryProvider memoryProvider;
    private int mappingControl = 0;
    private int numPages = 2; //32kb default
    private int[] frameReg = new int[FRAME_REG_DEFAULT.length];
    private RomMapper activeMapper = RomMapper.NO_OP_MAPPER;

    private Type currentType = Type.NONE;
    private static final String sramFileType = "srm";
    private String smsRomName;

    public static SmsMapper createInstance(String romName, IMemoryProvider memoryProvider) {
        SmsMapper s = new SmsMapper();
        s.memoryProvider = memoryProvider;
        s.smsRomName = romName;
        s.init();
        return s;
    }

    public RomMapper setupRomMapper(String name, RomMapper current){
        Type type = Type.SEGA;
        for (Type t : list) {
            if(name.equalsIgnoreCase(t.name())){
                type = t;
            }
        }
        return setupRomMapper(type, current);
    }

    public RomMapper setupRomMapper(Type type, RomMapper current){
        if(currentType != Type.NONE){
            if(currentType != type){
                LOG.error("Unable to change mapper from {} to {}", currentType, type);
            }
            return current;
        }
        currentType = type;
        switch (type){
            case SEGA:
                activeMapper = new SegaMapper();
                break;
            case CODEM:
                activeMapper = new CodeMapper();
                break;
            case KOREA:
                activeMapper = new KoreaMapper();
                break;
            default:
                LOG.error("Invalid mapper type: {}", type);
        }
        LOG.info("Mapper set to: {}", currentType);
        return activeMapper;
    }

    public int getMapperControl() {
        return mappingControl;
    }

    public int[] getFrameReg() {
        return frameReg;
    }

    private void init(){
        numPages = Math.max(1, memoryProvider.getRomSize() >> 14);
        frameReg = Arrays.copyOf(FRAME_REG_DEFAULT, FRAME_REG_DEFAULT.length);
    }

    public long readDataMapper(long addressL, Size size) {
        int address = (int) (addressL & 0xFFFF);
        if (size != Size.BYTE) {
            LOG.error("Unexpected read, addr : {} , size: {}", address, size);
            return 0xFF;
        }
        int page = (address >> 14);
        if(page < FRAME_REG_DEFAULT.length) { //rom
            int block16k = frameReg[page] << 14;
            int newAddr = block16k + (address & 0x3FFF);
            return memoryProvider.readRomByte(newAddr);
        } else if (address >= SmsBus.RAM_START && address <= SmsBus.RAM_END) { //ram
            address &= SmsBus.RAM_SIZE - 1;
            return memoryProvider.readRamByte(address);
        }
        LOG.error("Unexpected Z80 memory read: {}", Long.toHexString(address));
        return 0xFF;
    }


    class SegaMapper extends BackupMemoryMapper implements RomMapper {

        //This feature is not known to be used by any software.
        //Ys need this
        private final boolean sramWriteEnable = true;

        private boolean sramSlot2Enable = false;

        private SegaMapper() {
            super(SystemLoader.SystemType.SMS, sramFileType, smsRomName, DEFAULT_SRAM_SIZE);
        }

        @Override
        public long readData(long address, Size size) {
//            LogHelper.printLevel(LOG, Level.INFO,"readData: {}", address, verbose);
            if (sramSlot2Enable) {
                return readSramDataMaybe(address, size);
            }
            return readDataMapper(address, size);
        }

        @Override
        public void writeData(long addressL, long dataL, Size size) {
//            LogHelper.printLevel(LOG, Level.INFO,"writeData: {} , data: {}", addressL, dataL, verbose);
            if (sramSlot2Enable && sramWriteEnable) {
                if (writeSramDataMaybe(addressL, dataL, size)) {
                    return;
                }
            }
            int address = (int) (addressL & SmsBus.RAM_MASK);
            int data = (int) (dataL & 0xFF);
            memoryProvider.writeRamByte(address, data);
            if (addressL >= SmsBus.SEGA_MAPPING_CONTROL_ADDRESS) {
                writeBankData(addressL, data);
            }
        }

        private long readSramDataMaybe(long addressL, Size size) {
            int address = (int) (addressL & 0xFFFF);
            int page = address >> 14;
            if (sramSlot2Enable && page == 2) {
                return sram[address & 0x3FFF];
            }
            return readDataMapper(addressL, size);
        }

        private boolean writeSramDataMaybe(long addressL, long dataL, Size size) {
            int address = (int) (addressL & 0xFFFF);
            int page = address >> 14;
            if (sramSlot2Enable && page == 2) {
                sram[address & 0x3FFF] = (int) (dataL & 0xFF);
                return true;
            }
            return false;
        }

        /**
         * Write to a paging register
         * <p>
         * $FFFC - Control register
         * <p>
         * D7 : 1= /GWR disabled (write protect), 0= /GWR enabled (write enable)
         * D4 : 1= SRAM mapped to $C000-$FFFF (*1)
         * D3 : 1= SRAM mapped to $8000-$BFFF, 0= ROM mapped to $8000-$BFFF
         * D2 : SRAM banking; BA14 state when $8000-$BFFF is accessed (1= high, 0= low)
         * D1 : Bank shift, bit 1
         * D0 : Bank shift, bit 0
         */
        @Override
        public void writeBankData(long addressL, long dataL) {
            int val = (int) (addressL & 3);
            int data = (int) (dataL & 0xFF);
            switch (val){
                case 0:
                    if (mappingControl != data) {
                        //LOG.debug("Mapping control: {}", data);
                        mappingControl = data;
                        handleSramState(data);
                    }
                    break;
                case 1:
                case 2:
                case 3:
                    int frameRegNum = val - 1;
                    int bankShift = bankShiftMap[mappingControl & 3];
                    data = (data + bankShift) % numPages;
                    frameReg[frameRegNum] = data;
                    break;
            }
            if (verbose) LOG.info("writeMappingReg: {} , data: {}", addressL, data);
        }

        private void handleSramState(int data) {
            //This feature is not known to be used by any software.
//            sramWriteEnable = (data & 0x80) == 0;
            sramSlot2Enable = (data & 8) > 0;
            if (sramSlot2Enable) {
                initBackupFileIfNecessary();
            }
        }

        @Override
        public void closeRom() {
            writeFile();
        }
    }

    class CodeMapper implements RomMapper {

        @Override
        public long readData(long address, Size size) {
            return readDataMapper(address, size);
        }

        @Override
        public void writeData(long addressL, long dataL, Size size) {
            int address = (int) (addressL & 0xFFFF);
            int page = address >> 14;
            boolean isMappingControl = page < 3 && (address % SmsBus.CODEM_MAPPING_BASE_ADDRESS) == 0;
            if(isMappingControl){
                writeBankData(page, dataL);
            } else {
                memoryProvider.writeRamByte(address & SmsBus.RAM_MASK, (int) (dataL & 0xFF));
            }
        }

        @Override
        public void writeBankData(long page, long data) {
            frameReg[(int) page] = (int) (data & 0xFF);
        }
    }

    class KoreaMapper implements RomMapper {

        @Override
        public long readData(long address, Size size) {
            return readDataMapper(address, size);
        }

        @Override
        public void writeData(long addressL, long dataL, Size size) {
            int address = (int) (addressL & 0xFFFF);
            boolean isMappingControl = address == SmsBus.KOREA_MAPPING_CONTROL_ADDRESS;
            if(isMappingControl){
                writeBankData(addressL, dataL);
            } else {
                memoryProvider.writeRamByte(address & SmsBus.RAM_MASK, (int) (dataL & 0xFF));
            }
        }

        @Override
        public void writeBankData(long addressL, long dataL) {
            frameReg[2] = (int) (dataL & 0xFF);
        }
    }
}
