/*
 * NesSavestateTest
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 24/10/19 18:49
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

package omegadrive.save;

import com.google.common.collect.ImmutableMap;
import com.grapeshot.halfnes.CPU;
import com.grapeshot.halfnes.CPURAM;
import com.grapeshot.halfnes.PPU;
import com.grapeshot.halfnes.ROMLoader;
import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;
import com.grapeshot.halfnes.mappers.MapperHelper;
import com.grapeshot.halfnes.state.HalfnesSaveStateHandler;
import omegadrive.SystemLoader;
import omegadrive.util.FileUtil;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static com.grapeshot.halfnes.state.HalfnesSaveStateHandler.Type.LOAD;
import static com.grapeshot.halfnes.state.HalfnesSaveStateHandler.Type.SAVE;
import static omegadrive.SystemLoader.SystemType.NES;

public class NesSavestateTest extends BaseSavestateTest {

    public static Path saveStateFolder = Paths.get(baseSaveStateFolder.toAbsolutePath().toString(), "nes");
    private SystemLoader.SystemType systemType = NES;
    //TODO should detect the mapper used for each savestate -> needs to store it in the savestate?
    private static final Map<String, Integer> saveStateToMapper = ImmutableMap.<String, Integer>builder()
            .put("smb_82.n00", 0)  //NromMapper
            .put("smb_win.n00", 0)
            .put("cvj_easy_lev07.n00", 24) //VRC6Mapper
            .put("cvj_easy_lev10.n00", 24)
            .put("cvj_lev04.n00", 24)
            .put("contra_lev2.n00", 2) //UnromMapper
            .build();
    private int INVALID_MAPPER = -2;


    @Test
    public void testLoadAndSave() throws IOException {
        testLoadAndSave(saveStateFolder, ".n0");
    }

    private Mapper createMapper(Path saveFile) {
        String fileName = saveFile.toAbsolutePath().toString();
        int mapperNum = saveStateToMapper.getOrDefault(saveFile.getFileName().toString(), INVALID_MAPPER);
        ROMLoader l = new TestRomLoader(fileName, mapperNum);
        Mapper mapper = null;
        try {
            mapper = MapperHelper.getCorrectMapper(l);
            mapper.setLoader(l);
            mapper.loadrom();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
        return mapper;
    }

    @Override
    protected void testLoadSaveInternal(Path saveFile) {
        ByteBuffer buffer = ByteBuffer.wrap(FileUtil.readBinaryFile(saveFile));
        Mapper mapper = createMapper(saveFile);
        CPURAM cpuram = new CPURAM(mapper);
        CPU cpu = new CPU(cpuram);
        PPU ppu = new PPU(mapper);

        HalfnesSaveStateHandler loadHandler = new HalfnesSaveStateHandler();
        loadHandler.setBuf(buffer);
        loadHandler.processState(LOAD, cpu, ppu, null, cpuram);
        byte[] loadData = buffer.array();

        ByteBuffer buffer2 = ByteBuffer.allocate(buffer.capacity());
        HalfnesSaveStateHandler saveHandler = new HalfnesSaveStateHandler();
        saveHandler.setBuf(buffer2);
        saveHandler.processState(SAVE, cpu, ppu, null, cpuram);
        byte[] saveData = buffer2.array();
        //TODO ppu.openbus
        saveData[83] = loadData[83];
        //TODO
        Assert.assertArrayEquals(loadData, saveData);

        HalfnesSaveStateHandler loadHandler2 = new HalfnesSaveStateHandler();
        ByteBuffer buffer3 = ByteBuffer.wrap(saveData);
        loadHandler2.setBuf(buffer3);
        loadHandler2.processState(LOAD, cpu, ppu, null, cpuram);
        byte[] loadData2 = buffer.array();
        Assert.assertArrayEquals(loadData2, saveData);
    }

    static class TestRomLoader extends ROMLoader {

        public TestRomLoader(String filename, int mapperNum) {
            super(filename);
            this.mappertype = mapperNum;
            this.tvtype = Mapper.TVType.NTSC;
            this.scrolltype = Mapper.MirrorType.FOUR_SCREEN_MIRROR;
        }

        @Override
        public int[] load(int size, int offset) {
            return new int[0];
        }

        @Override
        public void parseHeader() throws BadMapperException {
        }
    }
}
