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

import graphics.scenery.Settings;
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
        new ControlSignalsClient(zContext,server.getBasePort(),"localhost", java.util.List.of(this::updateLabels));

        super.setLayout(new MigLayout());
        super.setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("/org/micromanager/icons/microscope.gif")));
        super.setLocation(100, 100);
        WindowPositioning.setUpLocationMemory(this, this.getClass(), null);

        // ---- content ----
        super.add(new JLabel("Version: vertex size"),"");

        super.add(new JLabel("Status: "));
        statusLabel_ = new JLabel("uninitalized");
        super.add(statusLabel_, "");

        super.add(new JLabel("Ports: "));
        portLabel_ = new JLabel(server.getBasePort() + "" + server.getStatus().getDataPorts().stream().map(p -> " ," + p).collect(Collectors.joining()));
        super.add(portLabel_);

        super.add(new JLabel("Clients: "));
        connectionsLabel_ = new JLabel("0");
        super.add(connectionsLabel_, "");

        super.add(new JLabel("Stack dimensions: "));
        dimensionsLabel_ = new JLabel("uninitalized");
        super.add(dimensionsLabel_, "");

        super.add(new JLabel("Vertex size"));
        JTextField vertexSizeText = new JTextField(
                msSettings.get("MMConnection.vertexDiameter",1.0f).toString()
                ,10);
        vertexSizeText.addActionListener(e -> {
            if (validFloat(vertexSizeText)){
                micromanagerWrapper.setVertexDiameter(Float.parseFloat(vertexSizeText.getText()));
            }
        });
        super.add(vertexSizeText,"wrap");

        JPanel tmp = new JPanel(new MigLayout());
        tmp.add(new StageLimitsPanel(mmcon,micromanagerWrapper,msSettings),"");
        tmp.add(new OldStackAcquisitionPanel(msSettings,studio,micromanagerWrapper), "wrap");
        super.add(tmp, "span, wrap");

        super.pack();

        updateLabels(server.getStatus());
        updateLabels(new ActualMicroscopeSignal(micromanagerWrapper.status()));

        // Registering this class for events means that its event handlers
        // (that is, methods with the @Subscribe annotation) will be invoked when
        // an event occurs. You need to call the right registerForEvents() method
        // to get events; this one is for the application-wide event bus, but
        // there's also Datastore.registerForEvents() for events specific to one
        // Datastore, and DisplayWindow.registerForEvents() for events specific
        // to one image display window.
        //studio.events().registerForEvents(this);
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
