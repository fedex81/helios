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

import omegadrive.vdp.gen.VdpColorMapper;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.model.VdpMemoryInterface;
import omegadrive.vdp.model.VdpRenderHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;

public class CramViewer implements UpdatableViewer {

    private static final Logger LOG = LogManager.getLogger(CramViewer.class.getSimpleName());

    private static final int CRAM_ENTRIES = GenesisVdpProvider.VDP_CRAM_SIZE / 2;
    private static final int LABEL_HEIGHT = 200;
    private static final int LABEL_WIDTH = 10;
    private static final int FRAME_HEIGHT = LABEL_HEIGHT + 50;
    private static final int FRAME_WIDTH = LABEL_WIDTH * CRAM_ENTRIES + 50;
    private static final int ROWS = 4;

    private VdpMemoryInterface vdpMemoryInterface;
    private JPanel cramPanel;
    private JFrame cramFrame;
    private VdpColorMapper colorMapper;
    private JPanel[] panelList = new JPanel[CRAM_ENTRIES];

    private CramViewer(VdpMemoryInterface vdpMemoryInterface) {
        this.vdpMemoryInterface = vdpMemoryInterface;
        this.colorMapper = VdpColorMapper.getInstance();
        this.cramFrame = new JFrame();
        this.cramPanel = new JPanel();
        initPanel();
    }

    public static CramViewer createInstance(VdpMemoryInterface vdpMemoryInterface) {
        return new CramViewer(vdpMemoryInterface);
    }

    private void initPanel() {
        SwingUtilities.invokeLater(() -> {
            int labelPerLine = CRAM_ENTRIES / ROWS;
            this.cramPanel = new JPanel(new GridLayout(ROWS + 1, labelPerLine));
            cramPanel.setBackground(Color.GRAY);
            cramPanel.setSize(FRAME_WIDTH - 25, FRAME_HEIGHT - 25);
            cramPanel.add(new JLabel());
            for (int i = 0; i < labelPerLine; i++) {
                JLabel label = new JLabel(Integer.toHexString(i));
                label.setHorizontalAlignment(SwingConstants.CENTER);
                label.setBackground(Color.WHITE);
                label.setForeground(Color.BLACK);
                cramPanel.add(label);
            }
            int k = 0;
            int rowCnt = 0;
            for (int i = 0; i < GenesisVdpProvider.VDP_CRAM_SIZE; i += 2) {
                if (k % labelPerLine == 0) {
                    JLabel label = new JLabel(Integer.toHexString(rowCnt * labelPerLine));
                    label.setHorizontalAlignment(SwingConstants.CENTER);
                    label.setBackground(Color.WHITE);
                    label.setForeground(Color.BLACK);
                    cramPanel.add(label);
                    rowCnt++;
                }
                JPanel cpanel = new JPanel();
                cpanel.setBackground(Color.BLACK);
                cpanel.setForeground(Color.BLACK);
                cpanel.setName("CRAM" + k);
                cpanel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
                cpanel.setMaximumSize(new Dimension(LABEL_WIDTH, LABEL_WIDTH));
                panelList[k] = cpanel;
                cramPanel.add(cpanel);
                k++;
            }
        });
    }

    private void initFrame() {
        initPanel();
        SwingUtilities.invokeLater(() -> {
            this.cramFrame = new JFrame();
            cramFrame.add(cramPanel);
            cramFrame.setMinimumSize(new Dimension(FRAME_WIDTH, FRAME_HEIGHT));
            cramFrame.setTitle("CRAM Viewer");
            cramFrame.pack();
            cramFrame.setVisible(true);
        });
    }

    public JPanel getPanel() {
        return cramPanel;
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

    public static void dumpVram(int[] vram) {
        VdpRenderHandler.TileDataHolder tileDataHolder = new VdpRenderHandler.TileDataHolder();
        for (int i = 0; i < vram.length; i += 2) {
            int tileNameTable = vram[i] << 8 | vram[i + 1];
//            tileDataHolder = getTileData(tileNameTable, tileDataHolder); //TODO
            if (tileDataHolder.tileIndex > 0) {
                System.out.println(Integer.toHexString(i) + "," + tileDataHolder);
                for (int j = 0; j < 64; j++) {
                    if (j > 0 && j % 4 == 0) {
                        System.out.println();
                    }
                    System.out.print(Integer.toHexString(vram[tileDataHolder.tileIndex + j]) + ",");
                }
                System.out.println("\n");
            }
        }
//        for (int i = 0; i < VDP_CRAM_SIZE; i++) {
//            System.out.println(i + "," + getJavaColorValue(i));
//        }
    }
}
