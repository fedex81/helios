package omegadrive.vdp;

import omegadrive.SystemLoader;
import omegadrive.input.InputProvider;
import omegadrive.joypad.JoypadProvider;
import omegadrive.joypad.JoypadProvider.JoypadButton;
import omegadrive.memory.IMemoryProvider;
import omegadrive.system.SysUtil;
import omegadrive.system.SystemProvider;
import omegadrive.util.Size;
import omegadrive.util.Util;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;

import static omegadrive.joypad.JoypadProvider.JoypadAction;
import static omegadrive.joypad.JoypadProvider.JoypadAction.PRESSED;
import static omegadrive.joypad.JoypadProvider.JoypadAction.RELEASED;
import static omegadrive.joypad.JoypadProvider.JoypadButton.A;
import static omegadrive.system.SystemProvider.SystemEvent.CLOSE_APP;
import static omegadrive.system.SystemProvider.SystemEvent.CLOSE_ROM;

/**
 * VdpFifoTesting
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
@Ignore
public class VdpFifoTesting {

    private static final int SUCCESS_TEST_RAM_LOCATION = 0xFF08;
    private static final int SUCCESS_BASELINE = 80;
    public static Path resFolder = Paths.get(new File(".").getAbsolutePath(),
            "src", "test", "resources");
    private static String fileName = "VDPFIFOTesting.zip";
    private static int BOOT_DELAY_MS = 500;
    private static int RUN_DELAY_MS = 15_000;

    static {
        System.setProperty("helios.headless", "true");
        System.setProperty("helios.enable.sound", "false");
        System.setProperty("helios.fullSpeed", "true");
    }

    private static void buttonEvent(JoypadProvider joypadProvider, JoypadButton button, JoypadAction action) {
//        System.out.println(joypadProvider.getState(InputProvider.PlayerNumber.P1));
        joypadProvider.setButtonAction(InputProvider.PlayerNumber.P1, button, action);
//        System.out.println(joypadProvider.getState(InputProvider.PlayerNumber.P1));
    }

    private static void waitUntilRunning(SystemProvider system) {
        long delay = 0;
        do {
            Util.sleep(BOOT_DELAY_MS);
            delay += BOOT_DELAY_MS;
        } while (!system.isRomRunning() && delay < RUN_DELAY_MS);
        Assert.assertTrue("Unable to run the system", system.isRomRunning());
    }

    private static <T> T getProvider(SystemProvider systemProvider, String fieldName) {
        T provider = null;
        try {
            Field f = systemProvider.getClass().getSuperclass().getDeclaredField(fieldName);
            f.setAccessible(true);
            provider = (T) f.get(systemProvider);
            Assert.assertNotNull(provider);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return provider;
    }

    @Test
    public void testVdpFifoTestingSuccessRate() {
        Path file = Paths.get(resFolder.toAbsolutePath().toString(), fileName);
        Assert.assertNotNull(file);
        SystemLoader systemLoader = SystemLoader.getInstance();
        SystemProvider system = systemLoader.handleNewRomFile(SysUtil.RomSpec.of(file));
        waitUntilRunning(system);
        JoypadProvider joypadProvider = getProvider(system, "joypad");
        IMemoryProvider memoryProvider = getProvider(system, "memory");

        long totalDelay = 0;
        long passTest = 0;
        if (system.isRomRunning()) {
            do {
                if (passTest < 10) { //first screen has 9 tests, then we need to press 'A'
                    buttonEvent(joypadProvider, A, PRESSED);
                }
                Util.sleep(BOOT_DELAY_MS);
                if (passTest < 10) {
                    buttonEvent(joypadProvider, A, RELEASED);
                }
                totalDelay += BOOT_DELAY_MS;
                passTest = Util.readData(memoryProvider.getRamData(), SUCCESS_TEST_RAM_LOCATION, Size.WORD);
                System.out.println("MS: " + totalDelay + ", PASS: " + passTest);
            } while (passTest < SUCCESS_BASELINE && totalDelay < RUN_DELAY_MS);
            system.handleSystemEvent(CLOSE_ROM, null);
            system.handleSystemEvent(CLOSE_APP, null);
        }
        Assert.assertTrue("Number of test passed is less than baseline: "
                + passTest + " < " + SUCCESS_BASELINE, passTest >= SUCCESS_BASELINE);
    }
}