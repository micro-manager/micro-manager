package org.micromanager.acquisition;

import org.micromanager.api.ImageCacheListener;
import ij.ImageStack;
import ij.process.LUT;
import java.awt.event.AdjustmentEvent;
import ij.CompositeImage;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.gui.ScrollbarWithLabel;
import ij.gui.StackWindow;
import ij.io.FileInfo;
import ij.measure.Calibration;
import ij.plugin.Animator;
import ij.process.ImageProcessor;
import java.awt.Color;
import java.awt.event.AdjustmentListener;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.api.AcquisitionEngine;
import org.micromanager.api.ImageCache;
import org.micromanager.utils.FileDialogs;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public final class VirtualAcquisitionDisplay implements ImageCacheListener {

   public static VirtualAcquisitionDisplay getDisplay(ImagePlus imgp) {
      ImageStack stack = imgp.getStack();
      if (stack instanceof AcquisitionVirtualStack) {
         return ((AcquisitionVirtualStack) stack).getVirtualAcquisitionDisplay();
      } else {
         return null;
      }
   }
   final static Color[] rgb = {Color.red, Color.green, Color.blue};
   final ImageCache imageCache_;
   final private ImagePlus hyperImage_;
   final private HyperstackControls hc_;
   final AcquisitionVirtualStack virtualStack_;
   final private ScrollbarWithLabel pSelector_;
   final private ScrollbarWithLabel tSelector_;
   final private ScrollbarWithLabel zSelector_;
   final private MMImagePlus mmImagePlus_;
   final private int numComponents_;
   private AcquisitionEngine eng_;
   private boolean finished_ = false;
   private boolean promptToSave_ = true;
   private final String name_;
   private long lastDisplayTime_;
   private JSONObject lastDisplayTags_;
   private boolean updating_ = false;
   private int[] channelInitiated_;


   /* This interface and the following two classes
    * allow us to manipulate the dimensions
    * in an ImagePlus without it throwing conniptions.
    */
   public interface IMMImagePlus {

      public int getNChannelsUnverified();

      public int getNSlicesUnverified();

      public int getNFramesUnverified();

      public void setNChannelsUnverified(int nChannels);

      public void setNSlicesUnverified(int nSlices);

      public void setNFramesUnverified(int nFrames);
   }

   public class MMCompositeImage extends CompositeImage implements IMMImagePlus {

      MMCompositeImage(ImagePlus imgp, int type) {
         super(imgp, type);
      }

      @Override
      public ImageProcessor getProcessor() {
         if (super.getMode() == CompositeImage.COMPOSITE )
            return super.getProcessor();
         ImageProcessor ip = super.getProcessor();
         ip.setPixels( getImageStack().getProcessor( 
                 getStackIndex(getChannel(),getSlice(),getFrame()) ).getPixels());
         return ip;
      }
      
      @Override
      public int getImageStackSize() {
         return super.nChannels * super.nSlices * super.nFrames;
      }

      @Override
      public int getStackSize() {
         return getImageStackSize();
      }

      @Override
      public int getNChannelsUnverified() {
         return super.nChannels;
      }

      @Override
      public int getNSlicesUnverified() {
         return super.nSlices;
      }

      @Override
      public int getNFramesUnverified() {
         return super.nFrames;
      }

      @Override
      public void setNChannelsUnverified(int nChannels) {
         super.nChannels = nChannels;
      }

      @Override
      public void setNSlicesUnverified(int nSlices) {
         super.nSlices = nSlices;
      }

      @Override
      public void setNFramesUnverified(int nFrames) {
         super.nFrames = nFrames;
      }
   }

   public class MMImagePlus extends ImagePlus implements IMMImagePlus {

      MMImagePlus(String title, ImageStack stack) {
         super(title, stack);
      }

      @Override
      public int getImageStackSize() {
         return super.nChannels * super.nSlices * super.nFrames;
      }

      @Override
      public int getStackSize() {
         return getImageStackSize();
      }

      @Override
      public int getNChannelsUnverified() {
         return super.nChannels;
      }

      @Override
      public int getNSlicesUnverified() {
         return super.nSlices;
      }

      @Override
      public int getNFramesUnverified() {
         return super.nFrames;
      }

      @Override
      public void setNChannelsUnverified(int nChannels) {
         super.nChannels = nChannels;
      }

      @Override
      public void setNSlicesUnverified(int nSlices) {
         super.nSlices = nSlices;
      }

      @Override
      public void setNFramesUnverified(int nFrames) {
         super.nFrames = nFrames;
      }
   }

   public VirtualAcquisitionDisplay(ImageCache imageCache, AcquisitionEngine eng) {
      this(imageCache, eng, "Untitled");
   }

   public VirtualAcquisitionDisplay(ImageCache imageCache, AcquisitionEngine eng, String name) {
      name_ = name;
      imageCache_ = imageCache;
      eng_ = eng;
      pSelector_ = createPositionScrollbar();

      JSONObject summaryMetadata = getSummaryMetadata();
      int numSlices = 1;
      int numFrames = 1;
      int numChannels = 1;
      int numGrayChannels;
      int numPositions = 0;
      int width = 0;
      int height = 0;
      int numComponents = 1;
      try {
         width = MDUtils.getWidth(summaryMetadata);
         height = MDUtils.getHeight(summaryMetadata);
         numSlices = Math.max(summaryMetadata.getInt("Slices"), 1);
         numFrames = Math.max(summaryMetadata.getInt("Frames"), 1);
         //numFrames = Math.max(Math.min(2,summaryMetadata.getInt("Frames")), 1);
         numChannels = Math.max(summaryMetadata.getInt("Channels"), 1);
         numPositions = Math.max(summaryMetadata.getInt("Positions"), 0);
         numComponents = Math.max(MDUtils.getNumberOfComponents(summaryMetadata), 1);
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
      numComponents_ = numComponents;
      numGrayChannels = numComponents_ * numChannels;

      channelInitiated_ = new int[numGrayChannels];

      if (imageCache_.getDisplayAndComments() == null || imageCache_.getDisplayAndComments().isNull("Channels")) {
         imageCache_.setDisplayAndComments(getDisplaySettingsFromSummary(summaryMetadata));
      }

      int type = 0;
      try {
         type = MDUtils.getSingleChannelType(summaryMetadata);
      } catch (Exception ex) {
         ReportingUtils.showError(ex, "Unable to determine acquisition type.");
      }
      virtualStack_ = new AcquisitionVirtualStack(width, height, type, null,
              imageCache, 1 /* numGrayChannels * numSlices * numFrames */, this);
      if (summaryMetadata.has("PositionIndex")) {
         try {
            virtualStack_.setPositionIndex(MDUtils.getPositionIndex(summaryMetadata));
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
         }
      }

      hc_ = new HyperstackControls(this);
      mmImagePlus_ = createMMImagePlus(virtualStack_);
      hyperImage_ = createHyperImage(numGrayChannels, numSlices, numFrames, virtualStack_, hc_);
      applyPixelSizeCalibration(hyperImage_);
      createWindow(hyperImage_, hc_);
      tSelector_ = getTSelector();
      zSelector_ = getZSelector();
      if (imageCache_.lastAcquiredFrame() > 1) {
         setNumFrames(1 + imageCache_.lastAcquiredFrame());
      } else {
         setNumFrames(1);
      }
      setNumSlices(numSlices);
      setNumPositions(numPositions);
      for (int i = 0; i < numGrayChannels; ++i) {
         updateChannelLUT(i);
         updateChannelContrast(i);
      }
      updateAndDraw();
      updateWindow();
   }

   /**
    * Allows bypassing the prompt to Save
    * @param promptToSave boolean flag
    */
   public void promptToSave(boolean promptToSave) {
      promptToSave_ = promptToSave;
   }

   /*
    * Method required by ImageCacheListener
    */
   @Override
   public void imageReceived(final TaggedImage taggedImage) {
      updateDisplay(taggedImage, false);
      updateAndDraw();
   }

   /*
    * Method required by ImageCacheListener
    */
   @Override
   public void imagingFinished(String path) {
      updateDisplay(null, true);
      updateAndDraw();
      updateWindow();
   }

   private void updateDisplay(TaggedImage taggedImage, boolean finalUpdate) {
      try {
         long t = System.currentTimeMillis();
         JSONObject tags;
         if (taggedImage != null) {
            tags = taggedImage.tags;
         } else {
            tags = imageCache_.getLastImageTags();
         }
         if (finalUpdate || (MDUtils.getFrameIndex(tags) == 0) || (Math.abs(t - lastDisplayTime_) > 30)) {
            if (tags != null /*&& tags != lastDisplayTags_*/) {
               showImage(tags, true);
               lastDisplayTags_ = tags;
            }
            lastDisplayTime_ = t;
         }
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
   }

   private ScrollbarWithLabel getTSelector() {
      ScrollbarWithLabel tSelector = null;
      ImageWindow win = hyperImage_.getWindow();
      if (win instanceof StackWindow) {
         try {
            tSelector = (ScrollbarWithLabel) JavaUtils.getRestrictedFieldValue((StackWindow) win, StackWindow.class, "tSelector");
         } catch (NoSuchFieldException ex) {
            tSelector = null;
            ReportingUtils.logError(ex);
         }
      }
      if (tSelector == null) {
         try {
            tSelector = (ScrollbarWithLabel) JavaUtils.getRestrictedFieldValue((StackWindow) win, StackWindow.class, "animationSelector");
         } catch (NoSuchFieldException ex) {
            tSelector = null;
            ReportingUtils.logError(ex);
         }
      }
      return tSelector;
   }

   private ScrollbarWithLabel getZSelector() {
      ScrollbarWithLabel zSelector = null;
      ImageWindow win = hyperImage_.getWindow();
      if (win instanceof StackWindow) {
         try {
            zSelector = (ScrollbarWithLabel) JavaUtils.getRestrictedFieldValue((StackWindow) win, StackWindow.class, "zSelector");
         } catch (NoSuchFieldException ex) {
            zSelector = null;
            ReportingUtils.logError(ex);
         }
      }
      if (zSelector == null) {
         try {
            zSelector = (ScrollbarWithLabel) JavaUtils.getRestrictedFieldValue((StackWindow) win, StackWindow.class, "animationSelector");
         } catch (NoSuchFieldException ex) {
            zSelector = null;
            ReportingUtils.logError(ex);
         }
      }
      return zSelector;
   }

   public int rgbToGrayChannel(int channelIndex) {
      try {
         if (MDUtils.getNumberOfComponents(imageCache_.getSummaryMetadata()) == 3) {
            return channelIndex * 3;
         }
         return channelIndex;
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return 0;
      }
   }

   public int grayToRGBChannel(int grayIndex) {
      try {
         if (MDUtils.getNumberOfComponents(imageCache_.getSummaryMetadata()) == 3) {
            return grayIndex / 3;
         }
         return grayIndex;
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return 0;
      }
   }

   public static JSONObject getDisplaySettingsFromSummary(JSONObject summaryMetadata) {
      try {
         JSONObject displaySettings = new JSONObject();
         JSONArray chColors;
         JSONArray chNames = MDUtils.getJSONArrayMember(summaryMetadata, "ChNames");
         try {
            chColors = MDUtils.getJSONArrayMember(summaryMetadata, "ChColors");
         } catch (JSONException ex) {
            int[] defaultColors = {Color.RED.getRGB(),
               Color.GREEN.getRGB(),
               Color.BLUE.getRGB()};
            chColors = new JSONArray();
            for (int i = 0; i < chNames.length(); i++) {
               chColors.put(defaultColors[i % defaultColors.length]);
            }
            summaryMetadata.put("ChColors", chColors);
         }
         JSONArray chMaxes;
         try {
            chMaxes = MDUtils.getJSONArrayMember(summaryMetadata, "ChContrastMax");
         } catch (JSONException ex) {
            chMaxes = new JSONArray();
            for (int i = 0; i < chNames.length(); i++) {
               chMaxes.put(256);
            }
            summaryMetadata.put("ChContrastMax", chMaxes);
         }
         JSONArray chMins;
         try {
            chMins = MDUtils.getJSONArrayMember(summaryMetadata, "ChContrastMin");
         } catch (JSONException ex) {
            chMins = new JSONArray();
            for (int i = 0; i < chNames.length(); i++) {
               chMins.put(0);
            }
            summaryMetadata.put("ChContrastMin", chMins);
         }
         int numComponents = 1;
         try {
            numComponents = MDUtils.getNumberOfComponents(summaryMetadata);
         } catch (JSONException ex) {
         }

         JSONArray channels = new JSONArray();
         for (int k = 0; k < chNames.length(); ++k) {
            String name = (String) chNames.get(k);
            int color = chColors.getInt(k);
            int min = chMins.getInt(k);
            int max = chMaxes.getInt(k);
            for (int component = 0; component < numComponents; ++component) {
               JSONObject channelObject = new JSONObject();
               if (numComponents == 1) {
                  channelObject.put("Color", color);
               } else {
                  channelObject.put("Color", rgb[component].getRGB());
               }
               channelObject.put("Name", name);
               channelObject.put("Gamma", 1.0);
               channelObject.put("Min", min);
               channelObject.put("Max", max);
               channels.put(channelObject);
            }
         }
         if (chNames.length() == 0) {
            for (int component = 0; component < numComponents; ++component) {
               JSONObject channelObject = new JSONObject();
               if (numComponents == 1) {
                  channelObject.put("Color", Color.white);
               } else {
                  channelObject.put("Color", rgb[component].getRGB());
               }
               channelObject.put("Name", "Default");
               channelObject.put("Gamma", 1.0);
               channels.put(channelObject);
            }
         }
         displaySettings.put("Channels", channels);

         JSONObject comments = new JSONObject();
         String summary = "";
         try {
            summary = summaryMetadata.getString("Comment");
         } catch (JSONException ex) {
            summaryMetadata.put("Comment", "");
         }
         comments.put("Summary", summary);
         displaySettings.put("Comments", comments);
         return displaySettings;
      } catch (Exception e) {
         ReportingUtils.logError(e);
         return null;
      }
   }

   /**
    * Sets ImageJ pixel size calibration
    * @param hyperImage
    */
   private void applyPixelSizeCalibration(final ImagePlus hyperImage) {
      try {
         double pixSizeUm = getSummaryMetadata().getDouble("PixelSize_um");
         if (pixSizeUm > 0) {
            Calibration cal = new Calibration();
            cal.setUnit("um");
            cal.pixelWidth = pixSizeUm;
            cal.pixelHeight = pixSizeUm;
            hyperImage.setCalibration(cal);
         }
      } catch (JSONException ex) {
         // no pixelsize defined.  Nothing to do
      }
   }

   private void setNumPositions(int n) {
      pSelector_.setMinimum(0);
      pSelector_.setMaximum(n);
      ImageWindow win = hyperImage_.getWindow();
      if (n > 1 && pSelector_.getParent() == null) {
         win.add(pSelector_, win.getComponentCount() - 1);
      } else if (n <= 1 && pSelector_.getParent() != null) {
         win.remove(pSelector_);
      }
      win.pack();
   }

   private void setNumFrames(int n) {
      if (tSelector_ != null) {
         //ImageWindow win = hyperImage_.getWindow();
         ((IMMImagePlus) hyperImage_).setNFramesUnverified(n);
         tSelector_.setMaximum(n + 1);
         // JavaUtils.setRestrictedFieldValue(win, StackWindow.class, "nFrames", n);
      }
   }

   private void setNumSlices(int n) {
      if (zSelector_ != null) {
         ((IMMImagePlus) hyperImage_).setNSlicesUnverified(n);
         zSelector_.setMaximum(n + 1);
      }
   }

   public ImagePlus getHyperImage() {
      return hyperImage_;
   }

   public int getStackSize() {
      if (hyperImage_ != null) {
         return getNumChannels() * getNumSlices() * getNumFrames();
      } else if (mmImagePlus_ != null) {
         return mmImagePlus_.getStackSize();
      } else {
         return 2;
      }
   }

   public void updateAndDraw() {
      if (!updating_) {
         updating_ = true;
         if (hyperImage_ instanceof CompositeImage) {
            ((CompositeImage) hyperImage_).setChannelsUpdated();
         }
         hyperImage_.updateAndDraw();
         updating_ = false;
      }
   }

   public void updateWindow() {
      if (hc_ == null) {
         return;
      }

      String status = "";
      final AcquisitionEngine eng = eng_;

      if (eng != null) {
         if (acquisitionIsRunning()) {
            if (!abortRequested()) {
               hc_.enableAcquisitionControls(true);
               if (isPaused()) {
                  status = "paused";
               } else {
                  status = "running";
               }
            } else {
               hc_.enableAcquisitionControls(false);
               status = "interrupted";
            }
         } else {
            hc_.enableAcquisitionControls(false);
            if (!status.contentEquals("interrupted")) {
               if (eng.isFinished()) {
                  status = "finished";
                  eng_ = null;
               }
            }
         }
         status += ", ";
         if (eng.isFinished()) {
            eng_ = null;
            finished_ = true;
         }
      } else {
         if (finished_ == true) {
            status = "finished, ";
         }
         hc_.enableAcquisitionControls(false);
      }
      if (isDiskCached()) {
         status += "on disk";
      } else {
         status += "not yet saved";
      }

      hc_.enableShowFolderButton(imageCache_.getDiskLocation() != null);
      String path = isDiskCached()
              ? new File(imageCache_.getDiskLocation()).getName() : name_;
      if (hyperImage_.isVisible()) {
         hyperImage_.getWindow().setTitle(path + " (" + status + ")");
      }

   }

   /**
    * Displays tagged image in the multi-D viewer
    * Will wait for the screen update
    *      
    * @param taggedImg
    * @throws Exception 
    */
   
   public void showImage(TaggedImage taggedImg) throws Exception {
      showImage(taggedImg, true);
   }
   
   /**
    * Displays tagged image in the multi-D viewer
    * Optionally waits for the display to draw the image
    *     * 
    * @param taggedImg
    * @throws Exception 
    */
   public void showImage(TaggedImage taggedImg, boolean waitForDisplay) throws Exception {
      showImage(taggedImg.tags, waitForDisplay);
   }


   public void showImage(JSONObject md, boolean waitForDisplay) throws Exception {
      updateWindow();
      if (md == null) {
         return;
      }
      int channel = MDUtils.getChannelIndex(md);
      int frame = MDUtils.getFrameIndex(md);
      int position = MDUtils.getPositionIndex(md);
      int slice = MDUtils.getSliceIndex(md);
      int superChannel = this.rgbToGrayChannel(MDUtils.getChannelIndex(md));
      if (hyperImage_.getClass().equals(MMCompositeImage.class)) {
         boolean[] active = ((MMCompositeImage) hyperImage_ ).getActiveChannels();
         for (int k = 0; k < active.length; k++) {
          if (active[k]) {
             superChannel += k;           //allows selected channel to persist in 
             break;                    //Composite or Grayscale display modes in RGB live mode
          }  
         }
      }
         
      
      if (frame == 0) {
         try {
            TaggedImage image = imageCache_.getImage(superChannel, slice, frame, position);
            if (image != null) {
               Object pix = image.pix;
               int pixelMin = ImageUtils.getMin(pix);
               int pixelMax = ImageUtils.getMax(pix);
               if (slice > 0) {
                  pixelMin = Math.min(this.getChannelMin(superChannel), pixelMin);
                  pixelMax = Math.max(this.getChannelMax(superChannel), pixelMax);
               }
               if (MDUtils.isRGB(md)) {
                  for (int i = 0; i < 3; ++i) {
                     setChannelDisplayRange(superChannel + i, pixelMin, pixelMax);
                  }
               } else {
                  setChannelDisplayRange(superChannel, pixelMin, pixelMax);
               }
            }
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
            throw(ex);
         }
      }

      if (tSelector_ != null) {
         if (tSelector_.getMaximum() <= (1 + frame)) {
            this.setNumFrames(1 + frame);
         }
      }

      try {
         int p = 1 + MDUtils.getPositionIndex(md);
         if (p >= getNumPositions()) {
            setNumPositions(p);
         }
         setPosition(MDUtils.getPositionIndex(md));
         if (MDUtils.isRGB(md)) {
            hyperImage_.setPosition(1 + superChannel,
                    1 + MDUtils.getSliceIndex(md),
                    1 + MDUtils.getFrameIndex(md));
         } else {
            hyperImage_.setPositionWithoutUpdate(1 + channel,
                    1 + MDUtils.getSliceIndex(md),
                    1 + MDUtils.getFrameIndex(md));
         }
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }

      // Make sure image is shown if it is a single plane:
      if (hyperImage_.getStackSize() == 1) {
         hyperImage_.getProcessor().setPixels(
                 hyperImage_.getStack().getPixels(1));
      }
      
      
      Runnable updateAndDraw = new Runnable() {

         public void run() {
            if (hyperImage_ instanceof CompositeImage) {
               ((CompositeImage) hyperImage_).setChannelsUpdated();
            }
            if (hyperImage_ != null && hyperImage_.isVisible())
            hyperImage_.updateAndDraw();
         }
      };

      if (!SwingUtilities.isEventDispatchThread()) {
         if (waitForDisplay) {
            SwingUtilities.invokeAndWait(updateAndDraw);
         } else {
            SwingUtilities.invokeLater(updateAndDraw);
         }
      } else {
         updateAndDraw();
      }

   }

   private void updatePosition(int p) {
      virtualStack_.setPositionIndex(p);
      if (!hyperImage_.isComposite()) {
         Object pixels = virtualStack_.getPixels(hyperImage_.getCurrentSlice());
         hyperImage_.getProcessor().setPixels(pixels);
      }
      updateAndDraw();
   }

   public void setPosition(int p) {
      pSelector_.setValue(p);
   }

   boolean pause() {
      if (eng_ != null) {
         if (eng_.isPaused()) {
            eng_.setPause(false);
         } else {
            eng_.setPause(true);
         }
         updateWindow();
         return (eng_.isPaused());
      }
      return false;
   }

   boolean abort() {
      if (eng_ != null) {
         if (eng_.abortRequest()) {
            updateWindow();
            return true;
         }
      }
      return false;
   }

   public boolean acquisitionIsRunning() {
      if (eng_ != null) {
         return eng_.isAcquisitionRunning();
      } else {
         return false;
      }
   }

   public long getNextWakeTime() {
      return eng_.getNextWakeTime();
   }

   public boolean abortRequested() {
      if (eng_ != null) {
         return eng_.abortRequested();
      } else {
         return false;
      }
   }

   private boolean isPaused() {
      if (eng_ != null) {
         return eng_.isPaused();
      } else {
         return false;
      }
   }

   boolean saveAs() {
      String prefix;
      String root;
      for (;;) {
         File f = FileDialogs.save(hyperImage_.getWindow(),
                 "Please choose a location for the data set",
                 MMStudioMainFrame.MM_DATA_SET);
         if (f == null) // Canceled.
         {
            return false;
         }
         prefix = f.getName();
         root = new File(f.getParent()).getAbsolutePath();
         if (f.exists()) {
            ReportingUtils.showMessage(prefix
                    + " is write only! Please choose another name.");
         } else {
            break;
         }
      }

      TaggedImageStorageDiskDefault newFileManager = new TaggedImageStorageDiskDefault(root + "/" + prefix, true,
              getSummaryMetadata());

      imageCache_.saveAs(newFileManager);
      MMStudioMainFrame.getInstance().setAcqDirectory(root);
      updateWindow();
      return true;
   }

   final public MMImagePlus createMMImagePlus(AcquisitionVirtualStack virtualStack) {
      MMImagePlus img = new MMImagePlus(imageCache_.getDiskLocation(), virtualStack);
      FileInfo fi = new FileInfo();
      fi.width = virtualStack.getWidth();
      fi.height = virtualStack.getHeight();
      fi.fileName = virtualStack.getDirectory();
      fi.url = null;
      img.setFileInfo(fi);
      return img;
   }

   final public ImagePlus createHyperImage(int channels, int slices,
           int frames, final AcquisitionVirtualStack virtualStack,
           HyperstackControls hc) {
      final ImagePlus hyperImage;
      mmImagePlus_.setNChannelsUnverified(channels);
      mmImagePlus_.setNFramesUnverified(frames);
      mmImagePlus_.setNSlicesUnverified(slices);
      if (channels > 1) {
         hyperImage = new MMCompositeImage(mmImagePlus_, CompositeImage.COMPOSITE);
         hyperImage.setOpenAsHyperStack(true);
      } else {
         hyperImage = mmImagePlus_;
         mmImagePlus_.setOpenAsHyperStack(true);
      }
      return hyperImage;
   }

   private void createWindow(ImagePlus hyperImage, HyperstackControls hc) {      
      final ImageWindow win = new StackWindow(hyperImage) {

         private boolean windowClosingDone_ = false;
         private boolean closed_ = false;

         @Override
         public void windowClosing(WindowEvent e) {
            if (windowClosingDone_) {
               return;
            }

            if (eng_ != null && eng_.isAcquisitionRunning()) {
               if (!abort()) {
                  return;
               }
            }

            if (imageCache_.getDiskLocation() == null && promptToSave_) {
               int result = JOptionPane.showConfirmDialog(this,
                       "This data set has not yet been saved.\n"
                       + "Do you want to save it?",
                       "Micro-Manager",
                       JOptionPane.YES_NO_CANCEL_OPTION);

               if (result == JOptionPane.YES_OPTION) {
                  if (!saveAs()) {
                     return;
                  }
               } else if (result == JOptionPane.CANCEL_OPTION) {
                  return;
               }
            }

            // push current display settings to cache
            if (imageCache_ != null) {
               imageCache_.close();
            }

            if (!closed_) {
               close();
            }

            super.windowClosing(e);
            MMStudioMainFrame.getInstance().removeMMBackgroundListener(this);
            ImagePlus.removeImageListener(hc_);
            windowClosingDone_ = true;
            closed_ = true;
         }

         @Override
         public void windowClosed(WindowEvent E) {
            this.windowClosing(E);
            super.windowClosed(E);
         }

         @Override
         public void windowActivated(WindowEvent e) {
            if (!isClosed()) {
               super.windowActivated(e);
            }
         }
      };


      win.setBackground(MMStudioMainFrame.getInstance().getBackgroundColor());
      MMStudioMainFrame.getInstance().addMMBackgroundListener(win);

      win.add(hc);
      ImagePlus.addImageListener(hc);
      win.pack();
   }

   private ScrollbarWithLabel createPositionScrollbar() {
      final ScrollbarWithLabel pSelector = new ScrollbarWithLabel(null, 1, 1, 1, 2, 'p') {

         @Override
         public void setValue(int v) {
            if (this.getValue() != v) {
               super.setValue(v);
               updatePosition(v);
            }
         }
      };

      // prevents scroll bar from blinking on Windows:
      pSelector.setFocusable(false);
      pSelector.setUnitIncrement(1);
      pSelector.setBlockIncrement(1);
      pSelector.addAdjustmentListener(new AdjustmentListener() {

         @Override
         public void adjustmentValueChanged(AdjustmentEvent e) {
            updatePosition(pSelector.getValue());
            // ReportingUtils.logMessage("" + pSelector.getValue());
         }
      });
      return pSelector;
   }

   public JSONObject getCurrentMetadata() {
      try {
         if (hyperImage_ != null) {
            TaggedImage image = virtualStack_.getTaggedImage(hyperImage_.getCurrentSlice());
            if (image != null) {
               return image.tags;
            } else {
               return null;
            }
         } else {
            return null;
         }
      } catch (NullPointerException ex) {
         return null;
      }
   }

   public int getCurrentPosition() {
      return virtualStack_.getPositionIndex();
   }

   public int getNumSlices() {
      return ((IMMImagePlus) hyperImage_).getNSlicesUnverified();
   }

   public int getNumFrames() {
      return ((IMMImagePlus) hyperImage_).getNFramesUnverified();
   }

   public int getNumPositions() {
      return pSelector_.getMaximum();
   }

   public ImagePlus getImagePlus() {
      return hyperImage_;
   }

   public ImageCache getImageCache() {
      return imageCache_;
   }

   public ImagePlus getImagePlus(int position) {
      ImagePlus iP = new ImagePlus();
      iP.setStack(virtualStack_);
      iP.setDimensions(numComponents_ * getNumChannels(), getNumSlices(), getNumFrames());
      iP.setFileInfo(hyperImage_.getFileInfo());
      return iP;
   }

   public void setComment(String comment) throws MMScriptException {
      try {
         getSummaryMetadata().put("Comment", comment);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   public final JSONObject getSummaryMetadata() {
      return imageCache_.getSummaryMetadata();
   }

   public void close() {
      if (hyperImage_ != null) {
         hyperImage_.close();
      }
   }

   public synchronized boolean windowClosed() {
      ImageWindow win = hyperImage_.getWindow();
      return (win == null || win.isClosed());
   }

   public void showFolder() {
      if (isDiskCached()) {
         try {
            File location = new File(imageCache_.getDiskLocation());
            if (JavaUtils.isWindows()) {
               Runtime.getRuntime().exec("Explorer /n,/select," + location.getAbsolutePath());
            } else if (JavaUtils.isMac()) {
               if (!location.isDirectory()) {
                  location = location.getParentFile();
               }
               Runtime.getRuntime().exec("open " + location.getAbsolutePath());
            }
         } catch (IOException ex) {
            ReportingUtils.logError(ex);
         }
      }
   }

   public void setPlaybackFPS(double fps) {
      if (hyperImage_ != null) {
         try {
            JavaUtils.setRestrictedFieldValue(null, Animator.class,
                    "animationRate", (double) fps);
         } catch (NoSuchFieldException ex) {
            ReportingUtils.showError(ex);
         }
      }
   }

   public void setPlaybackLimits(int firstFrame, int lastFrame) {
      if (hyperImage_ != null) {
         try {
            JavaUtils.setRestrictedFieldValue(null, Animator.class,
                    "firstFrame", firstFrame);
            JavaUtils.setRestrictedFieldValue(null, Animator.class,
                    "lastFrame", lastFrame);
         } catch (NoSuchFieldException ex) {
            ReportingUtils.showError(ex);
         }
      }
   }

   public double getPlaybackFPS() {
      return Animator.getFrameRate();
   }

   public String getSummaryComment() {
      return imageCache_.getComment();
   }

   public void setSummaryComment(String comment) {
      imageCache_.setComment(comment);
   }

   void setImageComment(String comment) {
      imageCache_.setImageComment(comment, getCurrentMetadata());
   }

   String getImageComment() {
      try {
         return imageCache_.getImageComment(getCurrentMetadata());
      } catch (NullPointerException ex) {
         return "";
      }
   }

   public boolean isDiskCached() {
      ImageCache imageCache = imageCache_;
      if (imageCache == null) {
         return false;
      } else {
         return imageCache.getDiskLocation() != null;
      }
   }

   public void show() {
      hyperImage_.show();
      hyperImage_.getWindow().toFront();
   }

   // CHANNEL SECTION ////////////
   public int getNumChannels() {
      return ((IMMImagePlus) hyperImage_).getNChannelsUnverified();
   }

   public int getNumGrayChannels() {
      return getNumChannels();
   }

   public String[] getChannelNames() {
      if (hyperImage_.isComposite()) {
         int nChannels = getNumGrayChannels();
         String[] chanNames = new String[nChannels];
         for (int i = 0; i < nChannels; ++i) {
            try {
               chanNames[i] = imageCache_.getDisplayAndComments().getJSONArray("Channels").getJSONObject(i).getString("Name");
            } catch (Exception ex) {
               ReportingUtils.logError(ex);
            }
         }
         return chanNames;
      } else {
         return null;
      }
   }

   public void setChannelVisibility(int channelIndex, boolean visible) {
      if (!hyperImage_.isComposite()) {
         return;
      }
      CompositeImage ci = (CompositeImage) hyperImage_;
      ci.getActiveChannels()[channelIndex] = visible;
   }

   public int[] getChannelHistogram(int channelIndex) {
      if (hyperImage_ == null || hyperImage_.getProcessor() == null) {
         return null;
      }
      if (hyperImage_.isComposite()
              && ((CompositeImage) hyperImage_).getMode()
              == CompositeImage.COMPOSITE) {
         ImageProcessor ip = ((CompositeImage) hyperImage_).getProcessor(channelIndex + 1);
         if (ip == null) {
            return null;
         }
         return ip.getHistogram();
      } else {
         if (hyperImage_.getChannel() == (channelIndex + 1)) {
            return hyperImage_.getProcessor().getHistogram();
         } else {
            return null;
         }
      }
   }

   private void updateChannelContrast(int channel) {
      if (hyperImage_ == null) {
         return;
      }

      if (hyperImage_.isComposite()) {
         setChannelWithoutMovingSlider(channel);
         CompositeImage ci = (CompositeImage) hyperImage_;
         setChannelDisplayRange(channel, ci);
      } else {
         hyperImage_.setDisplayRange(getChannelMin(channel), getChannelMax(channel));
      }
   }

   private void setChannelDisplayRange(int channel, CompositeImage ci) {
      int min = getChannelMin(channel);
      int max = getChannelMax(channel);
      // ImageJ WORKAROUND
      // The following two lines of code are both necessary for a correct update.
      // Otherwise min and max get inconsistent in ImageJ.
      if (ci.getProcessor(channel+1) != null) {
         ci.getProcessor(channel + 1).setMinAndMax(min, max);
      }
      ci.setDisplayRange(min, max);
   }

   private void updateChannelLUT(int channel) {
      if (hyperImage_ == null) {
         return;
      }
      LUT lut = ImageUtils.makeLUT(getChannelColor(channel), getChannelGamma(channel), 8);
      if (hyperImage_.isComposite()) {
         setChannelWithoutMovingSlider(channel);
         CompositeImage ci = (CompositeImage) hyperImage_;
         ci.setChannelLut(lut);
      } else {
         hyperImage_.getProcessor().setColorModel(lut);
         updateChannelContrast(channel);
      }
   }

   private void setChannelWithoutMovingSlider(int channel) {
      if (hyperImage_ != null) {
         int z = hyperImage_.getSlice();
         int t = hyperImage_.getFrame();

         hyperImage_.updatePosition(channel + 1, z, t);
      }
   }

   public int getChannelMin(int channelIndex) {
      try {
         return getChannelSetting(channelIndex).getInt("Min");
      } catch (Exception ex) {
         return Integer.MAX_VALUE;
      }
   }

   public int getChannelMax(int channelIndex) {
      try {
         return getChannelSetting(channelIndex).getInt("Max");
      } catch (Exception ex) {
         return Integer.MIN_VALUE;
      }
   }

   public double getChannelGamma(int channelIndex) {
      try {
         return getChannelSetting(channelIndex).getDouble("Gamma");
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return 1.0;
      }
   }

   public Color getChannelColor(int channelIndex) {
      try {
         return new Color(getChannelSetting(channelIndex).getInt("Color"));
      } catch (Exception ex) {
         return Color.WHITE;
      }
   }

   public void setChannelColor(int channel, int rgb) throws MMScriptException {
      JSONObject chan = getChannelSetting(channel);
      try {
         chan.put("Color", rgb);
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
      }
      updateChannelLUT(channel);
      String[] chNames = getChannelNames();
      if (chNames != null && chNames.length > channel)
         MMStudioMainFrame.getInstance().saveChannelColor(getChannelNames()[channel], rgb);
   }

   public void setChannelGamma(int channel, double gamma) {
      JSONObject chan = getChannelSetting(channel);
      try {
         chan.put("Gamma", gamma);
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
      updateChannelLUT(channel);
   }

   public void setChannelDisplayRange(int channel, int min, int max) {
      JSONObject chan = getChannelSetting(channel);
      try {
         chan.put("Min", min);
         chan.put("Max", max);
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }

      updateChannelContrast(channel);
   }

   private JSONObject getChannelSetting(int channel) {
      try {
         JSONArray array = imageCache_.getDisplayAndComments().getJSONArray("Channels");
         if (array != null) {
            return array.getJSONObject(channel);
         } else {
            return null;
         }
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return null;
      }
   }
}
