/*
 * GstStateHandler
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 20:41
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

package omegadrive.savestate;

import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.fm.ym2612.nukeykt.IYm3438;
import omegadrive.sound.fm.ym2612.nukeykt.Ym2612Nuke3;
import omegadrive.util.FileLoader;
import omegadrive.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Serializable;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * TODO
 * - handle loading of gs0
 * - gsh: save fm registers too
 */
public class GshStateHandler extends GstStateHandler {

    private static Logger LOG = LogManager.getLogger(GshStateHandler.class.getSimpleName());

    protected GshStateHandler() {
    }

    @Override
    protected void init(String fileNameEx) {
        fileExtension = "gsh";
        MAGIC_WORD = "GSH";
        this.fileName = handleFileExtension(fileNameEx);
        if (type == Type.SAVE) {
            data = new int[GstStateHandler.FILE_SIZE];
            //file type
            data[0] = 'G';
            data[1] = 'S';
            data[2] = 'H';
            //special Genecyst stuff
            data[6] = 0xE0;
            data[7] = 0x40;
        } else {
            data = FileLoader.readBinaryFile(Paths.get(fileName), fileExtension);
        }
    }

    @Override
    public void loadFmState(FmProvider fm) {
        int fmLen = data.length - FILE_SIZE - 4;
        int[] fmDataInt = new int[fmLen];
        System.arraycopy(data, FILE_SIZE + 4, fmDataInt, 0, fmLen);
        byte[] fmData = Util.toByteArray(fmDataInt);
        String fmType = new String(new byte[]{(byte) data[FILE_SIZE], (byte) data[FILE_SIZE + 1],
                (byte) data[FILE_SIZE + 2], (byte) data[FILE_SIZE + 3]});
        System.out.println("FM data type: " + fmType);
        Serializable res = Util.deserializeObject(fmData);
        ((Ym2612Nuke3) fm).setChip((IYm3438.IYm3438_Type) res);
    }


    @Override
    public void saveFm(FmProvider fm) {
        IYm3438.IYm3438_Type chip = ((Ym2612Nuke3) fm).getChip();
        int[] res = Util.toIntArray(Util.serializeObject(chip));
        int end = data.length;
        try {
            data = Arrays.copyOf(data, end + res.length + 4);
            data[end] = 'N';
            data[end + 1] = 'U';
            data[end + 2] = 'K';
            data[end + 3] = 'E';

            System.arraycopy(res, 0, data, end + 4, res.length);
        } catch (Exception e) {
            LOG.error("Unable to save FM data");
        }
    }

    /*
   GSH helios save file
   Range        Size   Description
   -----------  -----  -----------
   00000-00002  3      "GSH"
   00006-00007  2      "\xE0\x40"
   000FA-00112  24     VDP registers
   00112-00191  128    Color RAM
   00192-001E1  80     Vertical scroll RAM
   001E4-003E3  512    YM2612 registers
   00474-02473  8192   Z80 RAM
   02478-12477  65536  68K RAM
   12478-22477  65536  Video RAM

   main 68000 registers
   --------------------
   00080-0009F : D0-D7
   000A0-000BF : A0-A7
   000C8 : PC
   000D0 : SR
   000D2 : USP
   000D6 : SSP

   Z80 registers
   -------------
   00404 : AF
   00408 : BC
   0040C : DE
   00410 : HL
   00414 : IX
   00418 : IY
   0041C : PC
   00420 : SP
   00424 : AF'
   00428 : BC'
   0042C : DE'
   00430 : HL'
   00434 : I
   00435 : Unknow
   00436 : IFF1 = IFF2
   00437 : Unknow
   The 'R' register is not supported.
   Z80 State
   ---------
   00438 : Z80 RESET
   00439 : Z80 BUSREQ
   0043A : Unknow0
   0043B : Unknow
   0043C : Z80 BANK (DWORD)*/
}
