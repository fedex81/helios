package omegadrive.vdp.util;

import omegadrive.vdp.md.VdpInterruptHandler;
import omegadrive.vdp.model.GenesisVdpProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class VdpPortAccessLogger {

    public static final VdpPortAccessLogger NO_LOGGER = new VdpPortAccessLogger(null);
    private final VdpInterruptHandler handler;
    private final List<VdpWriteContext> writes;

    public VdpPortAccessLogger(VdpInterruptHandler handler) {
        this.handler = handler;
        this.writes = new ArrayList<>();
    }

    public void logVdpWrite(GenesisVdpProvider.VdpPortType type, int value) {
        VdpWriteContext v = new VdpWriteContext();
        v.hcInternal = handler.gethCounterInternal();
        v.hcExternal = handler.getHCounterExternal();
        v.vcInternal = handler.getvCounterInternal();
        v.vcExternal = handler.getVCounterExternal();
        v.value = value;
        v.portType = type;
        writes.add(v);
    }

    public String getWritesAsString() {
        return writes.stream().map(VdpWriteContext::toShortString).collect(Collectors.joining("\n"));
    }

    public List<VdpWriteContext> getWrites() {
        return writes;
    }

    public void reset() {
        writes.clear();
    }

    public static class VdpWriteContext {
        public int hcInternal;
        public int vcInternal;
        public int hcExternal;
        public int vcExternal;
        public int value;
        public GenesisVdpProvider.VdpPortType portType;

        public static VdpWriteContext parseShortString(String s) {
            String[] tk = s.split(",");
            VdpWriteContext v = new VdpWriteContext();
            v.hcExternal = Integer.parseInt(tk[0]);
            v.vcExternal = Integer.parseInt(tk[1]);
            v.hcInternal = Integer.parseInt(tk[2]);
            v.vcInternal = Integer.parseInt(tk[3]);
            v.portType = GenesisVdpProvider.VdpPortType.valueOf(tk[4]);
            v.value = Integer.parseInt(tk[5]);
            return v;
        }

        public String toShortString() {
            return hcExternal + "," + vcExternal + "," +
                    hcInternal + "," + vcInternal + "," + portType + "," + value;
        }

    }
}
