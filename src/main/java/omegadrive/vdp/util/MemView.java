package omegadrive.vdp.util;

import com.google.common.base.Ascii;
import omegadrive.Device;
import omegadrive.bus.model.MdMainBusProvider;
import omegadrive.cpu.z80.Z80Provider;
import omegadrive.memory.ReadableByteMemory;
import omegadrive.util.FileUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.Util;
import omegadrive.vdp.model.VdpMemoryInterface;
import org.slf4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static omegadrive.bus.model.MdMainBusProvider.ADDRESS_RAM_MAP_START;
import static omegadrive.bus.model.MdZ80BusProvider.END_RAM;
import static omegadrive.memory.MemoryProvider.M68K_RAM_SIZE;
import static omegadrive.vdp.model.MdVdpProvider.*;
import static omegadrive.vdp.util.MemView.MemViewOwner.*;

/**
 * VdpDebugView
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public class MemView implements Device, UpdatableViewer {

    private static final Logger LOG = LogHelper.getLogger(MemView.class.getSimpleName());

    public static final MemView NO_MEMVIEW = new MemView() {
        @Override
        public void update() {
        }

        @Override
        public void init() {
        }
    };

    protected static final MemViewData[] mdMemViewData = MdMemViewType.values();

    private JFrame frame;
    private JPanel panel;
    private JScrollPane scrollPane;
    private JTextArea textArea;
    private JComboBox<MemViewData> listComp;

    private volatile byte[] data;
    private AtomicReference<MemViewData> currentViewRef = new AtomicReference<>(MdMemViewType.values()[0]);
    private final Map<MemViewOwner, BiFunction<MemViewData, Integer, Integer>> readerMap;
    private final StringBuilder sb = new StringBuilder();

    private final MemViewData[] memViewData;

    public enum MemViewOwner {
        SH2, M68K, Z80, MD_VDP, SH2_WORD, MCD_SUB_CPU;
    }

    public interface MemViewData {
        int getStart();

        int getEnd();

        default int getBusBaseAddress() {
            return 0;
        }

        MemViewOwner getOwner();

        default VdpRamType getVdpRamType() {
            return null;
        }
    }

    public enum MdMemViewType implements MemViewData {
        MD_VDP_VRAM(MD_VDP, 0, VDP_VRAM_SIZE, VdpRamType.VRAM),
        MD_VDP_VSRAM(MD_VDP, 0, VDP_VSRAM_SIZE, VdpRamType.VSRAM),
        MD_VDP_CRAM(MD_VDP, 0, VDP_CRAM_SIZE, VdpRamType.CRAM),
        M68K_SDRAM(M68K, 0, M68K_RAM_SIZE, ADDRESS_RAM_MAP_START),
        Z80_RAM(Z80, 0, END_RAM / 2),
        ;

        private final int start, end;
        private final MemViewOwner owner;

        private final int busBaseAddress;

        private final VdpRamType vdpRamType;

        MdMemViewType(MemViewOwner c, int s, int e, int base) {
            this(c, s, e, null, base);
        }
        MdMemViewType(MemViewOwner c, int s, int e) {
            this(c, s, e, null, 0);
        }

        MdMemViewType(MemViewOwner c, int s, int e, VdpRamType v) {
            this(c, s, e, v, 0);
        }

        MdMemViewType(MemViewOwner c, int s, int e, VdpRamType v, int baseAddr) {
            start = s;
            end = e;
            owner = c;
            vdpRamType = v;
            busBaseAddress = baseAddr;
        }

        @Override
        public int getStart() {
            return start;
        }

        @Override
        public int getEnd() {
            return end;
        }

        @Override
        public int getBusBaseAddress() {
            return busBaseAddress;
        }

        @Override
        public MemViewOwner getOwner() {
            return owner;
        }

        @Override
        public VdpRamType getVdpRamType() {
            return vdpRamType;
        }
    }

    public static class MdVdpReadableMem {
        private final byte[] vram, vsram, cram;

        private MdVdpReadableMem(VdpMemoryInterface memory) {
            vram = memory.getVram().array();
            vsram = memory.getVsram().array();
            cram = memory.getCram().array();
        }

        public byte read(MemViewData m, int a) {
            return switch (m.getVdpRamType()) {
                case VRAM -> vram[a];
                case CRAM -> cram[a];
                case VSRAM -> vsram[a];
            };
        }
    }

    public static UpdatableViewer createInstance(MdMainBusProvider m, VdpMemoryInterface vdpMem) {
        return createInstance(mdMemViewData, m, null, vdpMem);
    }

    public static UpdatableViewer createInstance(MemViewData[] memViewData, MdMainBusProvider m,
                                                 ReadableByteMemory s32x, VdpMemoryInterface vdpMem) {
        return VdpDebugView.DEBUG_VIEWER_ENABLED ? new MemView(memViewData, m, s32x, vdpMem) : NO_MEMVIEW;
    }

    private MemView() {
        this(mdMemViewData, null, null, null);
    }


    protected MemView(MemViewData[] memViewData, MdMainBusProvider m, ReadableByteMemory bus, VdpMemoryInterface vdpMem) {
        this.memViewData = memViewData;
        if (!VdpDebugView.DEBUG_VIEWER_ENABLED || m == null) {
            readerMap = Collections.emptyMap();
            return;
        }
        init();
        Z80Provider z80 = m.getBusDeviceIfAny(Z80Provider.class).orElseThrow();
        ReadableByteMemory z80b = z80.getZ80BusProvider();
        MdVdpReadableMem mdVdpMem = new MdVdpReadableMem(vdpMem);
        readerMap = Map.of(
                MCD_SUB_CPU, (v, i) -> bus.read(i, Size.BYTE),
                SH2, (v, i) -> bus.read(i, Size.BYTE),
                M68K, (v, i) -> m.read(v.getBusBaseAddress() + i, Size.BYTE),
                Z80, (v, i) -> z80b.read(i, Size.BYTE),
                MD_VDP, (v, i) -> (int) mdVdpMem.read(v, i),
                SH2_WORD, (v, i) -> bus.read(i, Size.WORD)
        );
        data = new byte[0];
    }

    @Override
    public void init() {
        SwingUtilities.invokeLater(() -> {
            this.frame = new JFrame();
            this.panel = new JPanel();
            this.panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBackground(Color.GRAY);
            buildPanel();
            frame.setTitle("MD Memory Viewer");
            frame.pack();
            frame.setVisible(true);
        });
    }

    private void buildPanel() {
        frame.remove(panel);
        frame.invalidate();

        textArea = new JTextArea(0x1000, 16 * 2);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        textArea.setMinimumSize(new Dimension(600, 600));
        textArea.setEditable(false);
        scrollPane = new JScrollPane(textArea);
        listComp = new JComboBox<>(memViewData);
        listComp.addActionListener(this::updateSelected);
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.X_AXIS));
        JButton refreshBtn = new JButton("Refresh");
        JButton exportBtn = new JButton("Export");
        refreshBtn.addActionListener(this::updateUser);
        exportBtn.addActionListener(this::export);
        topPanel.add(listComp);
        topPanel.add(exportBtn);
        topPanel.add(refreshBtn);
        panel.add(topPanel);
        panel.add(scrollPane);
        panel.setSize(new Dimension(615, 615));
        frame.add(panel);
        frame.setMinimumSize(panel.getSize());
        frame.pack();
    }

    private void export(ActionEvent actionEvent) {
        MemViewData mvd = currentViewRef.get();
        byte[] local = Arrays.copyOf(data, mvd.getEnd() - mvd.getStart());
        String name = "MemView_" + mvd + "_" + System.currentTimeMillis() + ".dat";
        Path p = Path.of(".", name);
        FileUtil.writeFileSafe(p, local);
        LOG.info("Exported to: {}", p.toAbsolutePath());
    }

    private int cnt = 0;
    private AtomicInteger qLen = new AtomicInteger(0);

    public void update() {
        cnt++;
        if ((cnt & 0x4) == 0) {
            return;
        }
        int res = qLen.incrementAndGet();
        if (res > 1) {
//            System.out.println("Too slow: " + res);
            qLen.decrementAndGet();
            return;
        }
        cnt = 0;
        updateNow();
    }

    private void updateNow() {
        MemViewData current = currentViewRef.get();
        BiFunction<MemViewData, Integer, Integer> readerFn = readerMap.get(current.getOwner());
        int len = current.getEnd() - current.getStart();
        if (len > data.length) {
            data = new byte[len];
        }
        doMemoryRead(current, len, readerFn);
        Util.executorService.submit(Util.wrapRunnableEx(() -> updateFromMemory(current.getStart(), current.getEnd())));
    }

    private void updateSelected(ActionEvent e) {
        currentViewRef.set((MemViewData) listComp.getSelectedItem());
    }

    private void updateUser(ActionEvent e) {
        updateNow();
    }

    private static final int BYTES_PER_LINE = 0x10;
    private void updateFromMemory(int start, int end) {
        fillFormattedString(sb, data, start, end);
        textArea.setText(sb.toString());
        sb.setLength(0);
        qLen.decrementAndGet();
    }

    public static void fillFormattedString(StringBuilder sb, byte[] data, int start, int end) {
        try {
            HexFormat hf = HexFormat.of().withSuffix(" ");
            sb.append(String.format("%8x", start)).append(": ");
            int len = end - start;
            for (int i = start; i < end; i += BYTES_PER_LINE) {
                int slen = Math.min(len, BYTES_PER_LINE);
                hf.formatHex(sb, data, i, i + slen).append("  ");
                for (int j = i; j < i + slen; j++) {
                    sb.append(toAsciiChar(data[j])).append(" ");
                }
                if ((i - start) + BYTES_PER_LINE < len) {
                    sb.append("\n").append(String.format("%8x", i + BYTES_PER_LINE)).append(": ");
                }
            }
        } catch (Exception e) {
            LOG.error("Error", e);
            e.printStackTrace();
        }
    }

    protected void doMemoryRead(MemViewData current, int len, BiFunction<MemViewData, Integer, Integer> readerFn) {
        final int start = current.getStart();
        for (int i = 0; i < len; i++) {
            data[i] = (byte) readerFn.apply(current, start + i).intValue();
        }
    }

    protected void doMemoryRead_WordBE(MemViewData current, int len) {
        assert current.getOwner() == SH2;
        final int start = current.getStart();
        for (int i = 0; i < len; i += 2) {
            int w = readerMap.get(SH2_WORD).apply(current, start + i).intValue();
            Util.writeData(data, i, w, Size.WORD);
        }
    }

    private static char toAsciiChar(int val) {
        return val >= Ascii.SPACE && val < Ascii.MAX ? (char) val : '.';
    }

    @Override
    public void reset() {
        if (frame != null) {
            frame.setVisible(false);
            frame.dispose();
        }
    }
}