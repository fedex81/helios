package omegadrive.ssp16;

import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.ssp16.Ssp16Types.Ssp1601_t;
import org.junit.Assert;
import org.junit.Test;

import static omegadrive.ssp16.Ssp16Types.Cart;
import static omegadrive.ssp16.Ssp16Types.Svp_t;

public class Ssp16Test {

    static Ssp16Impl createSvp(IMemoryProvider memoryProvider) {
        Cart svpCart = new Cart();
        Ssp1601_t sspCtx = new Ssp1601_t();
        Svp_t svpCtx = new Svp_t(sspCtx);
        Ssp16Impl ssp16 = Ssp16Impl.createInstance(sspCtx, svpCtx, svpCart);
        ssp16.ssp1601_reset(sspCtx);
        return ssp16;
    }

    @Test
    public void testCMPA_Zero() {
        Ssp16Impl ssp16 = createSvp(MemoryProvider.NO_MEMORY);
        int ra32 = 0xFFFF_0000;
        long val = 0xFFFF;

        ssp16.rA32.v = ra32;
        ssp16.rST.h = 0;
        // rA32 - (val << 16)
        ssp16.OP_CMPA(val);
        //rA32 unchanged
        Assert.assertEquals(ra32, ssp16.rA32.v);
        //zero flag is set
        Assert.assertTrue((ssp16.rST.h & Ssp16Impl.SSP_FLAG_Z) > 0);
    }
}
