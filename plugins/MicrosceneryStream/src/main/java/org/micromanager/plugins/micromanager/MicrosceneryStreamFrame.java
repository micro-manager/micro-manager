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

    private final JTextField minZText_;
    private final JTextField maxZText_;
    private final JTextField stepsText_;
    private final JTextField refreshTimeText_;
    private final JLabel stepSizeLabel_;
    private final JLabel dimensionsLabel_;
    private final JLabel timesLabel;

    private final RemoteMicroscopeServer server;
    private final Settings msSettings;
    private final MicromanagerWrapper micromanagerWrapper;


    public MicrosceneryStreamFrame(Studio studio) {
        super("Microscenery Stream Plugin");
        ZContext zContext = new ZContext();
        MMConnection mmcon = new MMConnection(studio.core());
        micromanagerWrapper = new MicromanagerWrapper(mmcon,200);
        server = new RemoteMicroscopeServer(micromanagerWrapper, zContext,new SliceStorage(mmcon.getHeight()*mmcon.getWidth()*500));
        msSettings  = Util.getMicroscenerySettings();
        //studio.acquisitions().runAcquisition().getImage()

        ControlSignalsClient loopBackConnection = new ControlSignalsClient(zContext,server.getBasePort(),"localhost", java.util.List.of(this::updateLabels));

        super.setLayout(new MigLayout());//"fill, insets 2, gap 2, flowx"));

        super.add(new JLabel("Ports: "));
        portLabel_ = new JLabel(server.getBasePort() + "" + server.getStatus().getDataPorts().stream().map(p -> " ," + p).collect(Collectors.joining()));
        super.add(portLabel_);

        super.add(new JLabel("Clients: "));
        connectionsLabel_ = new JLabel("0");
        super.add(connectionsLabel_, "wrap");


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
        super.add(refreshTimeText_, "wrap");


        super.add(new JLabel("Status: "));
        statusLabel_ = new JLabel("uninitalized");
        super.add(statusLabel_, "");

        super.add(new JLabel("Stack dimensions: "));
        dimensionsLabel_ = new JLabel("uninitalized");
        super.add(dimensionsLabel_, "");

        super.add(new JLabel("Acq time: "));
        timesLabel = new JLabel("uninitalized");
        super.add(timesLabel, "wrap");
        final Timer timer = new Timer(500, null);
        //ActionListener listener = e -> timesLabel.setText("c:" + cvss.getMmConnection().getMeanCopyTime() + " s:" + cvss.getMmConnection().getMeanSnapTime());
        //timer.addActionListener(listener);
        //timer.start();

        JButton applyButton = new JButton("Apply Params");
        applyButton.addActionListener(e -> updateParams());
        super.add(applyButton);

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

        /*
        JButton sendButton = new JButton("Start Imaging");
        sendButton.addActionListener(e -> cvss.start());
        super.add(sendButton);

        JButton stopButton = new JButton("Stop Imaging");
        stopButton.addActionListener(e -> cvss.pause());
        super.add(stopButton, "wrap");
*/
        super.add(new JLabel("vProtocol2V"));

//      // Snap an image, show the image in the Snap/Live view, and show some
//      // stats on the image in our frame.
//      imageInfoLabel_ = new JLabel();
//      super.add(imageInfoLabel_, "growx, split, span");
//      JButton snapButton = new JButton("Snap Image");
//      snapButton.addActionListener(new ActionListener() {
//         @Override
//         public void actionPerformed(ActionEvent e) {
//            // Multiple images are returned only if there are multiple
//            // cameras. We only care about the first image.
//            List<Image> images = studio_.live().snap(true);
//            Image firstImage = images.get(0);
//            showImageInfo(firstImage);
//         }
//      });
//      super.add(snapButton, "wrap");
//
//      exposureTimeLabel_ = new JLabel("");
//      super.add(exposureTimeLabel_, "split, span, growx");
//
//      // Run an acquisition using the current MDA parameters.
//      JButton acquireButton = new JButton("Run Acquisition");
//      acquireButton.addActionListener(new ActionListener() {
//         @Override
//         public void actionPerformed(ActionEvent e) {
//            // All GUI event handlers are invoked on the EDT (Event Dispatch
//            // Thread). Acquisitions are not allowed to be started from the
//            // EDT. Therefore we must make a new thread to run this.
//            Thread acqThread = new Thread(new Runnable() {
//               @Override
//               public void run() {
//                  studio_.acquisitions().runAcquisition();
//               }
//            });
//            acqThread.start();
//         }
//      });
//      super.add(acquireButton, "wrap");

        super.setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("/org/micromanager/icons/microscope.gif")));
        super.setLocation(100, 100);
        WindowPositioning.setUpLocationMemory(this, this.getClass(), null);

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
        studio.events().registerForEvents(this);
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

//   /**
//    * To be invoked, this method must be public and take a single parameter
//    * which is the type of the event we care about.
//    * @param event
//    */
//   @Subscribe
//   public void onExposureChanged(ExposureChangedEvent event) {
//      exposureTimeLabel_.setText(String.format("Camera %s exposure time set to %.2fms",
//               event.getCameraName(), event.getNewExposureTime()));
//   }
//
//   /**
//    * Display some information on the data in the provided image.
//    */
//   private void showImageInfo(Image image) {
//      // See DisplayManager for information on these parameters.
//      //HistogramData data = studio_.displays().calculateHistogram(
//      //   image, 0, 16, 16, 0, true);
//      imageInfoLabel_.setText(String.format(
//            "Image size: %dx%d", // min: %d, max: %d, mean: %d, std: %.2f",
//            image.getWidth(), image.getHeight() ) ); //, data.getMinVal(),
//            //data.getMaxVal(), data.getMean(), data.getStdDev()));
//   }

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
            /*
            // status label
            switch (status.getState()) {
                case MANUAL ->
                case Paused -> statusLabel_.setText("Paused");
                case Imaging -> statusLabel_.setText("Imaging");
                case ShuttingDown -> statusLabel_.setText("ShuttingDown");
            }*/
            }
        }

        // dimensions label
        Vector2i d =micromanagerWrapper.hardwareDimensions().getImageSize();
        dimensionsLabel_.setText(d.x + "x" + d.y);

        return Unit.INSTANCE;
    }
}
