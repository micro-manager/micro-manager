package org.micromanager.plugins.FluidControl;

import net.miginfocom.swing.MigLayout;

import javax.swing.*;

public class AboutFrame extends JFrame {

    AboutFrame() {
        super("About");
        super.setLayout(new MigLayout("fill, insets 2, gap 2, flowx"));
        super.setResizable(false);
        super.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        this.add(new JLabel("This plugin is build by Lars Kool, Institut Pierre-Gilles de Gennes, Paris."), "wrap");
        this.add(new JLabel("It automatically loads all 'PressurePump' and 'Volumetric pump devices. You can select which devices"), "wrap");
        this.add(new JLabel("are shown by (un)checking the corresponding controllers in the 'Menu->Configure' menu"));
        this.pack();
    }
}
