package omegadrive;

import com.google.common.collect.ObjectArrays;
import omegadrive.input.InputProvider;
import omegadrive.system.*;
import omegadrive.ui.EmuFrame;
import omegadrive.ui.GenesisWindow;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;
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

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public class SystemLoader {

    public enum SystemType {
        NONE(""),
        GENESIS("MD"),
        SG_1000("SG"),
        COLECO("CV"),
        MSX("MSX")
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

    public static String[] binaryTypes = Stream.of(
            mdBinaryTypes, sgBinaryTypes, cvBinaryTypes, msxBinaryTypes
    ).flatMap(Stream::of).toArray(String[]::new);

    public static boolean verbose = false;
    public static boolean showFps = false;
    public static boolean headless = false;

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
    }

    private SystemLoader(){
    }

    public static SystemLoader getInstance(){
        if(init.compareAndSet(false, true)){
            init();
            new Exception().printStackTrace();
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
        boolean isGen = Arrays.stream(mdBinaryTypes).anyMatch(file.toString()::endsWith);
        boolean isSg = Arrays.stream(sgBinaryTypes).anyMatch(file.toString()::endsWith);
        boolean isCv = Arrays.stream(cvBinaryTypes).anyMatch(file.toString()::endsWith);
        boolean isMsx = Arrays.stream(msxBinaryTypes).anyMatch(file.toString()::endsWith);
        if(isGen){
            systemProvider = createSystemProvider(SystemType.GENESIS);
        } else if(isSg){
            systemProvider = createSystemProvider(SystemType.SG_1000);
        } else if(isCv){
            systemProvider = createSystemProvider(SystemType.COLECO);
        } else if(isMsx){
            systemProvider = createSystemProvider(SystemType.MSX);
        }
        return systemProvider;
    }

    public SystemProvider createSystemProvider(SystemType system){
        switch (system){
            case GENESIS:
                return Genesis.createNewInstance(emuFrame);
            case COLECO:
                return Coleco.createNewInstance(emuFrame);
            case SG_1000:
                return Sg1000.createNewInstance(emuFrame);
            case MSX:
                return Msx.createNewInstance(emuFrame);
        }
        return null;
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
