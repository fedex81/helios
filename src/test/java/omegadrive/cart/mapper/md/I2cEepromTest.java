package omegadrive.cart.mapper.md;

import omegadrive.cart.loader.MdRomDbModel.EepromLineMap;
import omegadrive.cart.loader.MdRomDbModel.EepromType;
import omegadrive.util.FileUtil;
import omegadrive.util.Size;
import omegadrive.util.Util;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static omegadrive.cart.loader.MdRomDbModel.EepromType.X24C01;

public class I2cEepromTest {

    public static Path baseFolder = Paths.get(new File(".").getAbsolutePath(),
            "src", "test", "resources", "eeprom");

    //binary files representing the eeprom before and after running the simulation
    public static Path eepromFolder = Paths.get(baseFolder.toAbsolutePath().toString(), "compare");

    private int[] sram;

    private String getFileEepromBefore(String name) {
        return Util.getNameWithoutExtension(name) + ".bf.bin";
    }

    private String getFileEepromAfter(String name) {
        return Util.getNameWithoutExtension(name) + ".af.bin";
    }

    private void setInitialEepromState(I2cEeprom eeprom, Path file) {
        sram = Util.toUnsignedIntArray(FileUtil.readFileSafe(file));
        Assertions.assertEquals(sram.length, eeprom.ctx.spec.sizeMask + 1);
        eeprom.setSram(sram);
    }

    @Test
    public void testXC2401_Sega() {
        testXC2401("wb3_read_empty.dat", EepromLineMap.SEGA);
        testXC2401("wb3_save.dat", EepromLineMap.SEGA);
        testXC2401("wb3_read_save.dat", EepromLineMap.SEGA);
    }

    @Test
    public void testXC2401_EA() {
        testXC2401("jmf93_access01.dat", EepromLineMap.EA);
        testXC2401("jmf93_access02.dat", EepromLineMap.EA);
    }

    private void testXC2401(String fileName, EepromLineMap lineMap) {
        Path p = Paths.get(baseFolder.toAbsolutePath().toString(), fileName);
        Path eepromBefore = Paths.get(eepromFolder.toAbsolutePath().toString(), getFileEepromBefore(fileName));
        Path eepromAfter = Paths.get(eepromFolder.toAbsolutePath().toString(), getFileEepromAfter(fileName));
        Assertions.assertTrue(p.toFile().exists());
        Assertions.assertTrue(eepromBefore.toFile().exists());
        Assertions.assertTrue(eepromAfter.toFile().exists());
        List<String> lines = FileUtil.readFileContent(p);
        Assertions.assertFalse(lines.isEmpty());
        I2cEeprom eeprom = createInstance(X24C01, lineMap);
        setInitialEepromState(eeprom, eepromBefore);
        for (String l : lines) {
            if (l.isEmpty()) {
                continue;
            }
            String[] tokns = l.split(",");
            boolean isWrite = "W".equalsIgnoreCase(tokns[0]);
            int port = Integer.parseInt(tokns[1], 16);
            Size size = Size.BYTE.name().startsWith(tokns[2]) ? Size.BYTE : Size.WORD;
            int data = Integer.parseInt(tokns[3], 16);
            if (isWrite) {
                eeprom.writeEeprom(port, data, size);
            } else {
                int res = eeprom.readEeprom(port, size);
                Assertions.assertEquals(data, res, l);
            }
        }
        int[] exp = Util.toUnsignedIntArray(FileUtil.readFileSafe(eepromAfter));
        Assertions.assertArrayEquals(exp, sram);
    }

    private I2cEeprom createInstance(EepromType type, EepromLineMap lm) {
        I2cEeprom e = new I2cEeprom();
        e.ctx.spec = type;
        e.ctx.lineMap = lm;
        return e;
    }
}