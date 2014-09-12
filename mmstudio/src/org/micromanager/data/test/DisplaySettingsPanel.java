package org.micromanager.data.test;

import ij.CompositeImage;
import ij.ImagePlus;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;

import net.miginfocom.swing.MigLayout;

import org.micromanager.api.data.Datastore;
import org.micromanager.api.data.DatastoreLockedException;
import org.micromanager.api.data.DisplaySettings;

import org.micromanager.utils.ReportingUtils;

/**
 * This class provides controls for the general display settings, including
 * some settings that control how the histograms behave.
 */
public class DisplaySettingsPanel extends JPanel {
   private Datastore store_;
   private ImagePlus ijImage_;

   public DisplaySettingsPanel(Datastore store, ImagePlus ijImage) {
      super(new MigLayout());

      store_ = store;
      ijImage_ = ijImage;

      add(new JLabel("Display mode: "), "split 2");
      final JComboBox displayMode = new JComboBox(
            new String[] {"Color", "Grayscale", "Composite"});
      displayMode.setToolTipText("<html>Set the display mode for the image:<ul><li>Color: single channel, in color<li>Grayscale: single-channel grayscale<li>Composite: multi-channel color overlay</ul></html>");
      displayMode.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            setDisplayMode(displayMode);
         }
      });
      add(displayMode);
      
      add(new JLabel("Histogram update rate: "));
      final JTextField histogramUpdateRate = new JTextField("0", 4);
      histogramUpdateRate.setToolTipText("Set how frequently the histograms are allowed to be recalculated, in seconds. This may be useful in reducing CPU load. Use 0 to update histograms as fast as possible, and -1 to disable histograms altogether.");
      histogramUpdateRate.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            setHistogramUpdateRate(histogramUpdateRate);
         }
      });
      add(histogramUpdateRate, "wrap");
      
      final JCheckBox shouldAutostretch = new JCheckBox("Autostretch histograms");
      shouldAutostretch.setToolTipText("Automatically rescale the histograms every time a new image is displayed.");
      shouldAutostretch.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            setShouldAutostretch(shouldAutostretch);
         }
      });
      add(shouldAutostretch);
   }

   /**
    * The user has interacted with the display mode combo box.
    */
   private void setDisplayMode(JComboBox displayMode) {
      if (!(ijImage_ instanceof CompositeImage)) {
         // Non-composite images are always in grayscale mode.
         displayMode.setSelectedIndex(1);
         return;
      }
      
      CompositeImage composite = (CompositeImage) ijImage_;
      String selection = (String) displayMode.getSelectedItem();
      if (selection.equals("Composite")) {
         if (store_.getMaxIndex("channel") > 6) {
            JOptionPane.showMessageDialog(null,
               "Images with more than 7 channels cannot be displayed in Composite mode.");
            // Send them back to Color mode.
            displayMode.setSelectedIndex(0);
         }
         else {
            composite.setMode(CompositeImage.COMPOSITE);
         }
      }
      else if (selection.equals("Color")) {
         composite.setMode(CompositeImage.COLOR);
      }
      else {
         // Assume grayscale mode.
         composite.setMode(CompositeImage.GRAYSCALE);
      }
      composite.updateAndDraw();
   }

   /** 
    * The user is setting a new update rate for the histograms.
    */
   private void setHistogramUpdateRate(JTextField histogramUpdateRate) {
      try {
         double updateRate = Double.parseDouble(histogramUpdateRate.getText());
         DisplaySettings settings = store_.getDisplaySettings();
         settings = settings.copy().histogramUpdateRate(updateRate).build();
         store_.setDisplaySettings(settings);
      }
      catch (NumberFormatException e) {
         // No valid number in the string; ignore it.
      }
      catch (DatastoreLockedException e) {
         ReportingUtils.showError("The datastore is locked; settings cannot be changed.");
      }
   }

   /**
    * The user is toggling autostretch on/off.
    */
   private void setShouldAutostretch(JCheckBox shouldAutostretch) {
      DisplaySettings settings = store_.getDisplaySettings();
      settings = settings.copy().shouldAutostretch(shouldAutostretch.isSelected()).build();
      try {
         store_.setDisplaySettings(settings);
      }
      catch (DatastoreLockedException e) {
         ReportingUtils.showError("The datastore is locked; settings cannot be changed.");
      }
   }
}
