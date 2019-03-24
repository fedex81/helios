package omegadrive.memory;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface IMemoryProvider extends IMemoryRam, IMemoryRom {

    void setChecksumRomValue(long value);

    void setRomData(int[] data);

//    default void setRomData(int[] data){
//        byte[] b = new byte[data.length];
//        for (int i = 0; i < data.length; i++) {
//           b[i] = (byte) data[i];
//        }
//        setRomData(b);
//    }
}
