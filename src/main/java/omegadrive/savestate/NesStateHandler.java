package omegadrive.savestate;

import com.grapeshot.halfnes.NES;
import com.grapeshot.halfnes.state.HalfnesSaveStateHandler;
import omegadrive.util.FileLoader;
import omegadrive.util.Util;

import java.nio.file.Paths;

/**
 * NesStateHandler
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class NesStateHandler implements BaseStateHandler {

    private final static String fileExtension = "n00";
    public static NesStateHandler EMPTY_STATE = new NesStateHandler() {
    };
    private HalfnesSaveStateHandler handler = new HalfnesSaveStateHandler();
    private byte[] stateData;
    private String fileName;
    private Type type;

    public static NesStateHandler createLoadInstance(String fileName) {
        NesStateHandler n = new NesStateHandler();
        n.fileName = handleFileExtension(fileName);
        n.type = Type.LOAD;
        n.stateData = FileLoader.readBinaryFile(Paths.get(n.fileName), fileExtension);
        return n;
    }

    public static NesStateHandler createLoadInstance(String fileName, int[] data) {
        NesStateHandler n = new NesStateHandler();
        n.fileName = handleFileExtension(fileName);
        n.type = Type.LOAD;
        n.stateData = Util.unsignedToByteArray(data);
        return n;
    }

    public static NesStateHandler createSaveInstance(String fileName) {
        NesStateHandler n = new NesStateHandler();
        n.fileName = handleFileExtension(fileName);
        n.type = Type.SAVE;
        return n;
    }

    private static String handleFileExtension(String fileName) {
        return fileName + (!fileName.toLowerCase().contains(".n0") ? "." + fileExtension : "");
    }

    @Override
    public BaseStateHandler.Type getType() {
        return type;
    }

    @Override
    public String getFileName() {
        return fileName;
    }

    @Override
    public byte[] getData() {
        return stateData;
    }

    public void processState(NES nes) {
        switch (type) {
            case LOAD:
                handler.setSaveStateData(nes, stateData);
                break;
            case SAVE:
                stateData = handler.getSaveStateData(nes);
                break;
        }
    }
}
