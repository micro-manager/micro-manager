package org.micromanager.internal.dialogs;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.micromanager.Studio;
import org.micromanager.acquisition.internal.AcquisitionEngine;
import org.micromanager.internal.utils.MMDialog;

/**
 * This class provides a dialog available from the "Advanced" button
 * on the AcqConrolDlg (MDA setup window) in the timepoints panel. It allows
 * users to set custom time intervals, i.e. nonuniform timing for a series of
 * frames.
 */
public final class CustomTimesDialog extends MMDialog {

    private static final long serialVersionUID = 1L;
    private final AcquisitionEngine acqEng_;
    private final JPanel closeButtonPanel_;
    private final CustomTimeIntervalsPanel customTimeIntervalsPanel_;

    public CustomTimesDialog(AcquisitionEngine acqEng, Studio gui) {
        super("custom timepoints configuration");
        this.setModal(true);
        acqEng_ = acqEng;
        loadPosition(0,0,600,500);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent arg0) {
                savePosition();
            }});
        setTitle("Custom Timepoints Configuration");

        this.setMinimumSize(new Dimension(600,400));

        customTimeIntervalsPanel_ = new CustomTimeIntervalsPanel(acqEng_, gui,
              this);
        getContentPane().add(customTimeIntervalsPanel_);

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
