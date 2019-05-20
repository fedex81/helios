/*
 * CramViewer
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 20/05/19 19:57
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package omegadrive.vdp.util;

import omegadrive.util.Util;
import omegadrive.vdp.gen.GenesisVdpMemoryInterface;
import omegadrive.vdp.gen.VdpColorMapper;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.model.VdpMemoryInterface;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public class CramViewer implements VdpMemoryInterface.ICramViewer {

    private static final Logger LOG = LogManager.getLogger(CramViewer.class.getSimpleName());

    private static final int CRAM_ENTRIES = GenesisVdpProvider.VDP_CRAM_SIZE / 2;
    private static final int LABEL_HEIGHT = 200;
    private static final int LABEL_WIDTH = 10;
    private static final int FRAME_HEIGHT = LABEL_HEIGHT + 50;
    private static final int FRAME_WIDTH = LABEL_WIDTH * CRAM_ENTRIES + 50;
    private static final int ROWS = 4;
    private static final VdpMemoryInterface.ICramViewer NO_OP_VIEWER = () -> {
    };
    private static boolean CRAM_VIEWER_ENABLED;

    private VdpMemoryInterface vdpMemoryInterface;
    private JPanel cramPanel;
    private JFrame cramFrame;
    private VdpColorMapper colorMapper;
    private JPanel[] panelList = new JPanel[CRAM_ENTRIES];

    static {
        CRAM_VIEWER_ENABLED =
                Boolean.valueOf(System.getProperty("md.show.cram.viewer", "true"));
        if (CRAM_VIEWER_ENABLED) {
            LOG.info("Cram viewer enabled");
        }
    }

    public static VdpMemoryInterface.ICramViewer createInstance(VdpMemoryInterface vdpMemoryInterface) {
        return CRAM_VIEWER_ENABLED ? new CramViewer(vdpMemoryInterface) : NO_OP_VIEWER;
    }


    private CramViewer(VdpMemoryInterface vdpMemoryInterface) {
        this.vdpMemoryInterface = vdpMemoryInterface;
        this.colorMapper = VdpColorMapper.getInstance();
        this.cramFrame = new JFrame();
        this.cramPanel = new JPanel();
        init();
    }

    private void init() {
        SwingUtilities.invokeLater(() -> {
            this.cramFrame = new JFrame();
            this.cramPanel = new JPanel(new GridLayout(ROWS, CRAM_ENTRIES));
            cramPanel.setSize(FRAME_WIDTH - 25, FRAME_HEIGHT - 25);
            int k = 0;
            for (int i = 0; i < GenesisVdpProvider.VDP_CRAM_SIZE; i += 2) {
                JPanel cpanel = new JPanel();
                cpanel.setBackground(Color.BLACK);
                cpanel.setForeground(Color.BLACK);
                cpanel.setName("CRAM" + k);
                JLabel cLabelWhite = new JLabel("" + k);
                JLabel cLabelBlack = new JLabel("" + k);
                cLabelWhite.setMaximumSize(new Dimension(LABEL_WIDTH, LABEL_HEIGHT));
                cLabelBlack.setMaximumSize(new Dimension(LABEL_WIDTH, LABEL_HEIGHT));
                cLabelWhite.setForeground(Color.WHITE);
                cLabelBlack.setForeground(Color.BLACK);
                cpanel.add(cLabelBlack);
                cpanel.add(cLabelWhite);
                panelList[k] = cpanel;
                k++;
            }
            Arrays.stream(panelList).forEach(cramPanel::add);
            cramFrame.add(cramPanel);
            cramFrame.setMinimumSize(new Dimension(FRAME_WIDTH, FRAME_HEIGHT));
            cramFrame.setTitle("CRAM Viewer");
            cramFrame.pack();
            cramFrame.setVisible(true);
        });
    }

    @Override
    public void update() {
        SwingUtilities.invokeLater(() -> {
            int k = 0;
            for (int i = 0; i < GenesisVdpProvider.VDP_CRAM_SIZE; i += 2) {
                int cramColor = vdpMemoryInterface.readVideoRamWord(GenesisVdpProvider.VdpRamType.CRAM, i);
                int rgb = colorMapper.getColor(cramColor);
                Color c = new Color(rgb);
                JPanel label = panelList[k];
                label.setBackground(c);
                label.setForeground(c);
                k++;
            }
        });
    }

    static int[] cram = {
            10, 32, 6, 0, 12, 0, 14, 68, 14, 102, 14, 136, 14, 238, 0, 174, 0, 106,
            0, 38, 0, 238, 14, 170, 0, 12, 0, 6, 0, 2, 0, 0, 0, 0, 12, 0, 14, 34,
            14, 68, 14, 102, 14, 136, 14, 238, 10, 170, 8, 136, 6, 102, 4, 68, 2, 72,
            8, 174, 6, 140, 0, 0, 0, 14, 8, 0, 0, 2, 14, 238, 0, 38, 0, 72, 0, 108, 0,
            142, 0, 206, 12, 66, 14, 134, 14, 202, 14, 236, 0, 64, 0, 96, 0, 164, 0,
            232, 12, 130, 10, 2, 12, 66, 14, 134, 14, 202, 14, 236, 14, 238, 14, 172,
            14, 138, 14, 104, 0, 232, 0, 164, 0, 2, 0, 38, 0, 108, 0, 206};

    public static void main(String[] args) {
        VdpMemoryInterface vdpMemoryInterface = GenesisVdpMemoryInterface.createInstance(new int[0], cram, new int[0]);
        Util.sleep(1000);
        int data = vdpMemoryInterface.readCramByte(0);
        vdpMemoryInterface.writeCramByte(0, data);
    }
}
