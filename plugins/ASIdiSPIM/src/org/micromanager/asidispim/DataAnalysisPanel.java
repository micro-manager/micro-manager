
package org.micromanager.asidispim;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.process.ImageProcessor;

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
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import net.miginfocom.swing.MigLayout;

import org.micromanager.api.MMWindow;
import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.Data.MyStrings;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.MyDialogUtils;
import org.micromanager.asidispim.Utils.PanelUtils;
import org.micromanager.utils.FileDialogs;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

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
   private final JPanel exportPanel_;
   private final JTextField saveDestinationField_;
   private final JTextField baseNameField_;
   private final Prefs prefs_;
   public static final String[] TRANSFORMOPTIONS = 
      {"None", "Rotate Right 90\u00B0", "Rotate Left 90\u00B0", "Rotate outward"};
   public static final String[] EXPORTFORMATS = 
      {"mipav GenerateFusion", "Multiview Reconstruction"};
   public static FileDialogs.FileType EXPORT_DATA_SET 
           = new FileDialogs.FileType("EXPORT_DATA_SET",
                 "Export to Location",
                 System.getProperty("user.home") + "/Untitled",
                 false, (String[]) null);
   
   /**
    * 
    * @param gui
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
      exportPanel_ = new JPanel(new MigLayout(
              "",
              "[right]4[center]4[left]",
              "[]8[]"));
      
      exportPanel_.setBorder(PanelUtils.makeTitledBorder("Export diSPIM data"));
     
      
      exportPanel_.add(new JLabel("Export directory:"), "");
      
      saveDestinationField_ = new JTextField();
      saveDestinationField_.setText(prefs_.getString(panelName_,
              Properties.Keys.PLUGIN_EXPORT_DATA_DIR, ""));
      saveDestinationField_.setColumns(textFieldWidth);
      saveDestinationField_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
             prefs_.putString(panelName_, Properties.Keys.PLUGIN_EXPORT_DATA_DIR,
                    saveDestinationField_.getText());
         }
      });
      exportPanel_.add(saveDestinationField_);
      
      JButton browseToSaveDestinationButton = new JButton();
      browseToSaveDestinationButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            setSaveDestinationDirectory(saveDestinationField_);
            prefs_.putString(panelName_, Properties.Keys.PLUGIN_EXPORT_DATA_DIR,
                    saveDestinationField_.getText());
         }
      });
      
      browseToSaveDestinationButton.setMargin(new Insets(2, 5, 2, 5));
      browseToSaveDestinationButton.setText("...");
      exportPanel_.add(browseToSaveDestinationButton, "wrap");
      
      exportPanel_.add(new JLabel("Base Name:"), "");
      baseNameField_ = new JTextField();
      proposeBaseFieldText();
      baseNameField_.setColumns(textFieldWidth);
      exportPanel_.add(baseNameField_, "wrap");
      
      
      // row with transform options
      JLabel transformLabel = new JLabel("Transform:");
      exportPanel_.add(transformLabel);
      final JComboBox transformSelect = new JComboBox();
      for (String item : TRANSFORMOPTIONS) {
         transformSelect.addItem(item);
      }
      String transformOption = prefs_.getString(
              panelName_, Properties.Keys.PLUGIN_EXPORT_TRANSFORM_OPTION, 
              TRANSFORMOPTIONS[1]);
      transformSelect.setSelectedItem(transformOption);
      transformSelect.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            prefs_.putString(panelName_, 
                    Properties.Keys.PLUGIN_EXPORT_TRANSFORM_OPTION, 
                    (String)transformSelect.getSelectedItem());
         }
      });
      exportPanel_.add(transformSelect, "left, wrap");
      
      // row with output options
      JLabel exportFormatLabel = new JLabel("Export for:");
      exportPanel_.add(exportFormatLabel);
      final JComboBox exportFormatSelect = new JComboBox();
      for (String item : EXPORTFORMATS) {
         exportFormatSelect.addItem(item);
      }
      String exportFormatOption = prefs_.getString(
              panelName_, Properties.Keys.PLUGIN_EXPORT_FORMAT, 
              EXPORTFORMATS[1]);
      exportFormatSelect.setSelectedItem(exportFormatOption);
      exportFormatSelect.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            prefs_.putString(panelName_, 
                    Properties.Keys.PLUGIN_EXPORT_FORMAT, 
                    (String)exportFormatSelect.getSelectedItem());
         }
      });
      exportPanel_.add(exportFormatSelect, "left, wrap");
      
      
      final JProgressBar progBar = new JProgressBar();
      progBar.setStringPainted(true);
      progBar.setVisible(false);
      final JLabel infoLabel = new JLabel("");
     
      JButton exportButton = new JButton("Export");
      exportButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            ExportTask task = new ExportTask(saveDestinationField_.getText(),
                    baseNameField_.getText(),
                    transformSelect.getSelectedIndex(), 
                    exportFormatSelect.getSelectedIndex() );
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
      exportPanel_.add(exportButton, "span 3, center, wrap");
      exportPanel_.add(infoLabel,"");
      exportPanel_.add(progBar, "span3, center, wrap");    
            
       
      this.add(exportPanel_);
   }
   
   @Override
   public void gotSelected() {
      proposeBaseFieldText();
   }
   
   private void proposeBaseFieldText() {
      ImagePlus ip = WindowManager.getCurrentImage();
      if (ip != null) {
         String baseName = ip.getShortTitle();
         baseName = baseName.replaceAll("[^a-zA-Z0-9_\\.\\-]", "_");
         baseNameField_.setText(baseName);
      }
   }
   
   
   /**
    * Worker thread that executes file saving.  Updates the progress bar
    * using the setProgress method, which results in a PropertyChangedEvent
    * in attached listeners
    */
   class ExportTask extends SwingWorker<Void, Void> {
      final String targetDirectory_;
      final String baseName_;
      final int transformIndex_;
      final int exportFormat_;
      ExportTask (String targetDirectory, String baseName, 
              int transformIndex, int exportFormat) {
         targetDirectory_ = targetDirectory;
         baseName_ = baseName.replaceAll("[^a-zA-Z0-9_\\.\\-]", "_");
         transformIndex_ = transformIndex;
         exportFormat_ = exportFormat;
      }
   
      @Override
      protected Void doInBackground() throws Exception {
         setProgress(0);
         ImagePlus ip = IJ.getImage();
         MMWindow mmW = new MMWindow(ip);

         if (!mmW.isMMWindow()) {
            throw new SaveTaskException("Can only convert Micro-Manager data set ");
         }

         
         if (exportFormat_ == 0) { // mipav
            String spimADir = targetDirectory_ + File.separator + baseName_ + 
                    File.separator + "SPIMA";
            String spimBDir = targetDirectory_ + File.separator + baseName_ + 
                    File.separator + "SPIMB";

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
                           iProc2.rotate((c == 1) ? 90 : -90);
                           break;
                        }
                     }

                     stack.addSlice(iProc2);
                     counter++;
                     double rate = ((double) counter / (double) totalNr) * 100.0;
                     setProgress((int) Math.round(rate));
                  }
                  ImagePlus ipN = new ImagePlus("tmp", stack);
                  ipN.setCalibration(ip.getCalibration());
                  if (c == 0) {
                     ij.IJ.save(ipN, spimADir + File.separator + baseName_ + "_" + 
                             "SPIMA-" + t + ".tif");
                  } else if (c == 1) {
                     ij.IJ.save(ipN, spimBDir + File.separator + baseName_ + "_" + 
                             "SPIMB-" + t + ".tif");
                  }
               }
            }
            
         } else 
         if (exportFormat_ == 1) { // Multiview reconstruction
            ImageProcessor iProc = ip.getProcessor();
            int totalNr = 2 * mmW.getNumberOfFrames() * mmW.getNumberOfSlices();
            int counter = 0;

            // one time point per file, one angle per file
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
                           iProc2.rotate((c == 1) ? 90 : -90);
                           break;
                        }
                     }
                     stack.addSlice(iProc2);
                     counter++;
                     double rate = ((double) counter / (double) totalNr) * 100.0;
                     setProgress((int) Math.round(rate));

                  }
                  ImagePlus ipN = new ImagePlus("tmp", stack);
                  ipN.setCalibration(ip.getCalibration());
                  if (c == 0) {
                     ij.IJ.save(ipN, targetDirectory_ + File.separator + baseName_ 
                             + "_TL" + t + "_Angle0.tif");
                  } else if (c == 1) {
                     ij.IJ.save(ipN, targetDirectory_ + File.separator + baseName_ 
                             + "_TL" + t + "_Angle90.tif");
                  }
               }
            }
            
            // the image files have been written to disk, now create the xml file
            // in bigviewer format, using w3c dom
            
            // first create the DOM in memory
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            Document domTree = dbf.newDocumentBuilder().newDocument();
            
            Element spimData = domTree.createElement("SpimData");
            spimData.setAttribute("version", "0.2");
            domTree.appendChild(spimData);
            
            Element basePath = domTree.createElement("BasePath");
            basePath.insertBefore(domTree.createTextNode("."), 
                    basePath.getLastChild());
            basePath.setAttribute("type", "relative");
            spimData.appendChild(basePath);
            
            Element sequenceDescription = domTree.createElement("SequenceDescription");
            spimData.appendChild(sequenceDescription);
            
            Element imageLoader = domTree.createElement("ImageLoader");
            imageLoader.setAttribute("format", "spimreconstruction.stack.ij");
            sequenceDescription.appendChild(imageLoader);
            
            Element imageDirectory = domTree.createElement("imagedirectory");
            imageDirectory.setAttribute("type", "relative");
            imageDirectory.insertBefore(domTree.createTextNode("."),
                    imageDirectory.getLastChild());
            imageLoader.appendChild(imageDirectory);
            
            Element filePattern = domTree.createElement("filePattern");
            String patternText = baseName_ + "_TL{t}_Angle{a}.tif";
            filePattern.insertBefore(domTree.createTextNode(patternText), 
                    filePattern.getLastChild());
            imageLoader.appendChild(filePattern);
            
            String nrTimepoints = "" + mmW.getNumberOfFrames();
            Element layoutTimepoints = domTree.createElement("layoutTimepoints");
            layoutTimepoints.insertBefore(domTree.createTextNode
                  (nrTimepoints), layoutTimepoints.getLastChild() );
            imageLoader.appendChild(layoutTimepoints);
            
            // note, once we add channels, the file name pattern should also change
            String nrChannels = "0";
            Element layoutChannels = domTree.createElement("layoutChannels");
            layoutChannels.insertBefore(domTree.createTextNode
                  (nrChannels), layoutChannels.getLastChild() );
            imageLoader.appendChild(layoutChannels);
            
            String nrIlls = "0";
            Element layoutIlls = domTree.createElement("layoutIlluminations");
            layoutIlls.insertBefore(domTree.createTextNode
                  (nrIlls), layoutIlls.getLastChild() );
            imageLoader.appendChild(layoutIlls);
            
            String nrAngles = "1";
            Element layoutAngles = domTree.createElement("layoutAngles");
            layoutAngles.insertBefore(domTree.createTextNode
                  (nrAngles), layoutAngles.getLastChild() );
            imageLoader.appendChild(layoutAngles);   
            
            String imglibContainer = "ArrayImgFactory";
            Element imglib2Container = domTree.createElement("imglib2container");
            imglib2Container.insertBefore(domTree.createTextNode(imglibContainer), 
                    imglib2Container.getLastChild() );
            imageLoader.appendChild(imglib2Container);
            
            // write out the DOM to an xml file
            TransformerFactory tFactory = TransformerFactory.newInstance();
            Transformer transformer = tFactory.newTransformer();
            DOMSource source = new DOMSource(domTree);
            StreamResult result = new StreamResult(new File(targetDirectory_ +
                    File.separator + "dataset.xml"));
            transformer.transform(source, result);
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
                  MyDialogUtils.showError(cause, "Data Export Error");
               } else {
                  MyDialogUtils.showError(ex);
               }
            }
         } catch (InterruptedException ex) {
            MyDialogUtils.showError(ex, "Interrupted while exporting data");
         }
      }
   }

   private void setSaveDestinationDirectory(JTextField rootField) {
      File result = FileDialogs.openDir(null,
              "Please choose a directory root for image data",
              EXPORT_DATA_SET);
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
