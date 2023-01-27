package org.micromanager.plugins.micromanager;

import fromScenery.Settings;
import microscenery.hardware.micromanagerConnection.MMConnection;
import microscenery.hardware.micromanagerConnection.MicromanagerWrapper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Locale;

class StageLimitsPanel extends JPanel {

    private final ArrayList<StageLimitContainer> stageLimits = new ArrayList<>(6);
    private final Settings msSettings;
    // really ugly but prevents the settings update routines to trigger when the values are applied
    private boolean applyingStageLimits = false;

    public StageLimitsPanel(MMConnection mmcon, MicromanagerWrapper wrapper, Settings msSettings) {
        this.msSettings = msSettings;


        JPanel stageLimitsPanel = this;
        stageLimitsPanel.setLayout(new MigLayout());

        TitledBorder title;
        title = BorderFactory.createTitledBorder("Stage limits");
        stageLimitsPanel.setBorder(title);


        JLabel notAppliedWarningLabel = new JLabel();
        notAppliedWarningLabel.setForeground(Color.RED);

        Font bold = new JLabel().getFont();
        bold = bold.deriveFont(bold.getStyle() | Font.BOLD);

        String[] dims = {"X", "Y", "Z"};
        String[] dirs = {"Min", "Max"};
        for (int dimIndex = 0; dimIndex < 3; dimIndex++) {

            String dim = dims[dimIndex];
            JLabel xLabel = new JLabel(dim);
            xLabel.setFont(bold);
            stageLimitsPanel.add(xLabel, "wrap");

            for (String dir : dirs) {
                stageLimitsPanel.add(new JLabel(dir));

                JTextField valueField = new JTextField(10);
                stageLimitsPanel.add(valueField);
                String settingName = "Stage." + dir.toLowerCase(Locale.ROOT) + dim.toUpperCase();
                Float value = msSettings.getOrNull(settingName);
                if (value == null) value = mmcon.getStagePosition().get(dimIndex);
                valueField.setText(value + "");
                valueField.getDocument().addDocumentListener(new DocumentListener() {
                    public void changedUpdate(DocumentEvent e) {
                        displayApplyWarning();
                    }

                    public void removeUpdate(DocumentEvent e) {
                        displayApplyWarning();
                    }

                    public void insertUpdate(DocumentEvent e) {
                        displayApplyWarning();
                    }

                    public void displayApplyWarning() {
                        notAppliedWarningLabel.setText("New limits have not been applied yet.");
                    }
                });

                JButton dirLabel = new JButton("copy stage position");
                int finalDimIndex = dimIndex;
                dirLabel.addActionListener(e -> valueField.setText(mmcon.getStagePosition().get(finalDimIndex) + ""));
                stageLimitsPanel.add(dirLabel, "wrap");

                stageLimits.add(new StageLimitContainer(valueField,settingName));
            }
        }

        JButton applyStageLimitsButton = new JButton("Apply stage limits");
        applyStageLimitsButton.addActionListener(e -> {
            for (StageLimitContainer tf : stageLimits) {
                if (MicrosceneryStreamFrame.validFloat(tf.field)) return;
            }
            for (StageLimitContainer container : stageLimits) {
                applyingStageLimits = true;
                msSettings.set(container.settingName, Float.parseFloat(container.field.getText()));
                applyingStageLimits = false;
            }

            try {
                wrapper.updateHardwareDimensions();
                notAppliedWarningLabel.setText("");
            } catch (IllegalArgumentException exception){
                if(exception.getMessage().equals("Min allowed stage area parameters need to be smaller than max values")){
                    JOptionPane.showMessageDialog(null,
                            "Min allowed stage area parameters need to be smaller than max values",
                            "Invalid Parameters",
                            JOptionPane.ERROR_MESSAGE);
                } else {
                    throw exception;
                }
            }
        });
        stageLimitsPanel.add(applyStageLimitsButton, "span 2");
        stageLimitsPanel.add(notAppliedWarningLabel, "wrap");

    }


    private static class StageLimitContainer{
        JTextField field;
        String settingName;

        public StageLimitContainer(JTextField field, String settingName) {
            this.field = field;
            this.settingName = settingName;
        }
    }

    public void updateValues() {
        if (applyingStageLimits) return;
        for (StageLimitContainer container : stageLimits) {
            Float value = msSettings.getOrNull(container.settingName);
            if (value == null) value = 0f;
            container.field.setText(value + "");
        }
    }
}
