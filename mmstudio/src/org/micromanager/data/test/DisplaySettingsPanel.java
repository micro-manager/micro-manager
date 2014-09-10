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

import net.miginfocom.swing.MigLayout;

import org.micromanager.api.data.Datastore;

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
      });
      add(displayMode);
      
      JCheckBox shouldShowScaleBar = new JCheckBox("Display scale bar");
      add(shouldShowScaleBar);
   }
}
