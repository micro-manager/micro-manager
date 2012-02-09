package org.micromanager.acquisition;

import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.micromanager.api.ImageCacheListener;
import ij.ImageStack;
import ij.process.LUT;
import java.awt.event.AdjustmentEvent;
import ij.CompositeImage;
import ij.ImageListener;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageWindow;
import ij.gui.ScrollbarWithLabel;
import ij.gui.StackWindow;
import ij.io.FileInfo;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Point;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.File;
import java.io.IOException;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.api.AcquisitionEngine;
import org.micromanager.api.ImageCache;
import org.micromanager.utils.AcqOrderMode;
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
   final static String[] rgbNames = {"Red", "Blue", "Green"};
   final ImageCache imageCache_;
   final Preferences prefs_ = Preferences.userNodeForPackage(this.getClass());

   private static final String SIMPLE_WIN_X = "simple_x";
   private static final String SIMPLE_WIN_Y = "simple_y";
   
   private AcquisitionEngine eng_;
   private boolean finished_ = false;
   private boolean promptToSave_ = true;
   private String name_;
   private long lastDisplayTime_;
   private int lastFrameShown_ = 0;
   private int lastSliceShown_ = 0;
   private int lastPositionShown_ = 0;
   private boolean updating_ = false;
   private int[] channelInitiated_;
   private int preferredSlice_ = -1;
   private int preferredPosition_ = -1;
   private int preferredChannel_ = -1;
   private Timer preferredPositionTimer_;

   private int numComponents_;
   private ImagePlus hyperImage_;
   private ScrollbarWithLabel pSelector_;
   private ScrollbarWithLabel tSelector_;
   private ScrollbarWithLabel zSelector_;
   private ScrollbarWithLabel cSelector_;   
   private DisplayControls controls_;   
   public AcquisitionVirtualStack virtualStack_;
   private final Preferences displayPrefs_;
   private boolean simple_ = false;
   private MetadataPanel mdPanel_;
   private boolean newDisplay_ = true; //used for autostretching on window opening
   
   private double framesPerSec_ = 7;
   private int firstFrame_;
   private int lastFrame_;
   private Timer zAnimationTimer_;
   private Timer tAnimationTimer_;
   private Component zIcon_, pIcon_, tIcon_, cIcon_;





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
      
      public void drawWithoutUpdate();
   }

   public class MMCompositeImage extends CompositeImage implements IMMImagePlus {
      public VirtualAcquisitionDisplay display_;
      private boolean updatingImage_, settingMode_, settingLut_;
      
      MMCompositeImage(ImagePlus imgp, int type, VirtualAcquisitionDisplay disp) {
         super(imgp, type);
         display_ = disp;
      }
      
      @Override
      public String getTitle() {
         return name_;
      }
      
      private void superReset() {
         super.reset();
      }
      
      @Override
      public void reset() {
         if (SwingUtilities.isEventDispatchThread())
            super.reset();
         else
            SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               superReset();
            } });         
      }
      
      
      /*
       * ImageJ workaround: the following two functions set the currentChannel field to -1, which can lead to a null 
       * pointer exception if the function is called while CompositeImage.updateImage is also running on a different 
       * Thread.  So we make sure they are all on the EDT so this never happens
       */
      @Override
      public synchronized void setMode(final int mode) {
         Runnable runnable = new Runnable() {
            @Override
            public void run() {
               superSetMode(mode);             
               }};
         invokeLaterIfNotEDT(runnable);
      }
      
      private void superSetMode(int mode) {
         super.setMode(mode);
      }
      
      @Override
      public synchronized void setChannelLut(final LUT lut) {
         Runnable runnable = new Runnable() {
            @Override
            public void run() {
               superSetLut(lut);
            }};
         invokeLaterIfNotEDT(runnable);
      }
      
      private void superSetLut(LUT lut) {
         super.setChannelLut(lut);
      }
      
      @Override
      public synchronized void updateImage() {
         Runnable runnable = new Runnable() {
            @Override
            public void run() {
               superUpdateImage();
            }};

         invokeLaterIfNotEDT(runnable);
      }
      
      private void superUpdateImage() {
         super.updateImage();
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
      
      private void superDraw() {
         super.draw();
      }
      
      @Override
      public void draw() {
         Runnable runnable = new Runnable() {
            public void run() {
               imageChangedUpdate();
               superDraw();
            } };
         invokeLaterIfNotEDT(runnable);
      }
      
      @Override
      public void drawWithoutUpdate() {
        super.getWindow().getCanvas().setImageUpdated();
        super.draw();
      }
   }

   public class MMImagePlus extends ImagePlus implements IMMImagePlus {
      public VirtualAcquisitionDisplay display_;
      
      MMImagePlus(String title, ImageStack stack, VirtualAcquisitionDisplay disp) {
         super(title, stack);
         display_ = disp;
      }
      
      @Override
      public String getTitle() {
         return name_;
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

      private void superDraw() {
         super.draw();
      }

      @Override
      public void draw() {
         if (!SwingUtilities.isEventDispatchThread()) {
            Runnable onEDT = new Runnable() {

               public void run() {
                  imageChangedUpdate();
                  superDraw();
               }
            };
            SwingUtilities.invokeLater(onEDT);
         } else {
            imageChangedUpdate();
            super.draw();
         }
      }

      @Override
      public void drawWithoutUpdate() {
         //ImageJ requires this
         super.getWindow().getCanvas().setImageUpdated();
         super.draw();
      }
   }

   public VirtualAcquisitionDisplay(ImageCache imageCache, AcquisitionEngine eng) {
      this(imageCache, eng, WindowManager.getUniqueName("Untitled"));
   }

   public VirtualAcquisitionDisplay(ImageCache imageCache, AcquisitionEngine eng, String name) {
      name_ = name;
      imageCache_ = imageCache;
      eng_ = eng;
      pSelector_ = createPositionScrollbar();
      displayPrefs_ = Preferences.userNodeForPackage(this.getClass());
      imageCache_.setDisplay(this);
   }

   //used for snap and live
   public VirtualAcquisitionDisplay(ImageCache imageCache, String name) throws MMScriptException {
      simple_ = true;
      imageCache_ = imageCache;
      displayPrefs_ = Preferences.userNodeForPackage(this.getClass());
      name_ = name;
      imageCache_.setDisplay(this);
   }
   
   private void invokeAndWaitIfNotEDT(Runnable runnable) {
       if (SwingUtilities.isEventDispatchThread())
         runnable.run();
      else
         try {
            SwingUtilities.invokeAndWait(runnable);
        } catch (InterruptedException ex) {
            ReportingUtils.logError(ex);
        } catch (InvocationTargetException ex) {
            System.out.println(ex.getCause());
        }
   }
   
   private void invokeLaterIfNotEDT(Runnable runnable){
      if (SwingUtilities.isEventDispatchThread())
         runnable.run();
      else
         SwingUtilities.invokeLater(runnable);
   }

   private void startup(JSONObject firstImageMetadata) {
      mdPanel_ = MMStudioMainFrame.getInstance().getMetadataPanel();
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
         if (firstImageMetadata != null) {
            width = MDUtils.getWidth(firstImageMetadata);
            height = MDUtils.getHeight(firstImageMetadata);
         } else {
            width = MDUtils.getWidth(summaryMetadata);
            height = MDUtils.getHeight(summaryMetadata);
         }
         numSlices = Math.max(summaryMetadata.getInt("Slices"), 1);
         numFrames = Math.max(summaryMetadata.getInt("Frames"), 1);
         int imageChannelIndex;
         try {
            imageChannelIndex = MDUtils.getChannelIndex(firstImageMetadata);
         } catch (Exception e) {
            imageChannelIndex = -1;
         }
         numChannels = Math.max(1 + imageChannelIndex,
                 Math.max(summaryMetadata.getInt("Channels"), 1));
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
         if (firstImageMetadata != null) {
            type = MDUtils.getSingleChannelType(firstImageMetadata);
         } else {
            type = MDUtils.getSingleChannelType(summaryMetadata);
         }
      } catch (Exception ex) {
         ReportingUtils.showError(ex, "Unable to determine acquisition type.");
      }
      virtualStack_ = new AcquisitionVirtualStack(width, height, type, null,
              imageCache_, numGrayChannels * numSlices * numFrames, this);
      if (summaryMetadata.has("PositionIndex")) {
         try {
            virtualStack_.setPositionIndex(MDUtils.getPositionIndex(summaryMetadata));
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
         }
      }
      if (simple_) {
         controls_ = new SimpleWindowControls(this);
      } else {
         controls_ = new HyperstackControls(this);
      }
      hyperImage_ = createHyperImage(createMMImagePlus(virtualStack_), 
              numGrayChannels, numSlices, numFrames, virtualStack_, controls_);
  
      applyPixelSizeCalibration(hyperImage_);
      
      mdPanel_.setup(null);
      createWindow();
      //Make sure contrast panel sets up correctly here
      windowToFront();
      setupMetadataPanel();
      
      
      cSelector_ = getSelector("c");
      if (!simple_) {
         tSelector_ = getSelector("t");
         zSelector_ = getSelector("z");
         if (zSelector_ != null) 
            zSelector_.addAdjustmentListener(new AdjustmentListener() {
               public void adjustmentValueChanged(AdjustmentEvent e) {
                  preferredSlice_ = zSelector_.getValue();
               }});
         if (cSelector_ != null)
            cSelector_.addAdjustmentListener(new AdjustmentListener() {
               public void adjustmentValueChanged(AdjustmentEvent e) {
                  preferredChannel_ = cSelector_.getValue();
               }});
              
         if (imageCache_.lastAcquiredFrame() > 1) {
            setNumFrames(1 + imageCache_.lastAcquiredFrame());
         } else {
            setNumFrames(1);
         }
         configureAnimationControls();
         //cant use these function because of an imageJ bug
//         setNumSlices(numSlices);
         setNumPositions(numPositions);
      }
      
      updateAndDraw();
      updateWindowTitleAndStatus();
   }
   
   private void animateSlices(boolean animate) {
      if (!animate) {
         zAnimationTimer_.stop();
         refreshAnimationIcons();
         return;
      }
      if (tAnimationTimer_ != null)
         animateFrames(false);
      if (zAnimationTimer_ == null)
         zAnimationTimer_ = new Timer((int) (1000.0 / framesPerSec_), new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            int slice = hyperImage_.getSlice();
            if (slice >= zSelector_.getMaximum()-1) 
               hyperImage_.setPosition(hyperImage_.getChannel(), 1, hyperImage_.getFrame());          
            else 
               hyperImage_.setPosition(hyperImage_.getChannel(), slice+1, hyperImage_.getFrame());          
         }});
      zAnimationTimer_.setDelay((int) (1000.0 / framesPerSec_));
      zAnimationTimer_.start();
      refreshAnimationIcons();
   }
   
   private void animateFrames(boolean animate) {
      if (!animate) {
         tAnimationTimer_.stop();
         refreshAnimationIcons();
         return;
      }
      if (zAnimationTimer_ != null)
         animateSlices(false);
      if (tAnimationTimer_ == null)
         tAnimationTimer_ = new Timer((int) (1000.0 / framesPerSec_), new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            int frame = hyperImage_.getFrame();
            if (frame >= lastFrame_) 
               hyperImage_.setPosition(hyperImage_.getChannel(), hyperImage_.getSlice(), firstFrame_);          
            else 
               hyperImage_.setPosition(hyperImage_.getChannel(), hyperImage_.getSlice(), frame + 1);              
         }});
      tAnimationTimer_.setDelay((int) (1000.0 / framesPerSec_));
      tAnimationTimer_.start();
      refreshAnimationIcons();
   }
   
   private void refreshAnimationIcons() {
      if (zIcon_ != null)
         zIcon_.repaint();
      if (tIcon_ != null)
         tIcon_.repaint();
   }
   
   private void configureAnimationControls() {
      firstFrame_ = 1;
      lastFrame_ = tSelector_!= null ? tSelector_.getMaximum()-1 : 1;
      if (zIcon_ != null) {
         zIcon_.addMouseListener(new MouseListener() {
            public void mousePressed(MouseEvent e) {              
               animateSlices(zAnimationTimer_ == null || !zAnimationTimer_.isRunning());
            }     
            public void mouseClicked(MouseEvent e) {}   
            public void mouseReleased(MouseEvent e) {}   
            public void mouseEntered(MouseEvent e) {}      
            public void mouseExited(MouseEvent e) {}       
         });
      }
      if (tIcon_ != null) {
         tIcon_.addMouseListener(new MouseListener() {
            public void mousePressed(MouseEvent e) {              
               animateFrames(tAnimationTimer_ == null || !tAnimationTimer_.isRunning());
            }     
            public void mouseClicked(MouseEvent e) {}   
            public void mouseReleased(MouseEvent e) {}   
            public void mouseEntered(MouseEvent e) {}      
            public void mouseExited(MouseEvent e) {}       
         });
      }
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
      try {
         int frame = MDUtils.getFrameIndex(taggedImage.tags);

      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
      }

      if (eng_ != null)
         updateDisplay(taggedImage, false);
   }

   /*
    * Method required by ImageCacheListener
    */
   @Override
   public void imagingFinished(String path) {
      updateDisplay(null, true);
      updateAndDraw();
      updateWindowTitleAndStatus();
   }

   private void updateDisplay(TaggedImage taggedImage, boolean finalUpdate) {
      try {
         long t = System.currentTimeMillis();
         JSONObject tags;
         if (taggedImage != null) 
            tags = taggedImage.tags;      
         else 
            tags = imageCache_.getLastImageTags();
         if (tags == null)
            return;
         int frame = MDUtils.getFrameIndex(tags);
         int ch = MDUtils.getChannelIndex(tags);
         int slice = MDUtils.getSliceIndex(tags);
         int position = MDUtils.getPositionIndex(tags);
     
         if (finalUpdate || frame == 0 || (Math.abs(t - lastDisplayTime_) > 30) ||
                 (ch == getNumChannels()-1 && lastFrameShown_==frame 
                 && lastSliceShown_==slice && lastPositionShown_==position  )  ) {              
         showImage(tags, true);
         lastFrameShown_ = frame;
         lastSliceShown_ = slice;
         lastPositionShown_ = position;
         lastDisplayTime_ = t;
         }
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
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
         
         JSONArray chColors = MDUtils.getJSONArrayMember(summaryMetadata, "ChColors");
         JSONArray chNames = MDUtils.getJSONArrayMember(summaryMetadata, "ChNames");
         JSONArray chMaxes = MDUtils.getJSONArrayMember(summaryMetadata, "ChContrastMax");
         JSONArray chMins = MDUtils.getJSONArrayMember(summaryMetadata, "ChContrastMin");
       
         int numComponents = MDUtils.getNumberOfComponents(summaryMetadata);    
       
         JSONArray channels = new JSONArray();
         if (numComponents > 1) //RGB
         {
            int rgbChannelBitDepth = summaryMetadata.getString("PixelType").endsWith("32") ? 8 : 16;
            for (int k = 0; k < 3; k++) {
               JSONObject channelObject = new JSONObject();
               channelObject.put("Color", rgb[k].getRGB());
               channelObject.put("Name", rgbNames[k]);
               channelObject.put("Gamma", 1.0);
               channelObject.put("Min", 0);
               channelObject.put("Max", Math.pow(2, rgbChannelBitDepth)-1);
               channels.put(channelObject);
            }
         } else {
            for (int k = 0; k < chNames.length(); ++k) {
               String name = (String) chNames.get(k);
               int color = chColors.getInt(k);
               int min = chMins.getInt(k);
               int max = chMaxes.getInt(k);
               JSONObject channelObject = new JSONObject();
               channelObject.put("Color", color);
               channelObject.put("Name", name);
               channelObject.put("Gamma", 1.0);
               channelObject.put("Min", min);
               channelObject.put("Max", max);
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
         ReportingUtils.showError("Error creating display settigns from summary metadata");
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
      if (simple_)
         return;
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
      if (simple_)
         return;
      if (tSelector_ != null) {
         //ImageWindow win = hyperImage_.getWindow();
         ((IMMImagePlus) hyperImage_).setNFramesUnverified(n);
         tSelector_.setMaximum(n + 1);
         // JavaUtils.setRestrictedFieldValue(win, StackWindow.class, "nFrames", n);
      }
   }

   private void setNumSlices(int n) {
      if (simple_)
         return;
      if (zSelector_ != null) {
         ((IMMImagePlus) hyperImage_).setNSlicesUnverified(n);
         zSelector_.setMaximum(n + 1);
      }
   }

   private void setNumChannels(int n) {
      if (cSelector_ != null) {
         ((IMMImagePlus) hyperImage_).setNChannelsUnverified(n);
         cSelector_.setMaximum(n);
      }
   }

   public ImagePlus getHyperImage() {
      return hyperImage_;
   }

   public int getStackSize() {
      if (hyperImage_ == null )
         return -1;
      int s = hyperImage_.getNSlices();
      int c = hyperImage_.getNChannels();
      int f = hyperImage_.getNFrames();
      if ( (s > 1 && c > 1) || (c > 1 && f > 1) || (f > 1 && s > 1) )
         return s * c * f;
      return Math.max(Math.max(s, c), f);
   }
   
   private void imageChangedWindowUpdate() {
      if (hyperImage_ != null && hyperImage_.isVisible()) {
         TaggedImage ti = virtualStack_.getTaggedImage(hyperImage_.getCurrentSlice());
         if (ti != null)
            controls_.newImageUpdate(ti.tags);
      }
   }
   
   public void updateAndDraw() {
      if (!updating_) {
         updating_ = true;
         setupMetadataPanel();
         if (hyperImage_ != null && hyperImage_.isVisible()) {            
            hyperImage_.updateAndDraw();
            imageChangedWindowUpdate();
         }
         updating_ = false;
      }
   }

   public void updateWindowTitleAndStatus() {
      if(simple_) {
         if (hyperImage_ != null && hyperImage_.getWindow() != null)
            hyperImage_.getWindow().setTitle(name_);
         return;
      }
      if (controls_ == null) {
         return;
      }
     
      String status = "";
      final AcquisitionEngine eng = eng_;

      if (eng != null) {
         if (acquisitionIsRunning()) {
            if (!abortRequested()) {
               controls_.acquiringImagesUpdate(true);
               if (isPaused()) {
                  status = "paused";
               } else {
                  status = "running";
               }
            } else {
               controls_.acquiringImagesUpdate(false);
               status = "interrupted";
            }
         } else {
            controls_.acquiringImagesUpdate(false);
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
         controls_.acquiringImagesUpdate(false);
      }
      if (isDiskCached()) {
         status += "on disk";
      } else {
         status += "not yet saved";
      }

      controls_.imagesOnDiskUpdate(imageCache_.getDiskLocation() != null);
      String path = isDiskCached()
              ? new File(imageCache_.getDiskLocation()).getName() : name_;
      
      if (hyperImage_.isVisible()) 
            hyperImage_.getWindow().setTitle(path + " (" + status + ")");
     
   }

   private void windowToFront() {
       if (hyperImage_ == null || hyperImage_.getWindow() == null)
         return;
      hyperImage_.getWindow().toFront();
   }
   
   private void setupMetadataPanel() {
       if (hyperImage_ == null || hyperImage_.getWindow() == null)
         return;
      //call this explicitly because it isn't fired immediately
      mdPanel_.setup(hyperImage_.getWindow());
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
   public void showImage(TaggedImage taggedImg, boolean waitForDisplay) throws InterruptedException, InvocationTargetException {
      showImage(taggedImg.tags, waitForDisplay);
   }
   
   public void showImage(JSONObject tags, boolean waitForDisplay) throws InterruptedException, InvocationTargetException {
      updateWindowTitleAndStatus();
      
      if (tags == null) 
         return;

      if (hyperImage_ == null)
         startup(tags);
      
      int channel = 0, frame = 0, slice = 0, position = 0, superChannel = 0;
      try {
         frame = MDUtils.getFrameIndex(tags);
         slice = MDUtils.getSliceIndex(tags);
         channel = MDUtils.getChannelIndex(tags);
         position = MDUtils.getPositionIndex(tags);
         superChannel = this.rgbToGrayChannel(MDUtils.getChannelIndex(tags));
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   
      //make sure pixels get properly set
      if (hyperImage_ != null && frame == 0) {
         IMMImagePlus img = (IMMImagePlus) hyperImage_;
         if (img.getNChannelsUnverified() == 1) {
            if (img.getNSlicesUnverified() == 1) {
               hyperImage_.getProcessor().setPixels(virtualStack_.getPixels(1));
            }
         } else if (hyperImage_ instanceof MMCompositeImage) {
            //reset rebuilds each of the channel ImageProcessors with the correct pixels
            //from AcquisitionVirtualStack
            MMCompositeImage ci = ((MMCompositeImage) hyperImage_);
            ci.reset();
            //This line is neccessary for image processor to have correct pixels in grayscale mode
            ci.getProcessor().setPixels(virtualStack_.getPixels(ci.getCurrentSlice()));
         }
      } else 
         if (hyperImage_ instanceof MMCompositeImage ) {
         MMCompositeImage ci = ((MMCompositeImage) hyperImage_);
         ci.reset();
      }


      if (frame + 1 > lastFrame_)
         lastFrame_ = frame + 1;
      
    
      if (cSelector_ != null) {
         if (cSelector_.getMaximum() <= (1 + superChannel)) {
            this.setNumChannels(1 + superChannel);
            ((CompositeImage) hyperImage_).reset();
            //JavaUtils.invokeRestrictedMethod(hyperImage_, CompositeImage.class,
            //       "setupLuts", 1 + superChannel, Integer.TYPE);
         }
      }
      

      initializeContrast(channel);
      

      
      if (!simple_) {
         if (tSelector_ != null) {
            if (tSelector_.getMaximum() <= (1 + frame)) {
               this.setNumFrames(1 + frame);
            }
         }
         if (position + 1 >= getNumPositions()) {
            setNumPositions(position + 1);
         }
         setPosition(position);
         hyperImage_.setPosition(1 + superChannel, 1 + slice, 1 + frame);
      }
      
      Runnable updateAndDraw = new Runnable() {
         public void run() {
            updateAndDraw();
         }  };

      if (!SwingUtilities.isEventDispatchThread()) {
         if (waitForDisplay) {
            SwingUtilities.invokeAndWait(updateAndDraw);
         } else {
            SwingUtilities.invokeLater(updateAndDraw);
         }
      } else {
         updateAndDraw();
      }

      setPreferredScrollbarPositions();        
   }
   
   /*
    * Live/snap should load window contrast settings
    * MDA should autoscale on frist image
    * Opening dataset should load from disoplay and comments
    */
   private void initializeContrast(final int channel) {
      Runnable autoscaleOrLoadContrast = new Runnable() {
         public void run() {
            if (!newDisplay_) 
               return;          
            if (simple_) {
               if (hyperImage_ instanceof MMCompositeImage
                       && ((MMCompositeImage) hyperImage_).getNChannelsUnverified() - 1 != channel) {
                  return;
               }
               mdPanel_.loadSimpleWinContrastWithoutDraw(imageCache_, hyperImage_);
            } else {
               if (eng_ != null) {
                  if (hyperImage_ instanceof MMCompositeImage
                          && ((MMCompositeImage) hyperImage_).getNChannelsUnverified() - 1 != channel) {
                     return;
                  }
                  mdPanel_.autoscaleWithoutDraw(imageCache_, hyperImage_);
               } else if (!simple_) { //Called when display created by pressing acquire button
                  if (hyperImage_ instanceof MMCompositeImage) {
                     if (((MMCompositeImage) hyperImage_).getNChannelsUnverified() - 1 != channel) {
                        return;
                     }
                     mdPanel_.autoscaleWithoutDraw(imageCache_, hyperImage_);
                  } else {
                     mdPanel_.autoscaleWithoutDraw(imageCache_, hyperImage_);
                  }
               }
//               else do nothing because contrast automatically loaded from cache

            }
            newDisplay_ = false;
         }
      };
      if (!SwingUtilities.isEventDispatchThread()) {
         SwingUtilities.invokeLater(autoscaleOrLoadContrast);
      } else {
         autoscaleOrLoadContrast.run();
      }
   }
   
   private void setPreferredScrollbarPositions() {
      if (this.acquisitionIsRunning() ) {
         long nextImageTime = eng_.getNextWakeTime();
         if(System.nanoTime()/1000000 - nextImageTime < -1000 ){ //1 sec or more until next start
            if (preferredPositionTimer_ == null)
               preferredPositionTimer_ = new Timer(1000, new ActionListener() {
                  @Override
                  public void actionPerformed(ActionEvent e) {
                     IMMImagePlus ip = ((IMMImagePlus) hyperImage_ );
                     int c = ip.getNChannelsUnverified(), s = ip.getNSlicesUnverified(), f = ip.getNFramesUnverified();
                     hyperImage_.setPosition(preferredChannel_ == -1 ? c : preferredChannel_,
                             preferredSlice_ == -1 ? s : preferredSlice_, f);
                     if (pSelector_ != null && preferredPosition_ > -1) 
                        if (eng_.getAcqOrderMode() != AcqOrderMode.POS_TIME_CHANNEL_SLICE
                                && eng_.getAcqOrderMode() != AcqOrderMode.POS_TIME_SLICE_CHANNEL) 
                           setPosition(preferredPosition_);
                     preferredPositionTimer_.stop();;
                  }});
            if (preferredPositionTimer_.isRunning())
               return;
            preferredPositionTimer_.start();
         }
      }
   }

   private void updatePosition(int p) {
      if (simple_)
         return;
      virtualStack_.setPositionIndex(p);
      if (!hyperImage_.isComposite()) {
         Object pixels = virtualStack_.getPixels(hyperImage_.getCurrentSlice());
         hyperImage_.getProcessor().setPixels(pixels);
      }
      updateAndDraw();
   }

   public void setPosition(int p) {
      if (simple_)
         return;
      pSelector_.setValue(p);
   }

   public void setSliceIndex(int i) {
     if (simple_)
         return;
      final int f = hyperImage_.getFrame();
      final int c = hyperImage_.getChannel();
      hyperImage_.setPosition(c, i+1, f);
   }

   public int getSliceIndex() {
      return hyperImage_.getSlice() - 1;
   }

   boolean pause() {
      if (eng_ != null) {
         if (eng_.isPaused()) {
            eng_.setPause(false);
         } else {
            eng_.setPause(true);
         }
         updateWindowTitleAndStatus();
         return (eng_.isPaused());
      }
      return false;
   }

   boolean abort() {
      if (eng_ != null) {
         if (eng_.abortRequest()) {
            updateWindowTitleAndStatus();
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
      updateWindowTitleAndStatus();
      return true;
   }

   final public MMImagePlus createMMImagePlus(AcquisitionVirtualStack virtualStack) {
      MMImagePlus img = new MMImagePlus(imageCache_.getDiskLocation(), virtualStack,this);
      FileInfo fi = new FileInfo();
      fi.width = virtualStack.getWidth();
      fi.height = virtualStack.getHeight();
      fi.fileName = virtualStack.getDirectory();
      fi.url = null;
      img.setFileInfo(fi);
      return img;
   }

   final public ImagePlus createHyperImage(MMImagePlus mmIP, int channels, int slices,
           int frames, final AcquisitionVirtualStack virtualStack,
           DisplayControls hc) {
      final ImagePlus hyperImage;
      mmIP.setNChannelsUnverified(channels);
      mmIP.setNFramesUnverified(frames);
      mmIP.setNSlicesUnverified(slices);
      if (channels > 1) {
         hyperImage = new MMCompositeImage(mmIP, CompositeImage.COMPOSITE,this);
         hyperImage.setOpenAsHyperStack(true);
      } else {
         hyperImage = mmIP;
         mmIP.setOpenAsHyperStack(true);
      }
      return hyperImage;
   }
   
   public void liveModeEnabled(boolean enabled) {
      if (simple_) {
         controls_.acquiringImagesUpdate(enabled);

      }
   }

   private void createWindow() {  

      DisplayWindow win = new DisplayWindow(hyperImage_);

      win.setBackground(MMStudioMainFrame.getInstance().getBackgroundColor());
      MMStudioMainFrame.getInstance().addMMBackgroundListener(win);

      win.add(controls_);
      win.pack();
      
      if (simple_ )
         win.setLocation(prefs_.getInt(SIMPLE_WIN_X, 0),prefs_.getInt(SIMPLE_WIN_Y, 0));
   }

    
   private ScrollbarWithLabel getSelector(String label) {
      // label should be "t", "z", or "c"
      ScrollbarWithLabel selector = null;
      ImageWindow win = hyperImage_.getWindow();
      int slices = ((IMMImagePlus) hyperImage_).getNSlicesUnverified();
      int frames = ((IMMImagePlus) hyperImage_).getNFramesUnverified();
      int channels = ((IMMImagePlus) hyperImage_).getNChannelsUnverified();
      if (win instanceof StackWindow) {
         try {
            //ImageJ bug workaround
            if (frames > 1 && slices == 1 && channels == 1 && label.equals("t"))
               selector = (ScrollbarWithLabel) JavaUtils.getRestrictedFieldValue((StackWindow) win, StackWindow.class, "zSelector");
            else
               selector = (ScrollbarWithLabel) JavaUtils.getRestrictedFieldValue((StackWindow) win, StackWindow.class, label + "Selector");
         } catch (NoSuchFieldException ex) {
            selector = null;
            ReportingUtils.logError(ex);
         }
      }
      //replace default icon with custom one
      if (selector != null) {
         try {
            Component icon = (Component) JavaUtils.getRestrictedFieldValue(
                    selector, ScrollbarWithLabel.class, "icon");
            selector.remove(icon);
         } catch (NoSuchFieldException ex) {
            ReportingUtils.logError(ex);
         }
         ScrollbarIcon newIcon = new ScrollbarIcon(label.charAt(0), this);
         if (label.equals("z")) {
            zIcon_ = newIcon;
         } else if (label.equals("t")) {
            tIcon_ = newIcon;
         } else if (label.equals("c")) {
            cIcon_ = newIcon;
         }

         selector.add(newIcon, BorderLayout.WEST);
         selector.invalidate();
         selector.validate();
      }
      return selector;
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
            preferredPosition_ = pSelector_.getValue();

         }
      });
      
      if (pSelector != null) {
      try {
         Component icon = (Component) JavaUtils.getRestrictedFieldValue(
                    pSelector, ScrollbarWithLabel.class, "icon");
         pSelector.remove(icon);
      } catch (NoSuchFieldException ex) {
         ReportingUtils.logError(ex);
      }
      
      pIcon_ = new ScrollbarIcon('p',this);
      pSelector.add(pIcon_,BorderLayout.WEST);
      pSelector.invalidate();
      pSelector.validate();
      }
      
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
      if (simple_)
         return 1;
      return ((IMMImagePlus) hyperImage_).getNSlicesUnverified();
   }

   public int getNumFrames() {
      if (simple_)
         return 1;
      return ((IMMImagePlus) hyperImage_).getNFramesUnverified();
   }

   public int getNumPositions() {
      if (simple_)
         return 1;
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
         //call this so when one window closes and is replaced by another
         //the md panel gets rid of the first before doing stuff with
         //the second 
         if (WindowManager.getCurrentImage() == hyperImage_)
            mdPanel_.setup(null);
         hyperImage_.getWindow().windowClosing(null);
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
            framesPerSec_ = fps;
      if (zAnimationTimer_ != null && zAnimationTimer_.isRunning()) {
         animateSlices(false);
         animateSlices(true);
      } else if (tAnimationTimer_ != null && tAnimationTimer_.isRunning()) {
         animateFrames(false);
         animateFrames(true);
      }
   }

   public void setPlaybackLimits(int firstFrame, int lastFrame) {
      firstFrame_ = firstFrame;
      lastFrame_ = lastFrame;
   }

   public double getPlaybackFPS() {
      return framesPerSec_;
   }
   
   public boolean isZAnimated() {
      if (zAnimationTimer_ != null && zAnimationTimer_.isRunning()) 
         return true;
      return false;
   }

   public boolean isTAnimated() {
      if (tAnimationTimer_ != null && tAnimationTimer_.isRunning()) 
         return true;
      return false;
   }

   public boolean isAnimated() {
      return isTAnimated() || isZAnimated();
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
      if (hyperImage_ == null) {
        startup(null);
      }
      hyperImage_.show();
      hyperImage_.getWindow().toFront();
   }

   public int getNumChannels() {
      return ((IMMImagePlus) hyperImage_).getNChannelsUnverified();
   }

   public int getNumGrayChannels() {
      return getNumChannels();
   }
   
   public void setWindowTitle(String name) {
      name_ = name;
      updateWindowTitleAndStatus();
   }
   
   public boolean isSimpleDisplay() {
      return simple_;
   }
   
   public void displayStatusLine(String status) {
      controls_.setStatusLabel(status);
   }
   
   public void setChannelContrast(int channelIndex, int min, int max, double gamma) {
      mdPanel_.setChannelContrast(channelIndex, min, max, gamma);
   }
   
   private void imageChangedUpdate() {
      mdPanel_.imageChangedUpdate(hyperImage_,imageCache_);
      imageChangedWindowUpdate(); //used to update status line
   }
   
   public void refreshContrastPanel() {
      mdPanel_.refresh();
   }
   
   public void drawWithoutUpdate() {
      if (hyperImage_ != null) {
         ((IMMImagePlus) hyperImage_).drawWithoutUpdate();
      }
   }
   
   public class DisplayWindow extends StackWindow {
         private boolean windowClosingDone_ = false;
         private boolean closed_ = false;
         
         public DisplayWindow(ImagePlus ip) {
            super(ip);
         }

         @Override
         public boolean close() {
            windowClosing(null);
            return closed_;
         }

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

            //for some reason window focus listener doesn't always fire, so call
            //explicitly here
            mdPanel_.setup(null);

            
            if (simple_ && hyperImage_ != null && hyperImage_.getWindow() != null && hyperImage_.getWindow().getLocation() != null) {
               Point loc = hyperImage_.getWindow().getLocation();
               prefs_.putInt(SIMPLE_WIN_X, loc.x);
               prefs_.putInt(SIMPLE_WIN_Y, loc.y);
               mdPanel_.saveContrastSettings(imageCache_);
            }
            
            if (imageCache_ != null) {
               imageCache_.close();
            }

            if (!closed_) {
               try {
                  super.close();
               } catch (NullPointerException ex) {
                  ReportingUtils.logError("Null pointer error in ImageJ code while closing window");
               }
            }
            
            
            super.windowClosing(e);
            MMStudioMainFrame.getInstance().removeMMBackgroundListener(this);
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
         
         @Override
         public void setAnimate(boolean b) {
            if ( ((IMMImagePlus) hyperImage_).getNFramesUnverified() > 1 )
               animateFrames(b);
            else
               animateSlices(b);
         }
         
         @Override
         public boolean getAnimate() {
            return isAnimated();
         }
      };   

}
