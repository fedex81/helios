package omegadrive.bus.mapper;

import omegadrive.bus.z80.SmsBus;
import omegadrive.memory.IMemoryProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public class SmsMapper {

    public enum Type{NONE, SEGA, CODEM, KOREA}

    private static Type[] list = Type.values();

    private static Logger LOG = LogManager.getLogger(SmsMapper.class);

    private static final boolean verbose = false;

    private static final int[] FRAME_REG_DEFAULT = {0,1,0};

    // 0 -> 0, 1 -> 24, 2 -> 16, 3 -> 8
    private static final int[] bankShiftMap = {0, 24, 16, 8};

    private IMemoryProvider memoryProvider;
    private int mappingControl = 0;
    private int numPages = 2; //32kb default
    private int[] frameReg = new int[FRAME_REG_DEFAULT.length];
    private RomMapper activeMapper = RomMapper.NO_OP_MAPPER;

    private Type currentType = Type.NONE;

    public static SmsMapper createInstance(IMemoryProvider memoryProvider){
        SmsMapper s = new SmsMapper();
        s.memoryProvider = memoryProvider;
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

    private void init(){
        numPages = memoryProvider.getRomSize() >> 14;
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
        LOG.error("Unexpected Z80 memory read: " + Long.toHexString(address));
        return 0xFF;
    }

    class SegaMapper implements RomMapper {

        @Override
        public long readData(long address, Size size) {
            return readDataMapper(address, size);
        }

        @Override
        public void writeData(long addressL, long dataL, Size size) {
            int address = (int) (addressL & SmsBus.RAM_MASK);
            int data = (int) (dataL & 0xFF);
            memoryProvider.writeRamByte(address, data);
            if (addressL >= SmsBus.SEGA_MAPPING_CONTROL_ADDRESS) {
                writeBankData(addressL, data);
            }
        }

        @Override
        public void writeBankData(long addressL, long dataL) {
            int val = (int) (addressL & 3);
            int data = (int) (dataL & 0xFF);
            switch (val){
                case 0:
                    mappingControl = data;
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
            LogHelper.printLevel(LOG, Level.INFO,"writeMappingReg: {} , data: {}", addressL, data, verbose);
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
            boolean isMappingControl = page < 3 && (address % 0x4000) == 0;
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
