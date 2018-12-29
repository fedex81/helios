package omegadrive.joypad;

//	http://md.squee.co/315-5309
//	http://md.squee.co/Howto:Read_Control_Pads

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * GenJoypad
 *
 * @author Federico Berti
 * <p>
 * Based on genefusto GenJoypad
 * https://github.com/DarkMoe/genefusto
 * @author DarkMoe
 */
public class GenesisJoypad implements JoypadProvider {

    private static Logger LOG = LogManager.getLogger(GenesisJoypad.class.getSimpleName());

    //SGDK needs 0 here, otherwise it is considered a RESET
    long control1 = 0;
    long control2 = 0;
    long control3 = 0;

    int D, U, L, R, A, B, C, S;
    int D2, U2, L2, R2, A2, B2, C2, S2;

    boolean asserted1;
    boolean asserted2;

    public void initialize() {
        D = 1;
        U = 1;
        L = 1;
        R = 1;
        A = 1;
        B = 1;
        C = 1;
        S = 1;

        D2 = 1;
        U2 = 1;
        L2 = 1;
        R2 = 1;
        A2 = 1;
        B2 = 1;
        C2 = 1;
        S2 = 1;

        writeDataRegister1(0x40);
        writeDataRegister2(0x40);
    }

    public void writeDataRegister1(long data) {
        asserted1 = (data & 0x40) == 0;
    }

    public int readDataRegister1() {
        int res;
        if (asserted1) {
            res = (S << 5) | (A << 4) | (D << 1) | (U);    //	 (00SA00DU)
        } else {
            res = 0xC0 | (C << 5) | (B << 4) | (R << 3) | (L << 2) | (D << 1) | (U);    //	 (11CBRLDU)
        }
        return res;
    }

    public void writeDataRegister2(long data) {
        asserted2 = (data & 0x40) == 0;
    }

    public int readDataRegister2() {
        if (asserted2) {
            return (S2 << 5) | (A2 << 4) | (D2 << 1) | (U2);    //	 (00SA00DU)
        } else {
            return 0xC0 | (C2 << 5) | (B2 << 4) | (R2 << 3) | (L2 << 2) | (D2 << 1) | (U2);    //	 (11CBRLDU)
        }
    }

    public int readDataRegister3() {
        return 0x3F;
    }

    private void writeControlCheck(int port, long data) {
        if (data != 0x40) {
            LOG.info("Setting ctrlPort{} to {}", port, Long.toHexString(data));
        }
    }

    public void writeControlRegister1(long data) {
        writeControlCheck(1, data);
        control1 = data;
    }

    public void writeControlRegister2(long data) {
        writeControlCheck(2, data);
        control2 = data;
    }

    public void writeControlRegister3(long data) {
        writeControlCheck(3, data);
        control3 = data;
    }

    @Override
    public void setD(int value) {
        this.D = value;
    }

    @Override
    public void setU(int value) {
        this.U = value;
    }

    @Override
    public void setL(int value) {
        this.L = value;
    }

    @Override
    public void setR(int value) {
        this.R = value;
    }

    @Override
    public void setA(int value) {
        this.A = value;
    }

    @Override
    public void setB(int value) {
        this.B = value;
    }

    @Override
    public void setC(int value) {
        this.C = value;
    }

    @Override
    public void setS(int value) {
        this.S = value;
    }

    @Override
    public void setD2(int value) {
        this.D2 = value;
    }

    @Override
    public void setU2(int value) {
        this.U2 = value;
    }

    @Override
    public void setL2(int value) {
        this.L2 = value;
    }

    @Override
    public void setR2(int value) {
        this.R2 = value;
    }

    @Override
    public void setA2(int value) {
        this.A2 = value;
    }

    @Override
    public void setB2(int value) {
        this.B2 = value;
    }

    @Override
    public void setC2(int value) {
        this.C2 = value;
    }

    @Override
    public void setS2(int value) {
        this.S2 = value;
    }

    public long readControlRegister1() {
        return control1;
    }

    public long readControlRegister2() {
        return control2;
    }

    public long readControlRegister3() {
        return control3;
    }


}
