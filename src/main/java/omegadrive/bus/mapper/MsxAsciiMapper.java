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

import java.util.stream.IntStream;

import static omegadrive.bus.mapper.MsxAsciiMapper.*;

/**
 * MSX mapper Ascii8/16
 *
 * TODO 16kb mapper
 *
 */
public class MsxAsciiMapper extends AsciiMapperImpl {

    public static final int BLOCK_NUM = 32;
    public static final int MAPPER_START_ADDRESS = 0x4000;
    public static final int MAPPER_END_ADDRESS = 0xBFFF;

    public static int PAGES_8KB = 4;
    public static int PAGES_16KB = 4;

    private MsxAsciiMapper(int[] rom, AsciiType type) {
        super(rom, type);
    }

    public static RomMapper createMapper(int[] rom, AsciiType type){
        return new AsciiMapperImpl(rom, type);
    }

    public enum AsciiType { kb8, kb16 }
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
        this.pageNum = type == AsciiType.kb8 ? PAGES_8KB : PAGES_16KB;
        this.pageSize = BLOCK_NUM*1024/pageNum;
        this.pageBlockMapper = new int[pageNum];
        this.readShiftMask = type == AsciiType.kb8 ? 0xE000 : 0x8000;
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
        return pageNum == 4 ? ((address & 0x7800) >> 11) & 3 : ((address & 0x7000) >> 12) & 1;
    }

    private int getPageRead(int address){
        return pageNum == 4 ? (address & 0x8000) >> 14 | (address & 0x2000) >> 13 : (address & 0x8000) >> 15;
    }
}
