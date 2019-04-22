/*
 * Ascii8Mapper
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 14/04/19 18:17
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

package omegadrive.bus.mapper;

import omegadrive.util.Size;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static omegadrive.bus.mapper.MsxAsciiMapper.*;

/**
 * MSX mapper Ascii8/16
 *
 * TODO 16kb mapper
 *
 */
public class MsxAsciiMapper extends AsciiMapperImpl {

    private static Logger LOG = LogManager.getLogger(MsxAsciiMapper.class.getSimpleName());

    public static final int BLOCK_NUM = 32;
    public static final int MAPPER_START_ADDRESS = 0x4000;
    public static final int MAPPER_END_ADDRESS = 0xBFFF;

    public static int PAGES_8KB = 4;
    public static int PAGES_16KB = 2;

    private MsxAsciiMapper(int[] rom, AsciiType type) {
        super(rom, type);
    }

    public static RomMapper createMapper(int[] rom, String type){
        AsciiType t = getMapperType(type);
        if(t == null){
            LOG.error("Mapper not supported: " + type);
            return NO_OP_MAPPER;
        }
        return createMapper(rom, t);
    }

    public static RomMapper createMapper(int[] rom, AsciiType type){
        return new AsciiMapperImpl(rom, type);
    }

    private static AsciiType[] list = AsciiType.values();

    //TODO fix ASCII16
    public enum AsciiType { ASCII8,
        ASCII16
    }

    public static AsciiType getMapperType(String mapperName){
        for (AsciiType t : list) {
            if(mapperName.equalsIgnoreCase(t.toString())){
                return t;
            }
        }
        return null;
    }
}

class AsciiMapperImpl implements RomMapper {

    int pageNum;
    int pageSize;
    int readShiftMask;
    int[] pageBlockMapper;
    int[] rom;
    AsciiType type;

    protected AsciiMapperImpl(int[] rom, AsciiType type){
        this.rom = rom;
        this.type = type;
        this.pageNum = type == AsciiType.ASCII8 ? PAGES_8KB : PAGES_16KB;
        this.pageSize = BLOCK_NUM*1024/pageNum;
        this.pageBlockMapper = new int[pageNum];
        this.readShiftMask = type == AsciiType.ASCII8 ? 0xE000 : 0x8000;
    }

    @Override
    public long readData(long addressL, Size size) {
        int res = 0xFF;
        int address = (int) (addressL & 0xFFFF);
        if(address < MAPPER_START_ADDRESS || address > MAPPER_END_ADDRESS){
            return -1;
        }
        int pagePointer = getPageRead(address);
        if(pagePointer >= 0 && pagePointer < pageNum) {
            int shift = address & readShiftMask;
            int blockPointer = pageBlockMapper[pagePointer];
            int index = (address - shift) + blockPointer * pageSize;
            res = rom[index];
        }
        return res;
    }

    @Override
    public void writeData(long addressL, long data, Size size) {
        if(addressL > 0x8000 || addressL < 0x6000){
            return;
        }
        int pagePointer = getPageWrite(addressL);
        if(pagePointer >= 0 && pagePointer < pageNum) {
            int blockPointer = (int) ((data & 0xFF) % BLOCK_NUM);
            pageBlockMapper[pagePointer] = blockPointer;
        }
    }

    private int getPageWrite(long addressL){
        int address = (int) (addressL & 0x7FFF);
        return type == AsciiType.ASCII8 ? ((address & 0x7800) >> 11) & 3 : ((address & 0x7000) >> 12) & 1;
    }

    private int getPageRead(int address){
        return type == AsciiType.ASCII8 ? (address & 0x8000) >> 14 | (address & 0x2000) >> 13 : (address & 0x8000) >> 15;
    }
}
