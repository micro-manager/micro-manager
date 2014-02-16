/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.imaging100x.twophoton;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.prefs.Preferences;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Henry
 */
public class SettingsDialog extends JDialog {

    static public final String DYNAMIC_STITCH = "Realtimestitching";
    static public final String STITCHED_DATA_DIRECTORY = "Stitched data location";
    static public final String FREE_GB__MIN_IN_STITCHED_DATA = "Free GB minimum in stitched data dir";
    static public final String CREATE_IMS_FILE = "Create Imaris file";
    static public final String FILTER_IMS = "Use gaussian filter";
    static public final String FILTER_SIZE = "Gaussian filter width";
    private static int xOverlap_ = 0, yOverlap_ = 0;
    private static int eom1SkipInterval_ = 1;

    public SettingsDialog(final Preferences prefs, final TwoPhotonControl twoP) {
        super();
        this.setModal(true);
        this.setLayout(new BorderLayout());
        JPanel panel = new JPanel();
        this.add(panel, BorderLayout.CENTER);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        final JCheckBox realTimeStitch = new JCheckBox("Activate dynamic stitching");
        row2.add(realTimeStitch);
        realTimeStitch.setSelected(prefs.getBoolean(DYNAMIC_STITCH, false));
        panel.add(row2);
        realTimeStitch.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!realTimeStitch.isSelected()) {
                    ReportingUtils.showMessage("Restart Micro-Manager for changes to take effect");
                } else {
                    twoP.acitvateDynamicStitching();
                }
            }
        });

        JPanel rowSquaw = new JPanel(new FlowLayout(FlowLayout.LEFT));
        final JSpinner xOverlap = new JSpinner(new SpinnerNumberModel(xOverlap_, 0, 500, 1));
        final JSpinner yOverlap = new JSpinner(new SpinnerNumberModel(yOverlap_, 0, 500, 1));
        rowSquaw.add(new JLabel("            Pixel overlap x: "));
        rowSquaw.add(xOverlap);
        rowSquaw.add(new JLabel("  y:"));
        rowSquaw.add(yOverlap);
        xOverlap.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                xOverlap_ = (Integer) xOverlap.getValue();
            }
        });
        yOverlap.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                yOverlap_ = (Integer) yOverlap.getValue();
            }
        });
        panel.add(rowSquaw);


        JPanel rowskis = new JPanel(new FlowLayout(FlowLayout.LEFT));
        final JCheckBox saveIMS = new JCheckBox("Create Imaris file during acquisition");
        rowskis.add(saveIMS);
        saveIMS.setSelected(prefs.getBoolean(CREATE_IMS_FILE, false));
        panel.add(rowskis);


        JPanel rowsqueezy = new JPanel(new FlowLayout(FlowLayout.LEFT));
        final JCheckBox filter = new JCheckBox("Gaussian filter imaris data");
        rowsqueezy.add(new JLabel("         "));
        rowsqueezy.add(filter);
        filter.setSelected(prefs.getBoolean(FILTER_IMS, false));
        final JSpinner filterSize = new JSpinner(new SpinnerNumberModel(prefs.getDouble(
                FILTER_SIZE, 2), 0.1, 50, 0.01));
        rowsqueezy.add(filterSize);
        rowsqueezy.add(new JLabel(" pixels"));


        panel.add(rowsqueezy);


        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel dir = new JLabel("            Saving location (DO NOT CHANGE)");
        row3.add(dir);
        final JTextField location = new JTextField(60);
        location.setText(prefs.get(STITCHED_DATA_DIRECTORY, ""));
        row3.add(location);
        panel.add(row3);

        JPanel row4 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        final JSpinner freeGig = new JSpinner(new SpinnerNumberModel(prefs.getInt(
                FREE_GB__MIN_IN_STITCHED_DATA, 100), 10, 10000, 1));
        row4.add(new JLabel("            Minimum number of free GB to maintain in saving directory at application startup: "));
        row4.add(freeGig);
        panel.add(row4);



        JPanel rowYourBoat = new JPanel(new FlowLayout(FlowLayout.LEFT));
        final JSpinner activeEOM = new JSpinner(new SpinnerNumberModel(
                eom1SkipInterval_, 1, 100, 1));
        rowYourBoat.add(new JLabel("EOM1 active every "));
        rowYourBoat.add(activeEOM);
        rowYourBoat.add(new JLabel(" frames"));
        activeEOM.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                eom1SkipInterval_ = (Integer) activeEOM.getValue();
            }
        });
        panel.add(rowYourBoat);


        JPanel lastRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(lastRow);
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                SettingsDialog.this.setVisible(false);
            }
        });
        lastRow.add(closeButton);

        final ActionListener saveSettings = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
//            prefs.putBoolean(DYNAMIC_STITCH, realTimeStitch.isSelected());
                prefs.put(STITCHED_DATA_DIRECTORY, location.getText());
                prefs.putInt(FREE_GB__MIN_IN_STITCHED_DATA, (Integer) freeGig.getValue());
                prefs.putBoolean(CREATE_IMS_FILE, saveIMS.isSelected());
                prefs.putBoolean(FILTER_IMS, filter.isSelected());
                prefs.putDouble(FILTER_SIZE, (Double) filterSize.getValue());
            }
        };
        realTimeStitch.addActionListener(saveSettings);
        location.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                saveSettings.actionPerformed(null);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                saveSettings.actionPerformed(null);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                saveSettings.actionPerformed(null);
            }
        });
        freeGig.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                saveSettings.actionPerformed(null);
            }
        });
        filterSize.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                saveSettings.actionPerformed(null);
            }
        });
        saveIMS.addActionListener(saveSettings);
        filter.addActionListener(saveSettings);

        this.pack();
        this.setTitle("Settings");
        this.setLocationRelativeTo(null);
        this.setVisible(true);
    }

    public static int getXOverlap() {
        return xOverlap_;
    }

    public static int getYOverlap() {
        return yOverlap_;
    }
    
    public static int getEOM1SkipInterval() {
        return eom1SkipInterval_;
    }
}
