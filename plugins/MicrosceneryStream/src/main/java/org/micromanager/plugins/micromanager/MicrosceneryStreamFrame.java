/**
 * ExampleFrame.java
 * <p>
 * This module shows an example of creating a GUI (Graphical User Interface).
 * There are many ways to do this in Java; this particular example uses the
 * MigLayout layout manager, which has extensive documentation online.
 * <p>
 * <p>
 * Nico Stuurman, copyright UCSF, 2012, 2015
 * <p>
 * LICENSE: This file is distributed under the BSD license. License text is
 * included with the source distribution.
 * <p>
 * This file is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.
 * <p>
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 */
package org.micromanager.plugins.micromanager;

import fromScenery.Settings;
import fromScenery.SettingsEditor;
import kotlin.Unit;
import microscenery.hardware.micromanagerConnection.MMCoreConnector;
import microscenery.hardware.micromanagerConnection.MicromanagerWrapper;
import microscenery.network.ControlSignalsClient;
import microscenery.network.RemoteMicroscopeServer;
import microscenery.signals.*;
import mmcorej.DeviceType;
import net.miginfocom.swing.MigLayout;
import org.joml.Vector2i;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.Studio;
import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.internal.utils.WindowPositioning;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

public class MicrosceneryStreamFrame extends JFrame implements ProcessorConfigurator {

    @SuppressWarnings("FieldCanBeLocal")
    private final String version = "fesh stage limits 2";

    private final JLabel statusLabel_;
    private final JLabel portLabel_;
    private final JLabel connectionsLabel_;
    private final JLabel dimensionsLabel_;

    private final StageLimitsPanel stageLimitsPanel;
    private final AblationPanel ablationPanel;

    private final RemoteMicroscopeServer server;
    private final Settings msSettings;
    private final MicromanagerWrapper micromanagerWrapper;


    public MicrosceneryStreamFrame(Studio studio, MicrosceneryContext msContext, MicrosceneryStream plugin) {
        super("Microscenery Stream Plugin");
        MMCoreConnector mmCon = msContext.mmCon;
        micromanagerWrapper = msContext.micromanagerWrapper;
        server = msContext.server;
        msSettings  = msContext.msSettings;

        
        // loopBackConnection
        new ControlSignalsClient(msContext.zContext,server.getBasePort(),"localhost", Collections.singletonList(this::updateLabels));

        // -- start GUI --
        this.setLayout(new MigLayout("fill","","align top"));
        this.setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("/org/micromanager/icons/microscope.gif")));
        this.setLocation(100, 100);
        WindowPositioning.setUpLocationMemory(this, this.getClass(), null);

        // -- misc container --
        JPanel miscContainer = new JPanel(new MigLayout());
        miscContainer.setBorder(BorderFactory.createTitledBorder("General"));

        miscContainer.add(new JLabel("Version:"));
        miscContainer.add(new JLabel(version), "wrap");

        miscContainer.add(new JLabel("Status: "));
        statusLabel_ = new JLabel("uninitalized");
        miscContainer.add(statusLabel_, "wrap");

        miscContainer.add(new JLabel("Ports: "));
        portLabel_ = new JLabel(server.getBasePort() + "" + server.getStatus().getDataPorts().stream().map(p -> " ," + p).collect(Collectors.joining()));
        miscContainer.add(portLabel_, "wrap");

        miscContainer.add(new JLabel("Clients: "));
        connectionsLabel_ = new JLabel("0");
        miscContainer.add(connectionsLabel_, "wrap");

        miscContainer.add(new JLabel("Stack dimensions: "));
        dimensionsLabel_ = new JLabel("uninitalized");
        miscContainer.add(dimensionsLabel_, "wrap");

        miscContainer.add(new JLabel("Vertex size"));
        JTextField vertexSizeText = new JTextField(
                msSettings.get(microscenery.Settings.MMMicroscope.VertexDiameter,1.0f).toString()
                ,10);
        vertexSizeText.addActionListener(e -> {
            if (validFloat(vertexSizeText)){
                micromanagerWrapper.setVertexDiameter(Float.parseFloat(vertexSizeText.getText()));
            }
        });
        miscContainer.add(vertexSizeText, "wrap");

        miscContainer.add(new JLabel("Stream camera:"));
        ArrayList<String> camSelectionValues = new ArrayList<>(Arrays.asList(studio.core().getLoadedDevicesOfType(DeviceType.CameraDevice).toArray()));
        camSelectionValues.add(0,"any");
        JComboBox<String> shutterComboBox = new JComboBox<>( camSelectionValues.toArray(new String[0]));
        shutterComboBox.addActionListener(e -> {
            @SuppressWarnings("unchecked") JComboBox<String> cb = (JComboBox<String>)e.getSource();
            String name = (String)cb.getSelectedItem();
            assert name != null;
            msSettings.set("Stream.Camera",name);
        });
        miscContainer.add(shutterComboBox, "wrap");

        JCheckBox watchStagePosCheckbox = new JCheckBox("Stream stage position", true);
        watchStagePosCheckbox.addChangeListener(e -> msContext.eventListener.listenToStage = watchStagePosCheckbox.isSelected());
        miscContainer.add(watchStagePosCheckbox,"wrap");

        miscContainer.add(new JLabel("Stream rate limit"));
        JTextField streamRateLimitText = new JTextField(
                msSettings.get(microscenery.Settings.MMMicroscope.Stream.ImageRateLimitPerSec,0.0f).toString()
                ,10);
        streamRateLimitText.addActionListener(e -> {
            if (validFloat(streamRateLimitText)){
                msSettings.set(microscenery.Settings.MMMicroscope.Stream.ImageRateLimitPerSec,Float.parseFloat(streamRateLimitText.getText()));
            }
        });
        miscContainer.add(streamRateLimitText, "wrap");

        JButton settingsButton = new JButton("Settings");
        settingsButton.addActionListener(e -> new SettingsEditor(msSettings,new JFrame("org.micromanager.plugins.micromanager.SettingsEditor"),480, 500));
        miscContainer.add(settingsButton, "wrap");

        JButton addPipelineButton = new JButton("Add stream to pipeline");
        addPipelineButton.addActionListener(e -> studio.data().addAndConfigureProcessor(plugin));
        miscContainer.add(addPipelineButton, " wrap");

        this.add(miscContainer, "grow");
        // -- end misc container --

        ablationPanel = new AblationPanel(mmCon,studio,micromanagerWrapper);
        this.add(ablationPanel,"growx,wrap");

        stageLimitsPanel = new StageLimitsPanel(mmCon,micromanagerWrapper,msSettings);
        this.add(stageLimitsPanel,"");

        JPanel stopAndHelpPanel = new JPanel(new MigLayout());
        stopAndHelpPanel.setBorder(BorderFactory.createTitledBorder("Other"));
        JButton stopButton = new JButton("STOP");
        stopButton.addActionListener(e -> micromanagerWrapper.stop());
        stopAndHelpPanel.add(stopButton,"grow");
        JTextArea helpTextArea = new JTextArea("1: drag\n2: snap\n3: live\n" +
                "4: steer\n5: stack\n6: explore Cube\n7: ablate\n0: STOP\nE: toggle controls");
        stopAndHelpPanel.add(helpTextArea,"wrap");
        //stopAndHelpPanel.add(new OldStackAcquisitionPanel(msSettings,studio,micromanagerWrapper), "wrap");
        this.add(stopAndHelpPanel, "span, wrap");

        this.pack();

        updateLabels(server.getStatus());
        updateLabels(new ActualMicroscopeSignal(micromanagerWrapper.status()));

        String[] settingsBase = {microscenery.Settings.Stage.Limits.Min, microscenery.Settings.Stage.Limits.Max};
        String[] stageLimitSettings = new String[]{
                settingsBase[0] + "X",
                settingsBase[0] + "Y",
                settingsBase[0] + "Z",
                settingsBase[1] + "X",
                settingsBase[1] + "Y",
                settingsBase[1] + "Z", //screw java 8 >:(
        };

        for (String s : stageLimitSettings) {
            msSettings.addUpdateRoutine(s,false,() -> {
                stageLimitsPanel.updateValuesFromSetting();
                return null;
            });
        }
    }

    @Override
    public void dispose() {
        super.dispose();
    }

    private Unit updateLabels(RemoteMicroscopeSignal signal) {

        if (signal instanceof RemoteMicroscopeStatus){
            // ports label
            RemoteMicroscopeStatus status = (RemoteMicroscopeStatus) signal;
            portLabel_.setText(server.getBasePort() + "" + status.getDataPorts().stream().map(p -> " ," + p).collect(Collectors.joining()));

            // connections label
            connectionsLabel_.setText(status.getConnectedClients() + "");
        } else if (signal instanceof ActualMicroscopeSignal) {
            ActualMicroscopeSignal ams = (ActualMicroscopeSignal) signal;
            if (ams.getSignal() instanceof MicroscopeStatus) {
                MicroscopeStatus status = (MicroscopeStatus) ams.getSignal();
                statusLabel_.setText(status.getState().toString());
            } else if(ams.getSignal() instanceof AblationResults){
                ablationPanel.updateTimings((AblationResults) ams.getSignal());
            }
        }

        // dimensions label
        Vector2i d =micromanagerWrapper.hardwareDimensions().getImageSize();
        dimensionsLabel_.setText(d.x + "x" + d.y);

        return Unit.INSTANCE;
    }

    static boolean validFloat(JTextField tf) {
        try{
            Float.parseFloat(tf.getText());
        }catch (NumberFormatException n){
            JOptionPane.showMessageDialog(null,
                    tf.getText()+ "Is not a valid floating point number", "Invalid number",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    @Override
    public void showGUI() {
        setVisible(true);
    }

    @Override
    public void cleanup() {
        dispose();
    }

    @Override
    public PropertyMap getSettings() {
        return PropertyMaps.builder().build();
    }
}
