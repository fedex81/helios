package mcd;

import mcd.util.McdWramCell;
import omegadrive.util.FileUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class McdVramMappingTest extends McdRegTestBase {


    /**
     * 0x20_0000 - 0x23_FFFF
     * - write to
     * 0x20_0000 - 0x21_FFFF
     * - data converted to cells and stored at
     * 0x22_0000 - 0x23_FFFF
     * <p>
     * 1 cell is 8x8 pixels = 64/2 = 32 bytes
     * Width is 512 px, 64 cells fixed
     * 512 * 256 dots = 0x10_000 bytes, 32 cells, cell data starts at 0x22_0000
     * 512 * 128 dots = 0x8_000 bytes,  16 cells, cell data starts at 0x23_0000
     * 512 * 64 dots = 0x4_000 bytes, 8 cells, cell data starts at 0x23_8000
     * 512 * 32 dots = 0x2_000 bytes, 4 cells type 0, cell data starts at 0x23_C000
     * 512 * 32 dots = 0x2_000 bytes, 4 cells type 1, cell data starts at 0x23_E000
     */
    static Path testFilePath = Paths.get("src/test/resources/megacd", "wram_cell_render_mappings.bin");

    //4 bytes key + 4 bytes value
    private static SortedMap<Integer, Integer> mappings = new TreeMap<>();


    @BeforeAll
    public static void loadFile() {
        byte[] b = FileUtil.readFileSafe(testFilePath);
        assert b.length > 0;
        ByteBuffer bb = ByteBuffer.wrap(b);
        do {
            mappings.put(bb.getInt(), bb.getInt());
        } while (bb.position() < bb.capacity() - 1);
    }

    @Test
    public void testMappings() {
        for (var entry : mappings.entrySet()) {
//            System.out.println(th(entry.getKey()) + "," + th(entry.getValue()));
            Assertions.assertEquals(entry.getValue(), McdWramCell.linearCellMap[entry.getKey()]);
        }
    }
}
