package omegadrive.joypad;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface JoypadProvider {

    void initialize();

    int readDataRegister1();

    int readDataRegister2();

    int readDataRegister3();

    long readControlRegister1();

    long readControlRegister2();

    long readControlRegister3();

    void writeDataRegister1(long data);

    void writeDataRegister2(long data);

    void writeControlRegister1(long data);

    void writeControlRegister2(long data);

    void writeControlRegister3(long data);

    void setD(int value);

    void setU(int value);

    void setL(int value);

    void setR(int value);

    void setA(int value);

    void setB(int value);

    void setC(int value);

    void setS(int value);

    void setD2(int value);

    void setU2(int value);

    void setL2(int value);

    void setR2(int value);

    void setA2(int value);

    void setB2(int value);

    void setC2(int value);

    void setS2(int value);
}
