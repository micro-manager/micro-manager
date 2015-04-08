
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
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import net.miginfocom.swing.MigLayout;

import org.json.JSONObject;
import org.micromanager.api.MMWindow;
import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.Data.MyStrings;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.MyDialogUtils;
import org.micromanager.asidispim.Utils.PanelUtils;
import org.micromanager.utils.FileDialogs;
import org.micromanager.utils.MDUtils;
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
   private final Prefs prefs_;
   private final JPanel exportPanel_;
   private final JPanel imageJPanel_;
   private final JTextField saveDestinationField_;
   private final JTextField baseNameField_;
   
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

      // start export sub-panel
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
      
      // end export sub-panel
      
      // start ImageJ sub-panel
      imageJPanel_ = new JPanel(new MigLayout(
              "",
              "[center]",
              "[]8[]"));
      
      imageJPanel_.setBorder(PanelUtils.makeTitledBorder("ImageJ"));
      
      JButton adjustBC = new JButton("Brightness/Contrast");
      adjustBC.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            IJCommandThread t = new IJCommandThread("Brightness/Contrast...");
            t.start();
         }
      });
      imageJPanel_.add(adjustBC, "wrap");
      
      JButton splitChannels = new JButton("Split Channels");
      splitChannels.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            IJCommandThread t = new IJCommandThread("Split Channels");
            t.start();
         }
      });
      imageJPanel_.add(splitChannels, "wrap");
      
      JButton zProjection = new JButton("Z Projection");
      zProjection.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            IJCommandThread t = new IJCommandThread("Z Project...", "projection=[Max Intensity]");
            t.start();
         }
      });
      imageJPanel_.add(zProjection, "wrap");
      
      // end ImageJ sub-panel
            
       
      this.add(exportPanel_);
      this.add(imageJPanel_);
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
            
            ImageProcessor iProc = ip.getProcessor();
            if (!mmW.getSummaryMetaData().getString("NumberOfSides").equals("2")) {
               throw new SaveTaskException("mipav export only works with two-sided data for now.");  
            }
            if (mmW.getNumberOfPositions() > 1) {
               throw new SaveTaskException("mipav export does not yet work with multiple positions");  
            }
            
            boolean usesChannels = mmW.getNumberOfChannels() > 2;  // if have channels besides two cameras
            String [] channelDirArray = new String[mmW.getNumberOfChannels()];
            if (usesChannels) {
               for (int c = 0; c < mmW.getNumberOfChannels(); c++) {
                  String chName = (String)mmW.getSummaryMetaData().getJSONArray("ChNames").get(c);
                  String colorName = chName.substring(chName.indexOf("-")+1);  // matches with AcquisitionPanel naming convention
                  channelDirArray[c] = targetDirectory_ + File.separator + baseName_ + File.separator
                        + (((c % 2) == 0) ? "SPIMA" : "SPIMB") + File.separator + colorName;
               }
            } else {
               channelDirArray[0] = targetDirectory_ + File.separator + baseName_ + 
                     File.separator + "SPIMA";
               channelDirArray[1] = targetDirectory_ + File.separator + baseName_ + 
                     File.separator + "SPIMB";
            }

            for (String dir : channelDirArray) {
               if (new File(dir).exists()) {
                     throw new SaveTaskException("Output directory already exists");
               }
            }

            for (String dir : channelDirArray) {
               new File(dir).mkdirs();
            }

            int totalNr = mmW.getNumberOfChannels() * mmW.getNumberOfFrames() * mmW.getNumberOfSlices();
            int counter = 0;
            
            for (int c = 0; c < mmW.getNumberOfChannels(); c++) {  // for each channel
               for (int t = 0; t < mmW.getNumberOfFrames(); t++) {  // for each timepoint
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
                           iProc2.rotate(((c % 2) == 1) ? 90 : -90);
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
                  ij.IJ.save(ipN, channelDirArray[c] + File.separator 
                        + (((c % 2) == 0) ? "SPIMA" : "SPIMB")
                        + "-" + t + ".tif");
               }
            }
            
         } else 
         if (exportFormat_ == 1) { // Multiview reconstruction
            String dir = targetDirectory_ + File.separator + baseName_;
            File fd = new File(dir);
            if (fd.exists()) {
               if (! MyDialogUtils.getConfirmDialogResult("Directy: " + dir + 
                       " already exists, overwrite?", 
                       JOptionPane.OK_CANCEL_OPTION) ) {
                  return null;
               }
               deleteFolder(fd);
            }
            if (! fd.mkdir()) {
               MyDialogUtils.showError("Failed to create directory: " + dir);
               return null;
            }
            ImageProcessor iProc = ip.getProcessor();
            int totalNr = mmW.getNumberOfChannels() * mmW.getNumberOfFrames() * 
                    mmW.getNumberOfSlices();
            
            // try to figure out if this was acquisition used one or two angles
            int nrAngles = 1;
            // if we had only one channel, then we used only one angle
            if (mmW.getNumberOfChannels() > 1) {
               JSONObject summaryMetadata = mmW.getSummaryMetaData();
               String key = "NumberOfSides";
               if (summaryMetadata.has(key)) {
                  int nos = summaryMetadata.getInt(key);
                  if (nos == 2) {
                     nrAngles = 2;
                  }
               }
            }
            
            int counter = 0;

            // one time point per file, one angle per file, one channel per file
            for (int c = 0; c < mmW.getNumberOfChannels(); c++) {
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
                  if (2 == nrAngles) {
                     if ((c % 2) == 0) {
                        ij.IJ.save(ipN, dir + File.separator + baseName_
                                + "_TL" + t + "_Angle0" + "_Ch" + (c / 2) + ".tif");
                     } else if ((c % 2) == 1) {
                        ij.IJ.save(ipN, dir + File.separator + baseName_
                                + "_TL" + t + "_Angle90" + "_Ch" + (c / 2) + ".tif");
                     }
                  } else {
                     ij.IJ.save(ipN, dir + File.separator + baseName_
                             + "_TL" + t + "_Angle0" + "_Ch" + c + ".tif");
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
            String patternText = baseName_ + "_TL{t}_Angle{a}_Ch{c}.tif";
            filePattern.insertBefore(domTree.createTextNode(patternText), 
                    filePattern.getLastChild());
            imageLoader.appendChild(filePattern);
            
            String nrTimepoints = "" + mmW.getNumberOfFrames();
            Element layoutTimepoints = domTree.createElement("layoutTimepoints");
            layoutTimepoints.insertBefore(domTree.createTextNode
                  (nrTimepoints), layoutTimepoints.getLastChild() );
            imageLoader.appendChild(layoutTimepoints);
            
            // note, once we add channels, the file name pattern should also change
            int nc = mmW.getNumberOfChannels();
            if (2 == nrAngles) {
               nc /= 2;
            }
            String nrChannels = "" + nc;
            Element layoutChannels = domTree.createElement("layoutChannels");
            layoutChannels.insertBefore(domTree.createTextNode
                  (nrChannels), layoutChannels.getLastChild() );
            imageLoader.appendChild(layoutChannels);
            
            String nrIlls = "0";
            Element layoutIlls = domTree.createElement("layoutIlluminations");
            layoutIlls.insertBefore(domTree.createTextNode
                  (nrIlls), layoutIlls.getLastChild() );
            imageLoader.appendChild(layoutIlls);
            
            String na = "" + nrAngles;
            Element layoutAngles = domTree.createElement("layoutAngles");
            layoutAngles.insertBefore(domTree.createTextNode
                  (na), layoutAngles.getLastChild() );
            imageLoader.appendChild(layoutAngles);   
            
            String imglibContainer = "ArrayImgFactory";
            Element imglib2Container = domTree.createElement("imglib2container");
            imglib2Container.insertBefore(domTree.createTextNode(imglibContainer), 
                    imglib2Container.getLastChild() );
            imageLoader.appendChild(imglib2Container);
            
            Element viewSetups = domTree.createElement("ViewSetups");
            sequenceDescription.appendChild(viewSetups);
            
            JSONObject summary = mmW.getSummaryMetaData();
            // workaround bug: z step is sometimes only present in per image data
            JSONObject imageTags = mmW.getImageMetadata(0,0,0,0);
            for (int angle = 0; angle < nrAngles; angle++) {
               Element viewSetup = createViewSetup (domTree, angle, 
                       MDUtils.getPixelSizeUm(summary), 
                       MDUtils.getPixelSizeUm(summary),
                       MDUtils.getZStepUm(imageTags),
                      "um");
               viewSetups.appendChild(viewSetup);
            }
            
            Element attrs = createAttributes(domTree, "illumination");
            Element attr = createAttribute(domTree, "Illumination", "0", "0");
            attrs.appendChild(attr);
            viewSetups.appendChild(attrs);
            attrs = createAttributes(domTree, "channel");
            attr = createAttribute(domTree, "Channel", "0", "0");
            attrs.appendChild(attr);
            viewSetups.appendChild(attrs);
            attrs = createAttributes(domTree, "angle");
            attr = createAttribute(domTree, "Angle", "0", "0");
            attrs.appendChild(attr);
            attr = createAttribute(domTree, "Angle", "1", "90");
            attrs.appendChild(attr);
            viewSetups.appendChild(attrs);
            
            Element timePoints = domTree.createElement("Timepoints");
            timePoints.setAttribute("type", "pattern");
            Element intP = domTree.createElement("integerpattern");
            intP.insertBefore(domTree.createTextNode("0-" + (mmW.getNumberOfFrames() -1) ), 
                    intP.getLastChild() );
            timePoints.appendChild(intP);
            sequenceDescription.appendChild(timePoints);
            
            Element viewRegistrations = domTree.createElement("ViewRegistrations");
            for (int t = 0; t < mmW.getNumberOfFrames(); t++) {
               for (int angle = 0; angle < 2; angle++) {
                  Element viewRegistration = getViewRegistration(domTree, t,
                          angle, MDUtils.getPixelSizeUm(summary),
                          MDUtils.getZStepUm(imageTags), true);
                  viewRegistrations.appendChild(viewRegistration);
               }
            }
            spimData.appendChild(viewRegistrations);
            
            Element viewInterestPoints = domTree.createElement("ViewInterestPoints");
            spimData.appendChild(viewInterestPoints);
            
            // write out the DOM to an xml file
            TransformerFactory tFactory = TransformerFactory.newInstance();
            Transformer transformer = tFactory.newTransformer();
            //transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            //transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, 
            //        "-//W3C//DTD XHTML 1.0 Transitional//EN");
            //transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, 
            //        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd");
            DOMSource source = new DOMSource(domTree);
            StreamResult result = new StreamResult(new File(dir +
                    File.separator + "dataset.xml"));
            transformer.transform(source, result);
         }
      return null;
      }
      
      private Element createViewSetup(Document dom, int angle, double x, double y,
                       double z, String unit) {
         Element el = dom.createElement("ViewSetup");
         
         Element id = dom.createElement("id");
         id.insertBefore(dom.createTextNode("" + angle), 
            id.getLastChild() );
         el.appendChild(id);
         
         Element voxelSize = dom.createElement("voxelSize");
         el.appendChild(voxelSize);
         
         Element u = dom.createElement("unit");
         u.insertBefore(dom.createTextNode("um"), 
                    u.getLastChild() );
         voxelSize.appendChild(u);
         Element size = dom.createElement("size");
         size.insertBefore(dom.createTextNode("" + Double.toString(x) + " " +
                 Double.toString(y) + " " + Double.toString(z) ), 
                 size.getLastChild() );
         voxelSize.appendChild(size);
         
         Element attributes = dom.createElement("attributes");
         el.appendChild(attributes);
         Element ill = dom.createElement("illumination");
         ill.insertBefore(dom.createTextNode("0"), ill.getLastChild() );
         attributes.appendChild(ill);
         Element ch = dom.createElement("channel");
         ch.insertBefore(dom.createTextNode("0"), ch.getLastChild() );
         attributes.appendChild(ch);
         Element a = dom.createElement("angle");
         a.insertBefore(dom.createTextNode("" + angle), a.getLastChild() );
         attributes.appendChild(a);
         
         return el;
      }
      
      private Element createAttribute (Document dom, String type, String id, 
              String name) {
         Element attr = dom.createElement(type);
         Element idElement = dom.createElement("id");
         idElement.insertBefore(dom.createTextNode(id), idElement.getLastChild() );
         attr.appendChild(idElement);
         Element nameElement = dom.createElement("name");
         nameElement.insertBefore(dom.createTextNode(name), nameElement.getLastChild() );
         attr.appendChild(nameElement);
         
         return attr;
      }
      
      private Element createAttributes (Document dom, String name) {
         Element attr = dom.createElement("Attributes");
         attr.setAttribute("name", name);
         return attr;
      }

      private Element getViewRegistration(Document dom, int t, int angle, 
              double xUm, double zUm, boolean rotateY90) {
         Element elvr = dom.createElement("ViewRegistration");
         elvr.setAttribute("timepoint" , "" + t);
         elvr.setAttribute("setup", "" + angle);
         
         if (angle == 1 && rotateY90) {
            Element elvt2 = dom.createElement("ViewTransform");
            elvt2.setAttribute("type", "affine");
            elvr.appendChild(elvt2);
            Element name2 = dom.createElement("Name");
            name2.insertBefore(dom.createTextNode("Manually defined transformation "
                    + "(Rotation around y-axis by 90.0 degrees)"),
                    name2.getLastChild());
            elvt2.appendChild(name2);
            Element affine2 = dom.createElement("affine");
            String transform2 = "6.123233995736766E-17 0.0 1.0 0.0 0.0 1.0 0.0 0.0 -1.0 0.0 6.123233995736766E-17 0.0";
            affine2.insertBefore(dom.createTextNode(transform2), affine2.getLastChild());
            elvt2.appendChild(affine2);
         }
         
         Element elvt = dom.createElement("ViewTransform");
         elvt.setAttribute("type", "affine");
         elvr.appendChild(elvt);
         Element name = dom.createElement("Name");
         name.insertBefore(dom.createTextNode("calibration"), name.getLastChild());
         elvt.appendChild(name);
         Element affine = dom.createElement("affine");
         String transform = "1.0 0.0 0.0 0.0 0.0 1.0 0.0 0.0 0.0 0.0 " + 
                 zUm/xUm + " 0.0";
         affine.insertBefore(dom.createTextNode(transform), affine.getLastChild());
         elvt.appendChild(affine);

         return elvr;
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
   
   /**
    * Since java 1.6 does not seem to have this functionality....
    * @param folder folder to be deleted
    */
   public static void deleteFolder(File folder) {
      File[] files = folder.listFiles();
      if (files != null) { 
         for (File f : files) {
            if (f.isDirectory()) {
               deleteFolder(f);
            } else {
               f.delete();
            }
         }
      }
      folder.delete();
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
   
   /**
    * Make it easy to execute an ImageJ command in its own thread (for speed).
    * After creating this object with the command (menu item) then call its start() method.
    * TODO: see if this would be faster using ImageJ's Executer class (http://rsb.info.nih.gov/ij/developer/api/ij/Executer.html)
    * @author Jon
    */
   class IJCommandThread extends Thread {
      private final String command_;
      private final String args_;
      IJCommandThread(String command) {
         super(command);
         command_ = command;
         args_ = "";
      }
      IJCommandThread(String command, String args) {
         super(command);
         command_ = command;
         args_ = args;
      }
      @Override
      public void run() {
         IJ.run(command_, args_);
      }
   }
  
}
