package org.micromanager.plugins.micromanager;

import graphics.scenery.Settings;
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

    public StageLimitsPanel(MMConnection mmcon, MicromanagerWrapper wrapper, Settings msSettings) {

        JPanel stageLimitsPanel = this;
        stageLimitsPanel.setLayout(new MigLayout());

        TitledBorder title;
        title = BorderFactory.createTitledBorder("Stage limits");
        stageLimitsPanel.setBorder(title);

        ArrayList<JTextField> stageLimits = new ArrayList<>(6);

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
                Float value = msSettings.getOrNull("Stage." + dir.toLowerCase(Locale.ROOT) + dim.toUpperCase());
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

                stageLimits.add(valueField);
            }
        }

        JButton applyStageLimitsButton = new JButton("Apply stage limits");
        applyStageLimitsButton.addActionListener(e -> {
            for (JTextField tf : stageLimits) {
                if (MicrosceneryStreamFrame.validFloat(tf)) return;
            }
            msSettings.set("Stage.minX", Float.parseFloat(stageLimits.get(0).getText()));
            msSettings.set("Stage.maxX", Float.parseFloat(stageLimits.get(1).getText()));
            msSettings.set("Stage.minY", Float.parseFloat(stageLimits.get(2).getText()));
            msSettings.set("Stage.maxY", Float.parseFloat(stageLimits.get(3).getText()));
            msSettings.set("Stage.minZ", Float.parseFloat(stageLimits.get(4).getText()));
            msSettings.set("Stage.maxZ", Float.parseFloat(stageLimits.get(5).getText()));
//            System.out.println(""+ Float.parseFloat(stageLimits.get(0).getText()));
//            System.out.println(""+ Float.parseFloat(stageLimits.get(1).getText()));
//            System.out.println(""+ Float.parseFloat(stageLimits.get(2).getText()));
//            System.out.println(""+ Float.parseFloat(stageLimits.get(3).getText()));
//            System.out.println(""+ Float.parseFloat(stageLimits.get(4).getText()));
//            System.out.println(""+ Float.parseFloat(stageLimits.get(5).getText()));
            notAppliedWarningLabel.setText("");
            wrapper.updateHardwareDimensions();
        });
        stageLimitsPanel.add(applyStageLimitsButton, "span 2");
        stageLimitsPanel.add(notAppliedWarningLabel, "wrap");

    }
}
