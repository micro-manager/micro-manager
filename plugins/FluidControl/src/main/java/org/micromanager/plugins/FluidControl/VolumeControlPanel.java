package org.micromanager.plugins.FluidControl;

import org.micromanager.Studio;

import javax.swing.*;

public class VolumeControlPanel extends JPanel {
    private Studio studio_;
    private Config config_;

    private int nSelected;
    private VolumeControlSubPanel[] panelList;
    private String[] devices_;


    VolumeControlPanel(Studio studio, String[] devices) {
        this.studio_ = studio;
        this.devices_ = devices;

        nSelected = devices.length;
        panelList = new VolumeControlSubPanel[nSelected];
        for (int i = 0; i < nSelected; i++) {
            panelList[i] = new VolumeControlSubPanel(studio_, devices[i]);
            this.add(panelList[i]);
        }
    }
}
