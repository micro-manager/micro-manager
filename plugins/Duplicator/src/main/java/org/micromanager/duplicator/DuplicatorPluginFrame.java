///////////////////////////////////////////////////////////////////////////////
//FILE:          CropperPluginFrame.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     Cropper plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    Regents of the University of California 2016
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

package org.micromanager.duplicator;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.DefaultFormatter;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.Coords.CoordsBuilder;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.DatastoreRewriteException;
import org.micromanager.data.Image;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.DisplayWindow;
import org.micromanager.internal.utils.MMDialog;

/**
 *
 * @author nico
 */
public class DuplicatorPluginFrame extends MMDialog {
   private final Studio studio_;
   private final DisplayWindow ourWindow_;
   private final Datastore ourStore_;
   
   public DuplicatorPluginFrame (Studio studio, DisplayWindow window) {
      studio_ = studio;
      final DuplicatorPluginFrame cpFrame = this;
      
      super.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      
      ourWindow_ = window;
      ourStore_ = ourWindow_.getDatastore();
      
      // Not sure if this is needed, be safe for now
      if (!ourStore_.getIsFrozen()) {
         studio_.logs().showMessage("Can not duplicate ongoing acquisitions");
         super.dispose();
         return;
      }
      
      super.setLayout(new MigLayout("flowx, fill, insets 8"));
      File file = new File(window.getName());
      String shortName = file.getName();
      super.setTitle(DuplicatorPlugin.MENUNAME + shortName);

      super.loadAndRestorePosition(100, 100, 375, 275);
      
      List<String> axes = ourStore_.getAxes();
      // Note: MM uses 0-based indices in the code, but 1-based indices
      // for the UI.  To avoid confusion, this storage of the desired
      // limits for each axis is 0-based, and translation to 1-based is made
      // in the UI code
      final Map<String, Integer> mins = new HashMap<String, Integer>();
      final Map<String, Integer> maxes = new HashMap<String, Integer>();
      
      super.add(new JLabel(" "));
      super.add(new JLabel("min"));
      super.add(new JLabel("max"), "wrap");
      
      for (final String axis : axes) {
         if (ourStore_.getAxisLength(axis) > 1) {
            mins.put(axis, 1);
            maxes.put(axis, ourStore_.getAxisLength(axis));
            
            super.add(new JLabel(axis));
            SpinnerNumberModel model = new SpinnerNumberModel( 1, 1,
                    (int) ourStore_.getAxisLength(axis), 1);
            mins.put(axis, 0);
            final JSpinner minSpinner = new JSpinner(model);
            JFormattedTextField field = (JFormattedTextField) 
                    minSpinner.getEditor().getComponent(0);
            DefaultFormatter formatter = (DefaultFormatter) field.getFormatter();
            formatter.setCommitsOnValidEdit(true);
            minSpinner.addChangeListener(new ChangeListener(){
               @Override
               public void stateChanged(ChangeEvent ce) {
                  // check to stay below max, this could be annoying at times
                  if ( (Integer) minSpinner.getValue() > maxes.get(axis) + 1) {
                     minSpinner.setValue(maxes.get(axis) + 1);
                  }
                  mins.put(axis, (Integer) minSpinner.getValue() - 1);
                  Coords coord = ourWindow_.getDisplayedImages().get(0).getCoords();
                  coord = coord.copy().index(axis, mins.get(axis)).build();
                  ourWindow_.setDisplayedImageTo(coord);
               }
            });
            super.add(minSpinner, "wmin 60");

            model = new SpinnerNumberModel((int) ourStore_.getAxisLength(axis),
                     1, (int) ourStore_.getAxisLength(axis), 1);
            maxes.put(axis, ourStore_.getAxisLength(axis) - 1);
            final JSpinner maxSpinner = new JSpinner(model);
            field = (JFormattedTextField) 
                    maxSpinner.getEditor().getComponent(0);
            formatter = (DefaultFormatter) field.getFormatter();
            formatter.setCommitsOnValidEdit(true);
            maxSpinner.addChangeListener(new ChangeListener(){
               @Override
               public void stateChanged(ChangeEvent ce) {
                  // check to stay above min
                  if ( (Integer) maxSpinner.getValue() < mins.get(axis) + 1) {
                     maxSpinner.setValue(mins.get(axis) + 1);
                  }
                  maxes.put(axis, (Integer) maxSpinner.getValue() - 1);
                  Coords coord = ourWindow_.getDisplayedImages().get(0).getCoords();
                  coord = coord.copy().index(axis, maxes.get(axis)).build();
                  ourWindow_.setDisplayedImageTo(coord);
               }
            });
            super.add(maxSpinner, "wmin 60, wrap");
         }
      }
      
      super.add(new JLabel("name"));
      final JTextField nameField = new JTextField(shortName);
      super.add(nameField, "span2, grow, wrap");
      
      JButton OKButton = new JButton("OK");
      OKButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent ae) {
            duplicate(ourWindow_, nameField.getText(), mins, maxes);
            cpFrame.dispose();
         }
      });
      super.add(OKButton, "span 3, split 2, tag ok, wmin button");
      
      JButton CancelButton = new JButton("Cancel");
      CancelButton.addActionListener(new ActionListener(){
         @Override
         public void actionPerformed(ActionEvent ae) {
            cpFrame.dispose();
         }
      });
      super.add(CancelButton, "tag cancel, wrap");     
      
      super.pack();
      super.setVisible(true);
      
      
   }
   
   /**
    * Performs the actual creation of a new image with reduced content
    * 
    * @param theWindow - original window to be copied
    * @param newName - name for the copy
    * @param mins - Map with new (or unchanged) minima for the given axis
    * @param maxes - Map with new (or unchanged) maxima for the given axis
    */
   public void duplicate(final DisplayWindow theWindow, 
           final String newName, 
           final Map<String, Integer> mins,
           final Map<String, Integer> maxes) {
      
      // TODO: provide options for disk-backed datastores
      Datastore newStore = studio_.data().createRAMDatastore();
      
      Roi roi = theWindow.getImagePlus().getRoi();
      
      Datastore oldStore = theWindow.getDatastore();
      Coords oldSizeCoord = oldStore.getMaxIndices();
      CoordsBuilder newSizeCoordsBuilder = oldSizeCoord.copy();
      SummaryMetadata metadata = oldStore.getSummaryMetadata();
      String[] channelNames = metadata.getChannelNames();
      if (mins.containsKey(Coords.CHANNEL)) {
         int min = mins.get(Coords.CHANNEL);
         int max = maxes.get(Coords.CHANNEL);
         List<String> chNameList = new ArrayList<String>();
         for (int index = min; index <= max; index++) {
            if (channelNames == null || index >= channelNames.length) {
               chNameList.add("channel " + index);
            } else {
               chNameList.add(channelNames[index]);
            }  
         }
         channelNames = chNameList.toArray(new String[chNameList.size()]);
      }
      newSizeCoordsBuilder.channel(channelNames.length);
      String[] axes = {Coords.STAGE_POSITION, Coords.TIME, Coords.Z};
      for (String axis : axes) {
         if (mins.containsKey(axis)) {
            int min = mins.get(axis);
            int max = maxes.get(axis);
            newSizeCoordsBuilder.index(axis, max - min);
         }
      }

      metadata = metadata.copy()
              .channelNames(channelNames)
              .intendedDimensions(newSizeCoordsBuilder.build())
              .build();
      try {
         newStore.setSummaryMetadata(metadata);

         Iterable<Coords> unorderedImageCoords = oldStore.getUnorderedImageCoords();
         for (Coords oldCoord : unorderedImageCoords) {
            boolean copy = true;
            for (String axis : oldCoord.getAxes()) {
               if (mins.containsKey(axis) && maxes.containsKey(axis)) {
                  if (oldCoord.getIndex(axis) < (mins.get(axis))) {
                     copy = false;
                  }
                  if (oldCoord.getIndex(axis) > maxes.get(axis)) {
                     copy = false;
                  }
               }
            }
            if (copy) {
               CoordsBuilder newCoordBuilder = oldCoord.copy();
               for (String axis : oldCoord.getAxes()) {
                  if (mins.containsKey(axis)) {
                     newCoordBuilder.index(axis, oldCoord.getIndex(axis) - mins.get(axis) );
                  }
               }
               Image img = oldStore.getImage(oldCoord);
               Coords newCoords = newCoordBuilder.build();
               Image newImgShallow = img.copyAtCoords(newCoords);
               if (roi != null) {
                  ImageProcessor ip = null;
                  if (img.getImageJPixelType() == ImagePlus.GRAY8) {
                     ip = new ByteProcessor(
                          img.getWidth(), img.getHeight(), (byte[]) img.getRawPixels());
                  } else 
                     if (img.getImageJPixelType() == ImagePlus.GRAY16) {
                        ip = new ShortProcessor(
                        img.getWidth(), img.getHeight() );
                        ip.setPixels((short[]) img.getRawPixels());
                  }
                  if (ip != null) {
                     ip.setRoi(roi);
                     ImageProcessor copyIp = ip.crop();
                     newImgShallow = studio_.data().createImage(copyIp.getPixels(), 
                             copyIp.getWidth(), copyIp.getHeight(), 
                             img.getBytesPerPixel(), img.getNumComponents(), 
                             newCoords, newImgShallow.getMetadata());
                  } else {
                     throw new DuplicatorException("Unsupported pixel type.  Can only copy 8 or 16 bit images.");
                  }
               }
               newStore.putImage(newImgShallow);
            }
         }

      } catch (DatastoreFrozenException ex) {
         studio_.logs().showError("Can not add data to frozen datastore");
      } catch (DatastoreRewriteException ex) {
         studio_.logs().showError("Can not overwrite data");
      } catch (DuplicatorException ex) {
         studio_.logs().showError(ex.getMessage());
      }
      
      newStore.freeze();
      DisplayWindow copyDisplay = studio_.displays().createDisplay(newStore);
      copyDisplay.setCustomTitle(newName);
      studio_.displays().manage(newStore);
   }
   
}
