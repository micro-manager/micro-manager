/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package MMCustomization;

import com.imaging100x.twophoton.SettingsDialog;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JButton;
import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.AcqControlDlg;
import org.micromanager.AcquisitionEngine2010;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.acquisition.*;
import org.micromanager.api.AcquisitionEngine;
import org.micromanager.api.IAcquisitionEngine2010;
import org.micromanager.api.ImageCache;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Henry
 */
public class AcquisitionEngineOverride {
   
   private MMStudioMainFrame gui_;
   private IAcquisitionEngine2010 acqEng_;
   private AcqControlDlg mdaWindow_;
   private AcquisitionWrapperEngine acqWrapperEngine_;
   private Preferences prefs_;
   
   
   //For now this class stores its own acquisitionEngine and takes control of the MDA window acquire button
   //Acquisition settings are still stored in the AcquisitionWrapperEngine, which implements AcquisitionEngine
   //Once MM gets rid of AcquisitionWrapperEngine, will need to modify this class
   public AcquisitionEngineOverride(Runnable depthListRunnable, Preferences prefs) {
      prefs_ = prefs;
      //Take over control of the MDA window acquire button
      gui_ = MMStudioMainFrame.getInstance();
      mdaWindow_ = gui_.getAcqDlg();
      try {
         JButton acquireButton = (JButton) 
                 JavaUtils.getRestrictedFieldValue(mdaWindow_, AcqControlDlg.class, "acquireButton_");
         acquireButton.removeActionListener( acquireButton.getActionListeners()[0] );
         acquireButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               acquire();
            }
         } );
      } catch (NoSuchFieldException ex) {
         ReportingUtils.showError("Couldn't get acquire button reference");
      }

      acqEng_ = new AcquisitionEngine2010(gui_.getCore());
      acqEng_.attachRunnable(-1,-1,-1,-1, depthListRunnable);
      acqWrapperEngine_ = (AcquisitionWrapperEngine) gui_.getAcquisitionEngine();
   }
  
   private void acquire() {
      //check if previous acquisition is running
      if (acqEng_.isRunning()) {
         ReportingUtils.showError("Cannot start acquisition: previous acquisition still in progress.");
         return;
      }
      
      //call applySettings, which sends MDA window settings to AcquisitionWrapperEngine
      try {
         JavaUtils.invokeRestrictedMethod(mdaWindow_, AcqControlDlg.class, "applySettings");
      } catch (Exception ex) {
         ReportingUtils.showError("Couldn't invoke applySettings()");
      }
      
      //get sequence settings (from acquisitionwrapper engine)
      SequenceSettings settings = acqWrapperEngine_.getSequenceSettings();
      
      //check for enough memory
      if (!enoughMemory(settings)) {
         return;
      } 
      try {
         runAcquisition(settings);
      } catch (Exception e) {
         ReportingUtils.showError("Couldn't run acquisition");
      }
   }
 
   private void runAcquisition(SequenceSettings settings) throws Exception {
      // Start up the acquisition engine
      BlockingQueue<TaggedImage> engineOutputQueue = acqEng_.run(settings, true, gui_.getPositionList(), null);
      JSONObject summaryMetadata = acqEng_.getSummaryMetadata();

      // Set up the DataProcessor<TaggedImage> sequence--no data processors for now
      // BlockingQueue<TaggedImage> procStackOutputQueue = ProcessorStack.run(engineOutputQueue, imageProcessors);


      // create storage
      TaggedImageStorage storage;
      try {
         if (settings.save) {
            String acqDirectory = createAcqDirectory(summaryMetadata.getString("Directory"), summaryMetadata.getString("Prefix"));
            summaryMetadata.put("Prefix", acqDirectory);
            String acqPath = summaryMetadata.getString("Directory") + File.separator + acqDirectory;
            storage = new TaggedImageStorageMultipageTiff(acqPath, true, summaryMetadata);
         } else {
            storage = new TaggedImageStorageRam(summaryMetadata);
         }
         
         MMImageCache imageCache = new MMImageCache(storage);
         imageCache.setSummaryMetadata(summaryMetadata);
         
         if (MDUtils.getNumPositions(summaryMetadata) > 1) {
            //copy summary metadata
            JSONObject summaryMetadata2 = new JSONObject(summaryMetadata.toString());
            //TODO change these parameters
            TaggedImageStorage stitchedImageStorage = new StitchedImageStorageImaris(summaryMetadata2,
                    prefs_.getBoolean(SettingsDialog.INVERT_X, false), prefs_.getBoolean(SettingsDialog.INVERT_Y, false),
                    prefs_.getBoolean(SettingsDialog.SWAP_X_AND_Y, false), 3, prefs_.get(SettingsDialog.STITCHED_DATA_DIRECTORY, ""));

            
            
            ImageCache stitchedCache = new MMImageCache(stitchedImageStorage) {
               //Override this method to change the position index of the last tags copy
               //that is stored within the imagecache to prevent the position slider on the stiched
               //display form appearing
               @Override
               public JSONObject getLastImageTags() {
                  try {
                     JSONObject lastTags = (JSONObject) JavaUtils.getRestrictedFieldValue(this, MMImageCache.class, "lastTags_");
                     JSONObject lastTagsCopy = new JSONObject(lastTags.toString());
                     lastTagsCopy.put("PositionIndex", 0);
                     return lastTagsCopy;
                  } catch (Exception ex) {
                     ReportingUtils.showError("Couldnt steal last image tags");
                     return null;
                  }
               }
            };
            stitchedCache.setSummaryMetadata(summaryMetadata);
            

            DisplayPlus stitchedDisplay = new DisplayPlus(stitchedCache);

            

            DefaultTaggedImageSink sink = new DefaultTaggedImageSink(engineOutputQueue, stitchedCache);
            sink.start();
         } else {
            //single position acquisition--run as normal

            //create display and hook into imagecache as listener
            imageCache.addImageCacheListener(new VirtualAcquisitionDisplay(imageCache, (AcquisitionEngine) null));
            //TODO create adapter so old acquisition engine can be passed to virtual acquisition display
         }

         
         // Start pumping images into the ImageCache
//         DefaultTaggedImageSink sink = new DefaultTaggedImageSink(engineOutputQueue, imageCache);                 
      } catch (IOException ex) {
         ReportingUtils.showError("Couldn't create image storage or start acquisition");
      }
   }

   private boolean enoughMemory(SequenceSettings settings) {
      return true;
   }
      // if (saveFiles_) {
//         File root = new File(rootName_);
//         if (!root.canWrite()) {
//            int result = JOptionPane.showConfirmDialog(null, "The specified root directory\n" + root.getAbsolutePath() +"\ndoes not exist. Create it?", "Directory not found.", JOptionPane.YES_NO_OPTION);
//            if (result == JOptionPane.YES_OPTION) {
//               root.mkdirs();
//               if (!root.canWrite()) {
//                  ReportingUtils.showError("Unable to save data to selected location: check that location exists.\nAcquisition canceled.");
//                  return null;
//               }
//            } else {
//               ReportingUtils.showMessage("Acquisition canceled.");
//               return null;
//            }
//         } else if (!this.enoughDiskSpace()) {
//            ReportingUtils.showError("Not enough space on disk to save the requested image set; acquisition canceled.");
//            return null;
//         }
//      }
   
   
   //Copied from MMAcquisition
   private String createAcqDirectory(String root, String prefix) throws Exception {
      File rootDir = JavaUtils.createDirectory(root);
      int curIndex = getCurrentMaxDirIndex(rootDir, prefix + "_");
      return prefix + "_" + (1 + curIndex);
   }

   private int getCurrentMaxDirIndex(File rootDir, String prefix) throws NumberFormatException {
      int maxNumber = 0;
      int number;
      String theName;
      for (File acqDir : rootDir.listFiles()) {
         theName = acqDir.getName();
         if (theName.startsWith(prefix)) {
            try {
               //e.g.: "blah_32.ome.tiff"
               Pattern p = Pattern.compile("\\Q" + prefix + "\\E" + "(\\d+).*+");
               Matcher m = p.matcher(theName);
               if (m.matches()) {
                  number = Integer.parseInt(m.group(1));
                  if (number >= maxNumber) {
                     maxNumber = number;
                  }
               }
            } catch (NumberFormatException e) {
            } // Do nothing.
         }
      }
      return maxNumber;
   }
   
}
