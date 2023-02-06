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

import com.google.common.eventbus.Subscribe;
import fromScenery.Settings;
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
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.util.Collections;
import java.util.stream.Collectors;

public class MicrosceneryStreamFrame extends JFrame {

    private final JLabel statusLabel_;
    private final JLabel portLabel_;
    private final JLabel connectionsLabel_;
    private final JLabel dimensionsLabel_;

    private final StageLimitsPanel stageLimitsPanel;
    private final AblationPanel ablationPanel;

    private final RemoteMicroscopeServer server;
    private final Settings msSettings;
    private final MicromanagerWrapper micromanagerWrapper;
    private final EventListener eventListener;


    public MicrosceneryStreamFrame(Studio studio) {
        super("Microscenery Stream Plugin");
        ZContext zContext = new ZContext();
        MMConnection mmCon = new MMConnection(studio.core());
        micromanagerWrapper = new MicromanagerWrapper(mmCon,200,false);
        server = new RemoteMicroscopeServer(micromanagerWrapper, zContext,new SliceStorage(mmCon.getHeight()*mmCon.getWidth()*500));
        msSettings  = Util.getMicroscenerySettings();

        eventListener = new EventListener(studio, micromanagerWrapper);
        
        // loopBackConnection
        new ControlSignalsClient(zContext,server.getBasePort(),"localhost", Collections.singletonList(this::updateLabels));

        // -- start GUI --
        this.setLayout(new MigLayout("fill","","align top"));
        this.setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("/org/micromanager/icons/microscope.gif")));
        this.setLocation(100, 100);
        WindowPositioning.setUpLocationMemory(this, this.getClass(), null);

        // -- misc container --
        JPanel miscContainer = new JPanel(new MigLayout());
        miscContainer.add(new JLabel("Version: abl shutter"), "wrap");
        miscContainer.setBorder(BorderFactory.createTitledBorder("General"));

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

        JCheckBox watchStagePosCheckbox = new JCheckBox("Watch stage position", true);
        watchStagePosCheckbox.addChangeListener(e -> eventListener.listenToStage = watchStagePosCheckbox.isSelected());
        miscContainer.add(watchStagePosCheckbox,"wrap");

        JButton settingsButton = new JButton("Settings");
        settingsButton.addActionListener(e -> new SettingsEditor(msSettings,new JFrame("org.micromanager.plugins.micromanager.SettingsEditor"),480, 500));
        miscContainer.add(settingsButton, "wrap");

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

        for (String s : (new String[]{"Stage.minX","Stage.maxX", "Stage.minY", "Stage.maxY", "Stage.minZ", "Stage.maxZ"})) {
            msSettings.addUpdateRoutine(s,() -> {
                stageLimitsPanel.updateValues();
                return null;
            });
        }

        this.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) {
                // if any component is not displayed this container is likely going down
                if (!stopAndHelpPanel.isDisplayable()) {
                    // detach listener gracefully otherwise there will be errors
                    eventListener.close();
                }
            }
        });
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
            return true;
        }
        return false;
    }
}
