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
//import graphics.scenery.org.micromanager.plugins.micromanager.SettingsEditor;
import kotlin.Unit;
import microscenery.Util;
import microscenery.hardware.micromanagerConnection.MMConnection;
import microscenery.hardware.micromanagerConnection.MicromanagerWrapper;
import microscenery.network.ControlSignalsClient;
import microscenery.network.RemoteMicroscopeServer;
import microscenery.network.SliceStorage;
import microscenery.signals.*;
import net.miginfocom.swing.MigLayout;
import org.joml.Vector2i;
import org.micromanager.Studio;
import org.micromanager.internal.utils.WindowPositioning;
import org.zeromq.ZContext;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.stream.Collectors;

// Imports for MMStudio internal packages
// Plugins should not access internal packages, to ensure modularity and
// maintainability. However, this plugin code is older than the current
// MMStudio API, so it still uses internal classes and interfaces. New code
// should not imitate this practice.

public class MicrosceneryStreamFrame extends JFrame {

    private final JLabel statusLabel_;
    private final JLabel portLabel_;
    private final JLabel connectionsLabel_;
    private final JLabel dimensionsLabel_;

    private final StageLimitsPanel stageLimitsPanel;

    private final RemoteMicroscopeServer server;
    private final Settings msSettings;
    private final MicromanagerWrapper micromanagerWrapper;


    public MicrosceneryStreamFrame(Studio studio) {
        super("Microscenery Stream Plugin");
        ZContext zContext = new ZContext();
        MMConnection mmcon = new MMConnection(studio.core());
        micromanagerWrapper = new MicromanagerWrapper(mmcon,200,false);
        server = new RemoteMicroscopeServer(micromanagerWrapper, zContext,new SliceStorage(mmcon.getHeight()*mmcon.getWidth()*500));
        msSettings  = Util.getMicroscenerySettings();

        // loopBackConnection
        new ControlSignalsClient(zContext,server.getBasePort(),"localhost", Collections.singletonList(this::updateLabels));

        super.setLayout(new MigLayout());
        super.setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("/org/micromanager/icons/microscope.gif")));
        super.setLocation(100, 100);
        WindowPositioning.setUpLocationMemory(this, this.getClass(), null);

        // ---- content ----
        JPanel miscContainer = new JPanel(new MigLayout());
        miscContainer.add(new JLabel("Version: abl shutter"), "wrap");

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
                msSettings.get("MMConnection.vertexDiameter",1.0f).toString()
                ,10);
        vertexSizeText.addActionListener(e -> {
            if (validFloat(vertexSizeText)){
                micromanagerWrapper.setVertexDiameter(Float.parseFloat(vertexSizeText.getText()));
            }
        });
        miscContainer.add(vertexSizeText, "wrap");

        miscContainer.add(new JLabel("Ablation Shutter:"));
        JComboBox<String> shutterComboBox = new JComboBox<>( studio.shutter().getShutterDevices().toArray(new String[0]));
        shutterComboBox.addActionListener(e -> {
            @SuppressWarnings("unchecked") JComboBox<String> cb = (JComboBox<String>)e.getSource();
            String name = (String)cb.getSelectedItem();
            assert name != null;
            msSettings.set("Ablation.Shutter",name);
        });
        miscContainer.add(shutterComboBox, "wrap");

        JButton settingsButton = new JButton("Settings");
        settingsButton.addActionListener(e -> new SettingsEditor(msSettings,new JFrame("org.micromanager.plugins.micromanager.SettingsEditor"),480, 500));
        miscContainer.add(settingsButton, "wrap");

        super.add(miscContainer);


        stageLimitsPanel = new StageLimitsPanel(mmcon,micromanagerWrapper,msSettings);
        super.add(stageLimitsPanel,"");


        JPanel panelContainer = new JPanel(new MigLayout());

        JButton stopButton = new JButton("STOP");
        stopButton.addActionListener(e -> micromanagerWrapper.stop());
        panelContainer.add(stopButton,"grow");

        JTextArea helpTextArea = new JTextArea("1: drag\n2: snap\n3: live\n" +
                "4: steer\n5: stack\n6: explore Cube\n7: ablate\n0: STOP\nE: toggle controls");
        panelContainer.add(helpTextArea,"wrap");
        //panelContainer.add(new OldStackAcquisitionPanel(msSettings,studio,micromanagerWrapper), "wrap");
        super.add(panelContainer, "span, wrap");

        super.pack();

        updateLabels(server.getStatus());
        updateLabels(new ActualMicroscopeSignal(micromanagerWrapper.status()));

        for (String s : (new String[]{"Stage.minX","Stage.maxX", "Stage.minY", "Stage.maxY", "Stage.minZ", "Stage.maxZ"})) {
            msSettings.addUpdateRoutine(s,() -> {
                stageLimitsPanel.updateValues();
                return null;
            });
        }
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
            return true;
        }
        return false;
    }
}
