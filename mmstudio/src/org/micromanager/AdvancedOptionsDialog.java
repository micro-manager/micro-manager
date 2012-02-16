package org.micromanager;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;


import org.micromanager.api.AcquisitionEngine;
import org.micromanager.utils.MMDialog;

public class AdvancedOptionsDialog extends MMDialog {

    private static final long serialVersionUID = 1L;
    private AcquisitionEngine acqEng_;
    private JTabbedPane tabbedPane_;
    private JPanel closeButtonPanel_;
    private CustomTimeIntervalsPanel customTimeIntervalsPanel_;

    public AdvancedOptionsDialog(AcquisitionEngine acqEng) {
        super();
        this.setModal(true);
        acqEng_ = acqEng;
        loadPosition(0,0,600,500);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent arg0) {
                savePosition();
            }});
        setTitle("Advanced acquisition options");

        this.setMinimumSize(new Dimension(600,400));

        tabbedPane_ = new JTabbedPane();
        customTimeIntervalsPanel_ = new CustomTimeIntervalsPanel(acqEng_,tabbedPane_);

        tabbedPane_.add("Custom time intervals", customTimeIntervalsPanel_);
//        tabbedPane_.add("More coming soon", new JLabel());
        this.getContentPane().add(tabbedPane_);

        closeButtonPanel_ = new JPanel();
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(new ActionListener() {

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
