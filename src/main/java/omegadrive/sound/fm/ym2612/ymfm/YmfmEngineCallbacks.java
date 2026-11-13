package omegadrive.sound.fm.ym2612.ymfm;

// BSD 3-Clause License
//
// Copyright (c) 2021, Aaron Giles
public interface YmfmEngineCallbacks {

    void engineTimerExpired(int tnum);

    void engineCheckInterrupts();

    void engineModeWrite(int data);

}
