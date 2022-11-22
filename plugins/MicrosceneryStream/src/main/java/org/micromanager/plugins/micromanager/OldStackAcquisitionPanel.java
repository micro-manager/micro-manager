package org.micromanager.plugins.micromanager;

import graphics.scenery.Settings;
import microscenery.hardware.micromanagerConnection.MicromanagerWrapper;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import javax.swing.*;
import javax.swing.border.TitledBorder;

public class OldStackAcquisitionPanel extends JPanel {

    private final Settings msSettings;
    private final MicromanagerWrapper micromanagerWrapper;

    private final JTextField minZText_;
    private final JTextField maxZText_;
    private final JTextField stepsText_;
    private final JLabel stepSizeLabel_;
    private final JTextField refreshTimeText_;
    private final JLabel timesLabel;

    OldStackAcquisitionPanel(Settings msSettings, Studio studio, MicromanagerWrapper micromanagerWrapper){
        super();

        this.msSettings = msSettings;
        this.micromanagerWrapper = micromanagerWrapper;

        super.setLayout(new MigLayout());

        TitledBorder title;
        title = BorderFactory.createTitledBorder("Old style stack acquisition");
        this.setBorder(title);

        super.add(new JLabel("Min Z (μm): "));
        minZText_ = new JTextField(10);
        minZText_.setText("0");
        super.add(minZText_, "");

        super.add(new JLabel("Max Z (μm): "));
        maxZText_ = new JTextField(10);
        maxZText_.setText("100");
        super.add(maxZText_, "wrap");


        super.add(new JLabel("Steps: "));
        stepsText_ = new JTextField(10);
        stepsText_.setText("100");
        super.add(stepsText_, "");

        super.add(new JLabel("Step size (μm): "));
        stepSizeLabel_ = new JLabel("0");
        super.add(stepSizeLabel_, "wrap");

        { // Z settings
            Double minZ = msSettings.get("MMConnection.minZ", 0.0f).doubleValue();
            Double maxZ = msSettings.get("MMConnection.maxZ", 10.0f).doubleValue();
            Integer steps = msSettings.get("MMConnection.slices", 10);
            minZText_.setText(minZ.toString());
            maxZText_.setText(maxZ.toString());
            stepsText_.setText(steps.toString());
            stepSizeLabel_.setText(((maxZ - minZ) / steps) + "");
        }

        super.add(new JLabel("Refresh Time (ms): "));
        refreshTimeText_ = new JTextField(String.valueOf(micromanagerWrapper.getTimeBetweenUpdates()),10);
        super.add(refreshTimeText_, "");

        super.add(new JLabel("Acq time: "));
        timesLabel = new JLabel("uninitalized");
        super.add(timesLabel, "wrap");

        JButton copyFromAcqEngButton = new JButton("Copy from AcqEng");
        copyFromAcqEngButton.addActionListener(e -> {
            double min = studio.acquisitions().getAcquisitionSettings().sliceZBottomUm();
            double max = studio.acquisitions().getAcquisitionSettings().sliceZTopUm();
            double stepsSize = studio.acquisitions().getAcquisitionSettings().sliceZStepUm();

            if (min > max) {
                // swat min and max (academic version)
                min = min + max;
                max = min - max;
                min = min - max;
            }

            minZText_.setText(Double.toString(min));
            maxZText_.setText(Double.toString(max));
            double steps = (max - min) / stepsSize +1;
            stepsText_.setText(((Integer) (int) steps).toString());

            updateParams();
        });
        super.add(copyFromAcqEngButton);

        JButton applyButton = new JButton("Apply Params");
        applyButton.addActionListener(e -> updateParams());
        super.add(applyButton,"wrap");

        final Timer timer = new Timer(500, null);
        //ActionListener listener = e -> timesLabel.setText("c:" + cvss.getMmConnection().getMeanCopyTime() + " s:" + cvss.getMmConnection().getMeanSnapTime());
        //timer.addActionListener(listener);
        //timer.start();


        /*
        JButton sendButton = new JButton("Start Imaging");
        sendButton.addActionListener(e -> cvss.start());
        super.add(sendButton);

        JButton stopButton = new JButton("Stop Imaging");
        stopButton.addActionListener(e -> cvss.pause());
        super.add(stopButton, "wrap");
*/
    }

    private void updateParams() {
        try {
            Double minZ = Double.parseDouble(minZText_.getText());
            Double maxZ = Double.parseDouble(maxZText_.getText());
            int steps = Integer.parseInt(stepsText_.getText());
            double stepSize = (maxZ - minZ) / steps;
            if (stepSize <= 0) {
                JOptionPane.showMessageDialog(null, "Max Z has to be lager than Min Z");
                return;
            }
            stepSizeLabel_.setText(stepSize + "");

            int updateTime = Integer.parseInt(refreshTimeText_.getText());

            msSettings.set("MMConnection.minZ", minZ.floatValue());
            msSettings.set("MMConnection.maxZ", maxZ.floatValue());
            msSettings.set("MMConnection.slices", steps);
            msSettings.set("MMConnection.TimeBetweenStackAcquisition", updateTime);
            micromanagerWrapper.setTimeBetweenUpdates(updateTime);
        } catch (NumberFormatException exc) {
            JOptionPane.showMessageDialog(null, "Values could not be parsed. Max and Min Z need to be a floating point number and steps an integer. ");
        }
    }

}
