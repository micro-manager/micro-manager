/**
 * ExampleFrame.java
 *
 * This module shows an example of creating a GUI (Graphical User Interface).
 * There are many ways to do this in Java; this particular example uses the
 * MigLayout layout manager, which has extensive documentation online.
 *
 *
 * Nico Stuurman, copyright UCSF, 2012, 2015
 *
 * LICENSE: This file is distributed under the BSD license. License text is
 * included with the source distribution.
 *
 * This file is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.
 *
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 */
package org.micromanager.plugins.example;

import com.google.common.eventbus.Subscribe;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Font;
import java.util.List;

import javax.swing.*;


import microscenery.MMConnection;
import microscenery.MMVolumeSender;
import net.miginfocom.swing.MigLayout;

import org.micromanager.data.Image;
import org.micromanager.events.ExposureChangedEvent;
import org.micromanager.Studio;
import org.micromanager.internal.utils.WindowPositioning;

// Imports for MMStudio internal packages
// Plugins should not access internal packages, to ensure modularity and
// maintainability. However, this plugin code is older than the current
// MMStudio API, so it still uses internal classes and interfaces. New code
// should not imitate this practice.


public class MicrosceneryStreamFrame extends JFrame {

   private Studio studio_;
   private JTextField portText_;
   private JTextField slicesText_;
   private JLabel statusLabel_;
//   private final JLabel imageInfoLabel_;
//   private final JLabel exposureTimeLabel_;
   private MMVolumeSender sender;



   public MicrosceneryStreamFrame(Studio studio) {
      super("Microscenery Stream Plugin");
      studio_ = studio;

      super.setLayout(new MigLayout());//"fill, insets 2, gap 2, flowx"));

      super.add(new JLabel("Port: "));
      portText_ = new JTextField(10);
      portText_.setText("4000");
      super.add(portText_,"wrap");

      super.add(new JLabel("Slices: "));
      slicesText_ = new JTextField(10);
      slicesText_.setText("100");
      super.add(slicesText_,"wrap");

      super.add(new JLabel("Status: "));
      statusLabel_ = new JLabel("uninitalized");
      super.add(statusLabel_,"wrap");

      JButton sendButton = new JButton("Start Sending");
      sendButton.addActionListener(e -> {
         sender = new MMVolumeSender(studio_.core());
         //MMConnection con = sender.getMmConnection();
         statusLabel_.setText("started");
      });
      super.add(sendButton);

      JButton stopButton = new JButton("Stop Sending");
      stopButton.addActionListener(e -> {
         sender.stop();
         statusLabel_.setText("stopped");
      });
      super.add(stopButton, "wrap");

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

      super.setIconImage(Toolkit.getDefaultToolkit().getImage(
              getClass().getResource("/org/micromanager/icons/microscope.gif")));
      super.setLocation(100, 100);
      WindowPositioning.setUpLocationMemory(this, this.getClass(), null);

      super.pack();

      // Registering this class for events means that its event handlers
      // (that is, methods with the @Subscribe annotation) will be invoked when
      // an event occurs. You need to call the right registerForEvents() method
      // to get events; this one is for the application-wide event bus, but
      // there's also Datastore.registerForEvents() for events specific to one
      // Datastore, and DisplayWindow.registerForEvents() for events specific
      // to one image display window.
      studio_.events().registerForEvents(this);
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
}
