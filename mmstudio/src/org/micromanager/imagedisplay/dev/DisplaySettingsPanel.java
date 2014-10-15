package org.micromanager.imagedisplay.dev;

import ij.CompositeImage;
import ij.ImagePlus;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import net.miginfocom.swing.MigLayout;

import org.micromanager.api.data.Datastore;
import org.micromanager.api.data.DatastoreLockedException;
import org.micromanager.api.data.DisplaySettings;
import org.micromanager.data.DefaultDisplaySettings;

import org.micromanager.utils.ReportingUtils;

/**
 * This class provides controls for the general display settings, including
 * some settings that control how the histograms behave. Note this is
 * distinct from the DisplaySettings metadata in the Datastore for the display;
 * some of that is addressed here, and some in the histograms.
 */
public class DisplaySettingsPanel extends JPanel {
   private Datastore store_;
   private ImagePlus ijImage_;

   public DisplaySettingsPanel(Datastore store, ImagePlus ijImage) {
      super(new MigLayout());

      store_ = store;
      ijImage_ = ijImage;

      DefaultDisplaySettings settings = DefaultDisplaySettings.getStandardSettings();

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
      displayMode.setSelectedIndex(settings.getChannelDisplayModeIndex());
      add(displayMode, "wrap");
      
      add(new JLabel("Histograms update "), "split 2");
      final JComboBox histogramUpdateRate = new JComboBox(
            new String[] {"Never", "Every image", "Once per second"});
      histogramUpdateRate.setToolTipText("Select how frequently to update histograms. Reduced histogram update rate may help reduce CPU load.");
      histogramUpdateRate.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            setHistogramUpdateRate(histogramUpdateRate);
         }
      });
      double updateRate = settings.getHistogramUpdateRate();
      if (updateRate < 0) {
         histogramUpdateRate.setSelectedIndex(0);
      }
      else if (updateRate == 0) {
         histogramUpdateRate.setSelectedIndex(1);
      }
      else {
         // TODO: this ignores the possibility that the actual update rate will
         // be a value other than once per second.
         histogramUpdateRate.setSelectedIndex(2);
      }
      add(histogramUpdateRate, "wrap");
      
      final JCheckBox shouldAutostretch = new JCheckBox("Autostretch histograms");
      shouldAutostretch.setToolTipText("Automatically rescale the histograms every time a new image is displayed.");
      shouldAutostretch.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            setShouldAutostretch(shouldAutostretch);
         }
      });
      shouldAutostretch.setSelected(settings.getShouldAutostretch());
      add(shouldAutostretch, "wrap");

      add(new JLabel("Truncate histograms: "), "split 2");
      final JSpinner trimPercentage = new JSpinner();
      trimPercentage.setToolTipText("When autostretching histograms, the min and max will be moved inwards by the specified percentage (e.g. if this is set to 10, then the scaling will be from the 10th percentile to the 90th).");
      trimPercentage.setModel(new SpinnerNumberModel(0.0, 0.0, 100.0, 1.0));
      trimPercentage.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent event) {
            setTrimPercentage(trimPercentage);
         }
      });
      trimPercentage.addKeyListener(new KeyAdapter() {
         @Override
         public void keyPressed(KeyEvent event) {
            setTrimPercentage(trimPercentage);
         }
      });
      trimPercentage.setValue(settings.getTrimPercentage());
      add(trimPercentage, "wrap");
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
   private void setHistogramUpdateRate(JComboBox histogramUpdateRate) {
      String selection = (String) histogramUpdateRate.getSelectedItem();
      double rate = 0; // i.e. update as often as possible.
      if (selection.equals("Never")) {
         rate = -1;
      }
      else if (selection.equals("Every image")) {
         rate = 0;
      }
      else if (selection.equals("Once per second")) {
         rate = 1;
      }
      DisplaySettings settings = store_.getDisplaySettings();
      settings = settings.copy().histogramUpdateRate(rate).build();
      try {
         store_.setDisplaySettings(settings);
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

   /**
    * The user set a new trim percentage.
    */
   private void setTrimPercentage(JSpinner trimPercentage) {
      DisplaySettings settings = store_.getDisplaySettings();
      double percentage = (Double) trimPercentage.getValue();
      settings = settings.copy().trimPercentage(percentage).build();
      try {
         store_.setDisplaySettings(settings);
      }
      catch (DatastoreLockedException e) {
         ReportingUtils.showError("The datastore is locked; settings cannot be changed.");
      }
   }
}
