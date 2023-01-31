/*
 * KonamiMapper
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 21/10/19 13:51
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

package omegadrive.cart.mapper.msx;

import omegadrive.cart.mapper.RomMapper;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import java.util.function.Predicate;

import static omegadrive.cart.mapper.msx.KonamiMapper.*;

/**
 * Konami4 and Konami5
 * Konami5 does not support SCC
 */
public class KonamiMapper extends KonamiMapperImpl {

    public static final int BLOCK_NUM = 32;
    public static final int MAPPER_START_ADDRESS = 0x4000;
    public static final int MAPPER_END_ADDRESS = 0xBFFF;
    public static final int PAGES_8KB = 4;
    private static final KonamiType[] list = KonamiType.values();
    private static final Logger LOG = LogHelper.getLogger(KonamiMapper.class.getSimpleName());

    private KonamiMapper(byte[] rom, KonamiType type) {
        super(rom, type);
    }

    public static RomMapper createMapper(byte[] rom, String type) {
        KonamiType t = getMapperType(type);
        if (t == null) {
            LOG.error("Mapper not supported: {}", type);
            return NO_OP_MAPPER;
        }
        return createMapper(rom, t);
    }

    public static RomMapper createMapper(byte[] rom, KonamiType type) {
        return new KonamiMapperImpl(rom, type);
    }

    public static KonamiType getMapperType(String mapperName) {
        for (KonamiType t : list) {
            if (mapperName.equalsIgnoreCase(t.toString())) {
                return t;
            }
        }
        return null;
    }

    public enum KonamiType {
        KONAMI, //Konami4
        KONAMISCC  //Konami5 (SCC)
    }
}

class KonamiMapperImpl implements RomMapper {

    final static Predicate<Integer> isMapperWriteKonami = add -> add >= 0x6000 && add < 0xC000;
    final static Predicate<Integer> isMapperWriteKonamiScc = add -> add >= 0x5000 && add < 0xB800;
    final Predicate<Integer> isMapperWrite;
    final int pageNum;
    final int pageSize;
    final int readShiftMask;
    final int[] pageBlockMapper;
    final byte[] rom;
    final KonamiMapper.KonamiType type;


    protected KonamiMapperImpl(byte[] rom, KonamiMapper.KonamiType type) {
        this.rom = rom;
        this.type = type;
        this.pageNum = PAGES_8KB;
        this.pageSize = BLOCK_NUM * 1024 / pageNum;
        this.pageBlockMapper = new int[pageNum];
        this.readShiftMask = 0xE000;
        this.isMapperWrite = type == KonamiMapper.KonamiType.KONAMI ? isMapperWriteKonami : isMapperWriteKonamiScc;
    }

    @Override
    public int readData(int addressL, Size size) {
        int res = 0xFF;
        int address = (addressL & 0xFFFF);
        if (address < MAPPER_START_ADDRESS || address > MAPPER_END_ADDRESS) {
            return -1;
        }
        int pagePointer = getPageRead(address);
        if (pagePointer >= 0 && pagePointer < pageNum) {
            int shift = address & readShiftMask;
            int blockPointer = pageBlockMapper[pagePointer];
            int index = (address - shift) + blockPointer * pageSize;
            res = rom[index];
        }
        return res;
    }

    @Override
    public void writeData(int addressL, int data, Size size) {
        if (!isMapperWrite.test(addressL)) {
            return;
        }
        int pagePointer = getPageWrite(addressL);
        if (pagePointer >= 0 && pagePointer < pageNum) {
            int blockPointer = ((data & 0xFF) % BLOCK_NUM);
            pageBlockMapper[pagePointer] = blockPointer;
        }
    }

    private int getPageWrite(int addressL) {
        int address = (addressL & readShiftMask) >> 13;
        return address - 2;
    }

    private int getPageRead(int address) {
        address = (address & readShiftMask) >> 13;
        return address - 2;
    }
}
