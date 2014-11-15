package org.micromanager.dialogs;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;



import org.micromanager.acquisition.AcquisitionEngine;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.MMDialog;

/*
 * This class provides a dialog available from the "Advanced" button
 * on the AcqConrolDlg (MDA setup window). It allows users to set custom
 * time intervals: i.e., nonuniform timing for a series of frames.
 */

public class AdvancedOptionsDialog extends MMDialog {

    private static final long serialVersionUID = 1L;
    private final AcquisitionEngine acqEng_;
    private final JTabbedPane tabbedPane_;
    private final JPanel closeButtonPanel_;
    private final CustomTimeIntervalsPanel customTimeIntervalsPanel_;

    public AdvancedOptionsDialog(AcquisitionEngine acqEng, ScriptInterface gui) {
        super();
        this.setModal(true);
        acqEng_ = acqEng;
        loadPosition(0,0,600,500);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent arg0) {
                savePosition();
            }});
        setTitle("Advanced acquisition options");

        this.setMinimumSize(new Dimension(600,400));

        tabbedPane_ = new JTabbedPane();
        customTimeIntervalsPanel_ = new CustomTimeIntervalsPanel(acqEng_,tabbedPane_, gui);

        tabbedPane_.add("Custom time intervals", customTimeIntervalsPanel_);
//        tabbedPane_.add("More coming soon", new JLabel());
        this.getContentPane().add(tabbedPane_);

        closeButtonPanel_ = new JPanel();
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                close();
            }
        });
        closeButtonPanel_.add(closeButton);
        this.getContentPane().add(closeButtonPanel_, BorderLayout.PAGE_END);
        
    }

    @Override
    public void setVisible(boolean b) {
        updatePanels();
        super.setVisible(b);
    }
    
    //make sure that all panels display data currently in acqEng upon becoming visible
    private void updatePanels() {
        customTimeIntervalsPanel_.syncCheckBoxFromAcqEng();
        customTimeIntervalsPanel_.syncIntervalsFromAcqEng();
    }
    
    private void close() {
        this.setVisible(false);
    }
}
