package org.micromanager.plugins.FluidControl;

import org.micromanager.Studio;

import javax.swing.*;

public class PressureControlPanel extends JPanel {
    private Studio studio_;
    private Config config_;

    private int nSelected;
    private PressureControlSubPanel[] panelList;
    private String[] devices_;


    PressureControlPanel(Studio studio, String[] devices) {
        this.studio_ = studio;
        this.devices_ = devices;

        nSelected = devices.length;
        panelList = new PressureControlSubPanel[nSelected];
        for (int i = 0; i < nSelected; i++) {
            panelList[i] = new PressureControlSubPanel(studio_, devices[i]);
            this.add(panelList[i]);
        }
    }

    public void update() {
        for (PressureControlSubPanel panel: panelList) {
            panel.updatePressure();
        }
    }
}
