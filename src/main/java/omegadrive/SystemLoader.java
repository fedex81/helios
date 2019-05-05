/*
 * SystemLoader
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 07/04/19 16:01
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

package omegadrive;

import com.google.common.collect.ObjectArrays;
import omegadrive.input.InputProvider;
import omegadrive.system.*;
import omegadrive.ui.EmuFrame;
import omegadrive.ui.GenesisWindow;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;
import omegadrive.vdp.Engine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.io.FileReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class SystemLoader {

    public enum SystemType {
        NONE(""),
        GENESIS("MD"),
        SG_1000("SG"),
        COLECO("CV"),
        MSX("MSX"),
        SMS("SMS"),
        GG("GG")
        ;

        private String shortName;

        private SystemType(String s){
            this.shortName = s;
        }

        public String getShortName() {
            return shortName;
        }
    }

    private static Logger LOG = LogManager.getLogger(SystemLoader.class.getSimpleName());

    public static final SystemLoader INSTANCE = new SystemLoader();

    private static final String PROPERTIES_FILENAME = "./emu.properties";

    public static String[] mdBinaryTypes = {".md", ".bin"};
    public static String[] sgBinaryTypes = {".sg", ".sc"};
    public static String[] cvBinaryTypes = {".col"};
    public static String[] msxBinaryTypes = {".rom"};
    public static String[] smsBinaryTypes = {".sms"};
    public static String[] ggBinaryTypes = {".gg"};

    public static String[] binaryTypes = Stream.of(
            mdBinaryTypes, sgBinaryTypes, cvBinaryTypes, msxBinaryTypes, smsBinaryTypes, ggBinaryTypes
    ).flatMap(Stream::of).toArray(String[]::new);

    public static boolean verbose = false;
    public static boolean showFps = false;
    public static boolean headless = false;
    public static String biosFolder = ".";
    public static String biosNameMsx1 = "cbios_main_msx1.rom";
    public static String biosNameColeco = "bios_coleco.col";

    private Path romFile;
    protected GenesisWindow emuFrame;
    private SystemProvider systemProvider;

    private static AtomicBoolean init = new AtomicBoolean();

    protected static boolean isHeadless() {
        GraphicsEnvironment ge =
                GraphicsEnvironment.getLocalGraphicsEnvironment();
        return ge.isHeadlessInstance() || headless;
    }

    protected static void loadProperties() {
        try (
                FileReader reader = new FileReader(PROPERTIES_FILENAME)
        ) {
            java.lang.System.getProperties().load(reader);
            java.lang.System.getProperties().store(java.lang.System.out, null);
        } catch (Exception e) {
            LOG.error("Unable to load properties file: " + PROPERTIES_FILENAME);
        }
        verbose = Boolean.valueOf(java.lang.System.getProperty("emu.debug", "false"));
        showFps = Boolean.valueOf(java.lang.System.getProperty("emu.fps", "false"));
        headless = Boolean.valueOf(java.lang.System.getProperty("emu.headless", "false"));
        biosFolder = String.valueOf(java.lang.System.getProperty("bios.folder", biosFolder));
        biosNameMsx1 = String.valueOf(java.lang.System.getProperty("bios.name.msx1", biosNameMsx1));
        biosNameColeco = String.valueOf(java.lang.System.getProperty("bios.name.coleco", biosNameColeco));
    }

    private SystemLoader(){
    }

    public static SystemLoader getInstance(){
        if(init.compareAndSet(false, true)){
            init();
        }
        return INSTANCE;
    }

    public static void main(String[] args) throws Exception {
        init();
        if (args.length > 0) {
            //linux pulseaudio can crash if we start too quickly
            Util.sleep(250);
            String filePath = args[0];
            LOG.info("Launching file at: " + filePath);
            INSTANCE.handleNewRomFile(Paths.get(filePath));
        }
        if (headless) {
            Util.waitForever();
        }
    }

     private static void init() {
         loadProperties();
         InputProvider.bootstrap();
         boolean isHeadless = isHeadless();
         LOG.info("Headless mode: " + isHeadless);
         INSTANCE.setHeadless(isHeadless);
         if (SwingUtilities.isEventDispatchThread()) {
             INSTANCE.createFrame(headless);
         } else {
         try {
             SwingUtilities.invokeAndWait(() -> INSTANCE.createFrame(headless));
         } catch (Exception e) {
             LOG.error(e);
         }
         init.set(true);
     }
     }

    private static void setHeadless(boolean headless) {
        SystemLoader.headless = headless;
    }

    // Create the frame on the event dispatching thread
    protected void createFrame(boolean isHeadless) {
        emuFrame = isHeadless ? GenesisWindow.HEADLESS_INSTANCE : new EmuFrame(getSystemAdapter());
        emuFrame.init();
    }

    public SystemProvider handleNewRomFile(Path file) {
        systemProvider = createSystemProvider(file);
        emuFrame.reloadSystem(systemProvider);
        systemProvider.handleNewRom(file);
        return systemProvider;
    }

    public SystemProvider createSystemProvider(Path file){
        String lowerCaseName = file.toString().toLowerCase();
        boolean isGen = Arrays.stream(mdBinaryTypes).anyMatch(lowerCaseName::endsWith);
        boolean isSg = Arrays.stream(sgBinaryTypes).anyMatch(lowerCaseName::endsWith);
        boolean isCv = Arrays.stream(cvBinaryTypes).anyMatch(lowerCaseName::endsWith);
        boolean isMsx = Arrays.stream(msxBinaryTypes).anyMatch(lowerCaseName::endsWith);
        boolean isSms = Arrays.stream(smsBinaryTypes).anyMatch(lowerCaseName::endsWith);
        boolean isGg = Arrays.stream(ggBinaryTypes).anyMatch(lowerCaseName::endsWith);
        if(isGen){
            systemProvider = Genesis.createNewInstance(emuFrame);
        } else if(isSg){
            systemProvider = Z80BaseSystem.createNewInstance(SystemType.SG_1000, emuFrame);
        } else if(isCv){
            systemProvider = Z80BaseSystem.createNewInstance(SystemType.COLECO, emuFrame);
        } else if(isMsx){
            systemProvider = Z80BaseSystem.createNewInstance(SystemType.MSX, emuFrame);
        } else if(isSms){
            systemProvider = Sms.createNewInstance(SystemType.SMS, emuFrame);
            Engine.setSMS();
        } else if(isGg){
            systemProvider = Sms.createNewInstance(SystemType.GG, emuFrame);
            Engine.setGG();
        }
        if(systemProvider == null){
            LOG.error("Unable to find a system to load: " + file.toAbsolutePath());
        }
        return systemProvider;
    }

    public SystemProvider getSystemProvider() {
        return systemProvider;
    }

    SystemProvider getSystemAdapter(){
        return new SystemProvider() {
            @Override
            public RegionDetector.Region getRegion() {
                return null;
            }

            @Override
            public void renderScreen(int[][] screenData) {

            }

            @Override
            public void handleNewRom(Path file) {
                handleNewRomFile(file);
            }

            @Override
            public void handleCloseRom() {

            }

            @Override
            public void handleCloseApp() {

            }

            @Override
            public void handleLoadState(Path file) {

            }

            @Override
            public boolean isRomRunning() {
                return false;
            }

            @Override
            public boolean isSoundWorking() {
                return false;
            }

            @Override
            public void toggleMute() {

            }

            @Override
            public void toggleSoundRecord() {

            }

            @Override
            public void setFullScreen(boolean value) {

            }

            @Override
            public void setPlayers(int i) {

            }

            @Override
            public void setDebug(boolean value) {

            }

            @Override
            public String getRomName() {
                return null;
            }

            @Override
            public void handleSaveState(Path file) {

            }

            @Override
            public void handlePause() {

            }

            @Override
            public SystemType getSystemType() {
                return SystemType.NONE;
            }
        };
    }
}
