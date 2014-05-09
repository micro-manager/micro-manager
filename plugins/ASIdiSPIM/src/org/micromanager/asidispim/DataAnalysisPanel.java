
package org.micromanager.asidispim;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import net.miginfocom.swing.MigLayout;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.api.MMWindow;
import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.PanelUtils;
import org.micromanager.utils.FileDialogs;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

/**
 * Panel in ASIdiSPIM plugin specifically for data analysis/processing
 * For now, we provide a way to export Micro-Manager datasets into 
 * a mipav compatible format 
 * mipav likes data in a folder as follows:
 * folder - SPIMA - name_SPIMA-0.tif, name_SPIMA-x.tif, name_SPIMA-n.tif
 *        - SPIMB - name_SPIMB-0.tif, name_SPIMB-x.tif, name_SPIMB-n.tif
 * @author Nico
 */
@SuppressWarnings("serial")
public class DataAnalysisPanel extends ListeningJPanel {
   private final ScriptInterface gui_;
   private final JPanel mipavPanel_;
   private final JTextField saveDestinationField_;
   private Prefs prefs_;
   
   /**
    * 
    * @param gui -implementation of the Micro-Manager ScriptInterface api
    */
   public DataAnalysisPanel(ScriptInterface gui, Prefs prefs) {    
      super("Data Analysis",
              new MigLayout(
              "",
              "[right]",
              "[]16[]"));
      gui_ = gui;
      prefs_ = prefs;
      
      
      int textFieldWidth = 20;

      // start volume sub-panel
      mipavPanel_ = new JPanel(new MigLayout(
              "",
              "[right]4[center]4[left]",
              "[]8[]"));
      
      mipavPanel_.setBorder(PanelUtils.makeTitledBorder("Export to mipav"));
      
      JLabel instructions = new JLabel("Exports selected data set to a format \n"
              + "compatible with the mipav GenerateFusion Plugin");
      mipavPanel_.add(instructions, "span 3, wrap");
      
      mipavPanel_.add(new JLabel("Target directory:"), "");
      
      saveDestinationField_ = new JTextField();
      saveDestinationField_.setText(prefs_.getString(panelName_,
              Properties.Keys.PLUGIN_DIRECTORY_ROOT, ""));
      saveDestinationField_.setColumns(textFieldWidth);
      mipavPanel_.add(saveDestinationField_);
      
      JButton browseToSaveDestinationButton = new JButton();
      browseToSaveDestinationButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            setSaveDestinationDirectory(saveDestinationField_);
            prefs_.putString(panelName_, Properties.Keys.PLUGIN_EXPORT_MIPAV_DATA_DIR,
                    saveDestinationField_.getText());
         }
      });
      
      browseToSaveDestinationButton.setMargin(new Insets(2, 5, 2, 5));
      browseToSaveDestinationButton.setText("...");
      mipavPanel_.add(browseToSaveDestinationButton, "wrap");
      
      JButton exportButton = new JButton("Export");
      exportButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            exportMipav(saveDestinationField_.getText());
         }
      });
      mipavPanel_.add(exportButton, "span 3, center, wrap");
      
      
      this.add(mipavPanel_);
   }
   
   private void setSaveDestinationDirectory(JTextField rootField) {
      File result = FileDialogs.openDir(null,
              "Please choose a directory root for image data",
              MMStudioMainFrame.MM_DATA_SET);
      if (result != null) {
         rootField.setText(result.getAbsolutePath());
      }
   }
   
   // needs to be put on its own thread, untested code
   private void exportMipav(String targetDirectory) {
      boolean rotateRight = true;
      ImagePlus ip = IJ.getImage();
      MMWindow mmW = new MMWindow(ip);
      try {
         if (!mmW.isMMWindow()) {
            gui_.message("Can only convert Micro-Manager data set ");
            return;
         }
         
         String spimADir = targetDirectory + "\\" + "SPIMA";
         String spimBDir = targetDirectory + "\\" + "SPIMB";
         
         if (new File(spimADir).exists()
                 || new File(spimBDir).exists()) {
            gui_.message("Output directory already exists");
            return;
         }
         
         new File(spimADir).mkdirs();
         new File(spimBDir).mkdir();
         
         String acqName = ip.getShortTitle();
         acqName = acqName.replace("/", "-");
         
         ImageProcessor iProc = ip.getProcessor();
         //gui.message("NrSlices: " + mmW.getNumberOfSlices());

         
         for (int c = 0; c < 2; c++) {
            for (int t = 0; t < mmW.getNumberOfFrames(); t++) {
               ImageStack stack = new ImageStack(iProc.getWidth(), iProc.getHeight());
               for (int i = 0; i < mmW.getNumberOfSlices(); i++) {
                  ImageProcessor iProc2;
                  
                  iProc2 = mmW.getImageProcessor(c, i, t, 1);
                  
                  if (rotateRight) {
                     iProc2 = iProc2.rotateRight();
                  }
                  stack.addSlice(iProc2);
               }
               ImagePlus ipN = new ImagePlus("tmp", stack);
               if (c == 0) {
                  ij.IJ.save(ipN, spimADir + "\\" + acqName + "_" + "SPIMA-" + t + ".tif");
               } else if (c == 1) {
                  ij.IJ.save(ipN, spimBDir + "\\" + acqName + "_" + "SPIMB-" + t + ".tif");
               }
               
            }
         }
      } catch (MMScriptException ex) {
         ReportingUtils.showError(ex, "Problem saving data");
      }
      
   }
}
