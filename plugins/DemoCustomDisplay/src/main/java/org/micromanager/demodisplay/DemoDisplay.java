///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.demodisplay;

import com.google.common.eventbus.EventBus;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.Font;
import java.awt.Graphics;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.Image;
import org.micromanager.data.DatastoreRewriteException;
import org.micromanager.data.DataProvider;
import org.micromanager.display.DataViewerListener;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplaySettings;

// Imports for MMStudio internal packages
// Plugins should not access internal packages, to ensure modularity and
// maintainability. However, this plugin code is older than the current
// MMStudio API, so it still uses internal classes and interfaces. New code
// should not imitate this practice.
import org.micromanager.display.internal.event.DataViewerDidBecomeActiveEvent;
import org.micromanager.display.internal.event.DefaultDisplaySettingsChangedEvent;


/**
 * This plugin provides an example implementation of a DataViewer, for creating
 * a custom image display window.
 */
public class DemoDisplay extends JFrame implements DataViewer {
   private EventBus bus_;
   private Datastore store_;
   private DisplaySettings settings_;
   private Studio studio_;
   private final JPanel imageDisplay_;

   private int imageCount_ = 0;
   private Image currentImage_ = null;

   public DemoDisplay(Studio studio) {
      super("Demo Display");
      studio_ = studio;
      // Ensure we start with valid, if empty, DisplaySettings.
      settings_ = studio_.displays().getDisplaySettingsBuilder().build();
      bus_ = new EventBus();
      store_ = studio_.data().createRAMDatastore();

      // Notify the DisplayManager when we receive focus.
      super.addWindowListener(new WindowAdapter() {
         @Override
         public void windowActivated(WindowEvent e) {
            postEvent(DataViewerDidBecomeActiveEvent.create(DemoDisplay.this));
         }
      });

      super.setLayout(new MigLayout("flowy"));

      imageDisplay_ = new JPanel() {
         @Override
         public void paint(Graphics g) {
            // Ordinarily you would paint your images here, but we just
            // draw some information about the image.
            if (currentImage_ == null) {
               // No images in the Datastore yet.
               return;
            }
            g.setFont(new Font("Arial", Font.PLAIN, 14));
            g.drawString("Image coords: " + currentImage_.getCoords(),
                  15, 15);
         }

         @Override
         public Dimension getPreferredSize() {
            // Ensure we're large enough to show our text.
            return new Dimension(400, 400);
         }
      };
      super.add(imageDisplay_);

      JButton snap = new JButton("Snap new image");
      snap.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            addNewImage();
         }
      });
      super.add(snap);

      // Start us off with several images already in our Datastore.
      for (int i = 0; i < 5; ++i) {
         addNewImage();
      }

      super.pack();
      super.setVisible(true);

      // Make Micro-Manager aware of our display.
      studio_.displays().addViewer(this);
      // Ensure there are histograms for our display.
      //updateHistograms();
   }

   /**
    * Snap a new image and add it to our Datastore.
    */
   private void addNewImage()  {
      Image newImage = studio_.live().snap(false).get(0);
      // Move its timepoint to the end of our little timeseries.
      newImage = newImage.copyAtCoords(newImage.getCoords()
         .copy().time(imageCount_).build());
      try {
         store_.putImage(newImage);
      }
      catch (DatastoreFrozenException e) {
         // This should be impossible.
         studio_.logs().showError("Datastore has been frozen.");
      }
      catch (DatastoreRewriteException e) {
         // This should also be impossible.
         studio_.logs().showError("Image already exists at " + newImage.getCoords());
      } catch (IOException ex) {
         studio_.logs().showError(ex, "IO exception in DemoDataViewer");
      }
      imageCount_ += 1;
      // Draw the new image, and ensure the inspector histograms are up to
      // date.
      currentImage_ = newImage;
      //updateHistograms();
      repaint();
   }

   /**
    * This method ensures that the Inspector histograms have up-to-date data
    * to display.
 
   private void updateHistograms() {
      // We only ever have one image shown at a time, but if we had multiple
      // visible, then we would add them all to this list.
      ArrayList<Image> imageList = new ArrayList<Image>();
      imageList.add(currentImage_);
      studio_.displays().updateHistogramDisplays(imageList, this);
   }
   *    */

   @Override
   public void setDisplaySettings(DisplaySettings settings) {
      // We must inform our clients of the new display settings, according to
      // the documentation in DataViewer.
      bus_.post(DefaultDisplaySettingsChangedEvent.create(this, settings_, settings)); 
      // Display settings may have changed how we should paint. Again, this is
      // per the documentation in DataViewer.
      settings_ = settings;
      repaint();
   }

   @Override
   public DisplaySettings getDisplaySettings() {
      return settings_;
   }

   @Override
   public void registerForEvents(Object obj) {
      bus_.register(obj);
   }

   @Override
   public void unregisterForEvents(Object obj) {
      bus_.unregister(obj);
   }

   public void postEvent(Object obj) {
      bus_.post(obj);
   }

   @Override
   public Datastore getDatastore() {
      return store_;
   }

   @Override
   public void setDisplayedImageTo(Coords coords) {
      try {
         currentImage_ = store_.getImage(coords);
      } catch (IOException ex) {
         studio_.logs().logError(ex, "IOException in DemoDataViewer");
      }
      repaint();
   }

   @Override
   public List<Image> getDisplayedImages() {
      // We only ever show one image at a time.
      ArrayList<Image> result = new ArrayList<Image>();
      result.add(currentImage_);
      return result;
   }

   @Override
   public String getName() {
      return "Demo Display";
   }

   @Override
   public boolean compareAndSetDisplaySettings(DisplaySettings originalSettings, DisplaySettings newSettings) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public DataProvider getDataProvider() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public void setDisplayPosition(Coords position, boolean forceRedisplay) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public void setDisplayPosition(Coords position) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public Coords getDisplayPosition() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public boolean compareAndSetDisplayPosition(Coords originalPosition, Coords newPosition, boolean forceRedisplay) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public boolean compareAndSetDisplayPosition(Coords originalPosition, Coords newPosition) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public boolean isClosed() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public void addListener(DataViewerListener listener, int priority) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public void removeListener(DataViewerListener listener) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }
}
