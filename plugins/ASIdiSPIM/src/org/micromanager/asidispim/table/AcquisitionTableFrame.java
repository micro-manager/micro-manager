package org.micromanager.asidispim.table;

import javax.swing.JFrame;

import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.AcquisitionPanel;
import org.micromanager.asidispim.Data.Icons;

import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
public class AcquisitionTableFrame extends JFrame {

    public AcquisitionTableFrame(final ScriptInterface gui, final AcquisitionPanel acqPanel) {
        setTitle("Acquistion Playlist");
        setIconImage(Icons.MICROSCOPE.getImage());
        setLocation(200, 200);
        setSize(800, 700);
        setLayout(new MigLayout("", "", ""));
    }
    
}
