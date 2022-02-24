package omegadrive.util;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private Map<Integer, Integer> maskMap;

    @Before
    public void loadFile() {
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
}
