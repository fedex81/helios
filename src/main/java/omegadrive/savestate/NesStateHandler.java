package omegadrive.savestate;

import com.grapeshot.halfnes.NES;
import com.grapeshot.halfnes.state.HalfnesSaveStateHandler;
import omegadrive.util.FileUtil;

import java.nio.ByteBuffer;
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
    private final HalfnesSaveStateHandler handler = new HalfnesSaveStateHandler();
    private byte[] stateData;
    private String fileName;
    private Type type;
    private NES nes;

    public static NesStateHandler createInstance(String fileName, Type type) {
        return type == Type.LOAD ? createLoadInstance(fileName) : createSaveInstance(fileName);
    }

    private static NesStateHandler createLoadInstance(String fileName) {
        NesStateHandler n = new NesStateHandler();
        n.fileName = handleFileExtension(fileName);
        n.type = Type.LOAD;
        n.stateData = FileUtil.readBinaryFile(Paths.get(n.fileName), fileExtension);
        return n;
    }

    private static NesStateHandler createSaveInstance(String fileName) {
        NesStateHandler n = new NesStateHandler();
        n.fileName = handleFileExtension(fileName);
        n.type = Type.SAVE;
        return n;
    }

    public void setNes(NES nes) {
        this.nes = nes;
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
    public ByteBuffer getDataBuffer() {
        return ByteBuffer.wrap(stateData);
    }

    @Override
    public byte[] getData() {
        return stateData;
    }

    @Override
    public void processState() {
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