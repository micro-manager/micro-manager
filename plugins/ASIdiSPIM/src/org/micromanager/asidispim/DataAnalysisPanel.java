
package org.micromanager.asidispim;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.concurrent.ExecutionException;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import net.miginfocom.swing.MigLayout;

import org.micromanager.api.MMWindow;
import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.Data.MyStrings;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.PanelUtils;
import org.micromanager.utils.FileDialogs;
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
   private final JPanel mipavPanel_;
   private final JTextField saveDestinationField_;
   private Prefs prefs_;
   public static final String[] TRANSFORMOPTIONS = 
      {"None", "Rotate Right 90\u00B0", "Rotate Left 90\u00B0", "Rotate outward"};
   public static FileDialogs.FileType MIPAV_DATA_SET 
           = new FileDialogs.FileType("MIPAV_DATA_SET",
                 "Export to mipav Location",
                 System.getProperty("user.home") + "/Untitled",
                 false, (String[]) null);
   
   /**
    * 
    * @param prefs - Plugin-wide preferences
    */
   public DataAnalysisPanel(ScriptInterface gui, Prefs prefs) {    
      super(MyStrings.PanelNames.DATAANALYSIS.toString(),
              new MigLayout(
              "",
              "[right]",
              "[]16[]"));
      prefs_ = prefs;
            
      int textFieldWidth = 35;

      // start volume sub-panel
      mipavPanel_ = new JPanel(new MigLayout(
              "",
              "[right]4[center]4[left]",
              "[]8[]"));
      
      mipavPanel_.setBorder(PanelUtils.makeTitledBorder("Export to mipav"));
      
      JLabel instructions = new JLabel("Exports data to a format \n"
              + "compatible with the MIPAV GenerateFusion Plugin");
      mipavPanel_.add(instructions, "span 3, wrap");
      
      mipavPanel_.add(new JLabel("Export directory:"), "");
      
      saveDestinationField_ = new JTextField();
      saveDestinationField_.setText(prefs_.getString(panelName_,
              Properties.Keys.PLUGIN_EXPORT_MIPAV_DATA_DIR, ""));
      saveDestinationField_.setColumns(textFieldWidth);
      saveDestinationField_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
             prefs_.putString(panelName_, Properties.Keys.PLUGIN_EXPORT_MIPAV_DATA_DIR,
                    saveDestinationField_.getText());
         }
      });
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
      
      // row with transform options
      JLabel transformLabel = new JLabel("Transform:");
      mipavPanel_.add(transformLabel);
      final JComboBox transformSelect = new JComboBox();
      for (String item : TRANSFORMOPTIONS) {
         transformSelect.addItem(item);
      }
      String transformOption = prefs_.getString(
              panelName_, Properties.Keys.PLUGIN_EXPORT_MIPAV_TRANSFORM_OPTION, 
              TRANSFORMOPTIONS[1]);
      transformSelect.setSelectedItem(transformOption);
      transformSelect.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            prefs_.putString(panelName_, 
                    Properties.Keys.PLUGIN_EXPORT_MIPAV_TRANSFORM_OPTION, 
                    (String)transformSelect.getSelectedItem());
         }
      });
      mipavPanel_.add(transformSelect, "left, wrap");
      
      final JProgressBar progBar = new JProgressBar();
      progBar.setStringPainted(true);
      progBar.setVisible(false);
      final JLabel infoLabel = new JLabel("");
      
      
      JButton exportButton = new JButton("Export");
      exportButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            SaveTask task = new SaveTask(saveDestinationField_.getText(),
                    transformSelect.getSelectedIndex());
            task.addPropertyChangeListener(new PropertyChangeListener() {

               @Override
               public void propertyChange(PropertyChangeEvent evt) {
                  if ("progress".equals(evt.getPropertyName())) {
                     int progress = (Integer) evt.getNewValue();
                     if (!progBar.isVisible()) {
                        progBar.setVisible(true);
                        infoLabel.setText("Saving...");
                        infoLabel.setVisible(true);
                     }
                     progBar.setValue(progress);
                     if (progress == 100) {
                        progBar.setVisible(false);
                        infoLabel.setText("Done Saving...");
                     }
                  }
               }
            });
            task.execute();
         }
      });
      mipavPanel_.add(exportButton, "span 3, center, wrap");
      mipavPanel_.add(infoLabel,"");
      mipavPanel_.add(progBar, "span3, center, wrap");    
            
       
      this.add(mipavPanel_);
   }
   
   /**
    * Worker thread that executes file saving.  Updates the progress bar
    * using the setProgress method, which results in a PropertyChangedEvent
    * in attached listeners
    */
   class SaveTask extends SwingWorker<Void, Void> {
      final String targetDirectory_;
      final int transformIndex_;
      SaveTask (String targetDirectory, int transformIndex) {
         targetDirectory_ = targetDirectory;
         transformIndex_ = transformIndex;
      }
   
      @Override
      protected Void doInBackground() throws Exception {
         setProgress(0);
         ImagePlus ip = IJ.getImage();
         MMWindow mmW = new MMWindow(ip);

         if (!mmW.isMMWindow()) {
            throw new SaveTaskException("Can only convert Micro-Manager data set ");
         }

         String acqName = ip.getShortTitle();
         acqName = acqName.replace("/", "-");

         String spimADir = targetDirectory_ + "\\" + acqName + "\\" + "SPIMA";
         String spimBDir = targetDirectory_ + "\\" + acqName + "\\" + "SPIMB";

         if (new File(spimADir).exists()
                 || new File(spimBDir).exists()) {
            throw new SaveTaskException("Output directory already exists");
         }

         new File(spimADir).mkdirs();
         new File(spimBDir).mkdir();

         ImageProcessor iProc = ip.getProcessor();
         int totalNr = 2 * mmW.getNumberOfFrames() * mmW.getNumberOfSlices();
         int counter = 0;

         for (int c = 0; c < 2; c++) {
            for (int t = 0; t < mmW.getNumberOfFrames(); t++) {
               ImageStack stack = new ImageStack(iProc.getWidth(), iProc.getHeight());
               for (int i = 0; i < mmW.getNumberOfSlices(); i++) {
                  ImageProcessor iProc2;

                  iProc2 = mmW.getImageProcessor(c, i, t, 1);

                  // optional transformation
                  switch (transformIndex_) {
                     case 1: {
                        iProc2.rotate(90);
                        break;
                     }
                     case 2: {
                        iProc2.rotate(-90);
                        break;
                     }
                     case 3: {
                        iProc2.rotate((c==1) ? 90 : -90);
                        break;
                     }
                  }
                  
                  stack.addSlice(iProc2);
                  counter++;
                  double rate = ( (double) counter / (double) totalNr ) * 100.0;
                  setProgress( (int) Math.round(rate));
               }
               ImagePlus ipN = new ImagePlus("tmp", stack);
               ipN.setCalibration(ip.getCalibration());
               if (c == 0) {
                  ij.IJ.save(ipN, spimADir + "\\" + acqName + "_" + "SPIMA-" + t + ".tif");
               } else if (c == 1) {
                  ij.IJ.save(ipN, spimBDir + "\\" + acqName + "_" + "SPIMB-" + t + ".tif");
               }

            }
         }
         return null;
      }

      @Override
      public void done() {
         setCursor(null);
         try {
            get();
            setProgress(100);
         } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (!cause.getMessage().equals("Macro canceled")) {
               if (cause instanceof SaveTaskException) {
                  JOptionPane.showMessageDialog(null, cause.getMessage(), 
                          "Data Export Error", JOptionPane.ERROR_MESSAGE);
               } else {
                  ReportingUtils.showError(ex, (Component) ASIdiSPIM.getFrame());
               }
            }
         } catch (InterruptedException ex) {
             ReportingUtils.showError(ex, "Interrupted while saving data", ASIdiSPIM.getFrame());
         }
      }
   }

   private void setSaveDestinationDirectory(JTextField rootField) {
      File result = FileDialogs.openDir(null,
              "Please choose a directory root for image data",
              MIPAV_DATA_SET);
      if (result != null) {
         rootField.setText(result.getAbsolutePath());
      }
   }

   public class SaveTaskException extends Exception {

      private static final long serialVersionUID = -8472323699461107823L;
      private Throwable cause;

      public SaveTaskException(String message) {
         super(message);
      }

      public SaveTaskException(Throwable t) {
         super(t.getMessage());
         this.cause = t;
      }

      @Override
      public Throwable getCause() {
         return this.cause;
      }
   }
  
}
