package omegadrive.sound.persist;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface SoundPersister {

    enum SoundType {
        PSG,
        FM,
        BOTH;
    }

    void persistSound(SoundType type, byte[] output);

    boolean isRecording();

    void stopRecording();

    void startRecording(SoundType soundType);

    default void persistPsg(byte[] output) {
        persistSound(SoundType.PSG, output);
    }

    default void persistFm(byte[] output) {
        persistSound(SoundType.FM, output);
    }

    default void persistMix(byte[] output) {
        persistSound(SoundType.BOTH, output);
    }
}
