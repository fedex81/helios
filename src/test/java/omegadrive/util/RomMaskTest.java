package omegadrive.util;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class RomMaskTest {

    public static Path resFolder = Paths.get(new File(".").getAbsolutePath(),
            "src", "test", "resources");
    public static String datFileName = "rom_mask.dat";
    public static Path datFile;

    // <size>:<mask>
    private static Map<Integer, Integer> maskMap;

    @BeforeClass
    public static void loadFile() {
        datFile = resFolder.resolve(datFileName);
        List<String> lines = FileUtil.readFileContent(datFile);
        maskMap = new HashMap<>();
        lines.stream().forEach(l -> {
            String[] tk = l.split(",");
            maskMap.put(Integer.parseInt(tk[0], 16), Integer.parseInt(tk[1], 16));
        });
        System.out.println("Entries loaded: " + maskMap.size());
    }

    @Test
    public void testRomMask() {
        for (Map.Entry<Integer, Integer> entry : maskMap.entrySet()) {
            int mask = Util.getRomMask(entry.getKey());
            if (mask != entry.getValue()) {
                String msg = "Mismatch on [" + th(entry.getKey()) + "," + th(entry.getValue()) + "], mask: " + th(mask);
                System.out.println(msg);
                Assert.fail(msg);
            }
        }
    }

    @Test
    public void testRomPadding() {
        int romVal = 0x22;
        int padVal = 0xFF;
        for (Map.Entry<Integer, Integer> entry : maskMap.entrySet()) {
            int romSize = entry.getKey();
            int mask = entry.getValue();
            int[] baseRom = new int[romSize];
            Arrays.fill(baseRom, romVal);
            RomHolder h = new RomHolder(baseRom);
            int[] rom = h.data;
            int size = h.size;
            boolean isPadded = size > romSize;
//            System.out.println(th(entry.getKey()) + "," + th(entry.getValue()) + "," + th(size) + "," + isPadded);

            int b1 = (int) Util.readDataMask(rom, Size.BYTE, size - 1, mask);
            Assert.assertEquals(isPadded ? padVal : romVal, b1);
            int b2 = (int) Util.readDataMask(rom, Size.BYTE, size, mask); //this maps to address 0
            Assert.assertEquals(romVal, b2);
            int w1 = (int) Util.readDataMask(rom, Size.WORD, size - 1, mask);
            Assert.assertEquals(isPadded ? 0xFF22 : 0x2222, w1);
            int w2 = (int) Util.readDataMask(rom, Size.WORD, size, mask);
            Assert.assertEquals(0x2222, w2);
            int ln = (int) Util.readDataMask(rom, Size.LONG, size, mask);
            Assert.assertEquals(0x22222222, ln);
            if (size - romSize <= 4) { //TODO corner case
                continue;
            }
            int w3 = (int) Util.readDataMask(rom, Size.WORD, size - 2, mask);
            Assert.assertEquals(isPadded ? 0xFFFF : 0x2222, w3);
            int l0 = (int) Util.readDataMask(rom, Size.LONG, size - 4, mask);
            Assert.assertEquals(isPadded ? 0xFFFFFFFF : 0x22222222, l0);
            int l1 = (int) Util.readDataMask(rom, Size.LONG, size - 3, mask);
            Assert.assertEquals(isPadded ? 0xFFFFFF22 : 0x22222222, l1);
            int l2 = (int) Util.readDataMask(rom, Size.LONG, size - 2, mask);
            Assert.assertEquals(isPadded ? 0xFFFF2222 : 0x22222222, l2);
            int l3 = (int) Util.readDataMask(rom, Size.LONG, size - 1, mask);
            Assert.assertEquals(isPadded ? 0xFF222222 : 0x22222222, l3);
        }
    }
}