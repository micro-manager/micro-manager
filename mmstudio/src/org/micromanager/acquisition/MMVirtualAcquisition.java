package org.micromanager.acquisition;

import ij.process.LUT;
import java.awt.event.AdjustmentEvent;
import org.micromanager.api.AcquisitionInterface;
import ij.CompositeImage;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.gui.ScrollbarWithLabel;
import ij.gui.StackWindow;
import ij.plugin.Animator;
import ij.process.ImageStatistics;
import java.awt.Color;
import java.awt.event.AdjustmentListener;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.api.AcquisitionEngine;
import org.micromanager.metadata.AcquisitionData;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class MMVirtualAcquisition implements AcquisitionInterface {
   private String dir_;
   private String name_;
   MMImageCache imageCache_;
   private int numChannels_;
   private int depth_;
   private int numFrames_;
   private int height_;
   private int numSlices_;
   private int width_;
   private int numComponents_ = 1;
   private boolean initialized_;
   private ImagePlus hyperImage_;
   private Map<String,String>[] displaySettings_;
   private ArrayList<AcquisitionVirtualStack> virtualStacks_;
   private String pixelType_;
   private Map<String,String> summaryMetadata_ = null;
   private boolean newData_;
   private Map<String,String> systemMetadata_ = null;
   private int numGrayChannels_;
   private boolean diskCached_;
   private AcquisitionEngine eng_;
   private HyperstackControls hc_;
   private String status_ = "";
   private ScrollbarWithLabel pSelector;
   private boolean multiPosition_ = false;
   private int numPositions_;
   private int curPosition_ = -1;
   private ChannelDisplaySettings[] channelSettings_;

   MMVirtualAcquisition(String name, String dir, boolean newData, boolean virtual) {
      name_ = name;
      dir_ = dir;
      newData_ = newData;
      diskCached_ = virtual;
   }

   public void setDimensions(int frames, int channels, int slices, int positions) throws MMScriptException {
      if (initialized_) {
         throw new MMScriptException("Can't change dimensions - the acquisition is already initialized");
      }
      if (summaryMetadata_ == null) {
         summaryMetadata_ = new HashMap<String,String>();
      }
      numFrames_ = frames;
      numChannels_ = channels;
      numSlices_ = slices;
      numPositions_ = Math.max(positions,1);
      displaySettings_ = new Map[channels];
      for (int i = 0; i < channels; ++i) {
         displaySettings_[i] = new HashMap<String,String>();
      }

      MDUtils.put(summaryMetadata_,"Acquisition-Channels",numChannels_);
      MDUtils.put(summaryMetadata_,"Acquisition-Slices",numSlices_);
      MDUtils.put(summaryMetadata_,"Acquisition-Frames",numFrames_);
      MDUtils.put(summaryMetadata_,"Acquisition-Positions",numPositions_);
   }

   public void setImagePhysicalDimensions(int width, int height, int depth) throws MMScriptException {
      width_ = width;
      height_ = height;
      depth_ = depth;
      int type = 0;

      if (depth_ == 1)
         type = ImagePlus.GRAY8;
      if (depth_ == 2)
         type = ImagePlus.GRAY16;
      if (depth_ == 4)
         type = ImagePlus.COLOR_RGB;
      if (depth_ == 8)
         type = 64;
      if ((depth_ == 1) || (depth_ == 2))
         numComponents_ = 1;
      else if ((depth_ == 4 || depth_ == 8))
         numComponents_ = 3;

      try {
         MDUtils.setWidth(summaryMetadata_, width);
         MDUtils.setHeight(summaryMetadata_, height);
         MDUtils.setImageType(summaryMetadata_, type);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   public int getChannels() {
      return numChannels_;
   }

   public int getDepth() {
      return depth_;
   }

   public int getFrames() {
      return numFrames_;
   }

   public int getHeight() {
      return height_;
   }

   public int getSlices() {
      return numSlices_;
   }

   public int getWidth() {
      return width_;
   }

   public boolean isInitialized() {
      return initialized_;
   }

   public void close() {
      //compositeImage_.hide();
      initialized_ = false;
      if (imageCache_ != null)
         imageCache_.finished();
   }

   public void closeImage5D() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public AcquisitionData getAcqData() {
      throw new UnsupportedOperationException("Not supported.");
   }

   public String getProperty(String propertyName) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public String getProperty(int frame, int channel, int slice, String propName) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public boolean hasActiveImage5D() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void initialize() throws MMScriptException {
      if (!newData_) {
         try {
            summaryMetadata_ = imageCache_.getSummaryMetadata();
            width_ = MDUtils.getWidth(summaryMetadata_);
            height_ = MDUtils.getHeight(summaryMetadata_);
            pixelType_ = MDUtils.getPixelType(summaryMetadata_);
            numSlices_ = MDUtils.getInt(summaryMetadata_, "Acquisition-Slices");
            numFrames_ = MDUtils.getInt(summaryMetadata_, "Acquisition-Frames");
            numChannels_ = MDUtils.getInt(summaryMetadata_, "Acquisition-Channels");
            numPositions_ = MDUtils.getInt(summaryMetadata_, "Acquisition-Positions");
            numComponents_ = MDUtils.getNumberOfComponents(summaryMetadata_);
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
         }
      }
      imageCache_.setSummaryMetadata(summaryMetadata_);
      numGrayChannels_ = numComponents_ * numChannels_;
      AcquisitionVirtualStack virtualStack;
      virtualStacks_ = new ArrayList<AcquisitionVirtualStack>();
      channelSettings_ = new ChannelDisplaySettings[numChannels_];
      for (int pos=0;pos<numPositions_;++pos) {
         virtualStack = new AcquisitionVirtualStack(width_, height_, null, 
                 imageCache_, numGrayChannels_ * numSlices_ * numFrames_, pos, this);
         try {
            virtualStack.setType(MDUtils.getSingleChannelType(summaryMetadata_));
            virtualStacks_.add(virtualStack);
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
         }
      }
      initialized_ = true;
   }

   public void insertImage(Object pixels, int frame, int channel, int slice)
           throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   private int getPositionIndex(TaggedImage taggedImg) throws Exception {
      int pos;
      if (numPositions_ > 1) {
         pos = MDUtils.getPositionIndex(taggedImg.tags);
      } else {
         pos = 0;
      }
      return pos;
   }

   private void setChannelColor(CompositeImage compositeImage, int channel, Color col) {
      int oldChan = compositeImage.getChannel();
      setChannelWithoutUpdate(channel + 1);
      compositeImage.setChannelLut(compositeImage.createLutFromColor(col));
      setChannelWithoutUpdate(oldChan);
   }


   private void updateWindow() {
      if (newData_) {
         if (acquisitionIsRunning()) {
            if (!abortRequested()) {
               if (isPaused()) {
                  status_ = "Paused";
               } else {
                  status_ = "Running";
               }
            } else {
               status_ = "Interrupted";
               hc_.disableAcquisitionControls();
            }
         } else {
            if (!status_.contentEquals("Interrupted"))
               status_ = "Finished";
            hc_.disableAcquisitionControls();
         }
      } else {
         status_ = "On disk";
         hc_.disableAcquisitionControls();
      }
      hc_.enableShowFolderButton(diskCached_);
      hyperImage_.getWindow().setTitle(new File(dir_).getName() + " (" + status_ + ")");
   }

   public void insertImage(TaggedImage taggedImg) throws MMScriptException {

      try {
         int pos = getPositionIndex(taggedImg);
         virtualStacks_.get(pos).insertImage(taggedImg);
         if (hyperImage_ == null) {
            show(pos);
         }
         updateWindow();
         Map<String, String> md = taggedImg.tags;
         if (numChannels_ > 1) {
            ((CompositeImage) hyperImage_).setChannelsUpdated();
         }
         try {
            // if ((hyperImage_.getFrame() - 1) > (MDUtils.getFrame(md) - 2)) {

            pSelector.setValue(1 + pos);
            hyperImage_.setPosition(1 + MDUtils.getChannelIndex(md), 1 + MDUtils.getSliceIndex(md), 1 + MDUtils.getFrameIndex(md));
            //  }
         } catch (Exception e) {
            ReportingUtils.logError(e);
         }
         if (hyperImage_.getFrame() == 1) {
            try {
               ImageStatistics stat = hyperImage_.getStatistics();
               if (MDUtils.isRGB(taggedImg)) {
                  for (int i = 1; i <= numGrayChannels_; ++i) {
                     (hyperImage_).setPosition(i, MDUtils.getFrameIndex(taggedImg.tags), MDUtils.getFrameIndex(taggedImg.tags));
                     hyperImage_.setDisplayRange(stat.min, stat.max);
                     hyperImage_.updateAndDraw();
                  }
               } else {
                  double min = hyperImage_.getDisplayRangeMin();
                  double max = hyperImage_.getDisplayRangeMax();
                  System.out.println("min"+min +"max"+max);
                  if (hyperImage_.getSlice() == 1) {
                     min = Double.MAX_VALUE;
                     max = Double.MIN_VALUE;
                  }
                  min = Math.min(min, stat.min);
                  max = Math.max(max, stat.max);
                  hyperImage_.setDisplayRange(min, max);
                  hyperImage_.updateAndDraw();
                  int chan = MDUtils.getChannelIndex(md);
                  if (channelSettings_[chan] == null) {
                     channelSettings_[chan] = new ChannelDisplaySettings();
                  }
                  channelSettings_[chan].min = (int) min;
                  channelSettings_[chan].max = (int) max;
                  channelSettings_[chan].gamma = 1.0;
                  channelSettings_[chan].color = Color.red;
               }
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }
         }

      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   public void setPosition(int p) {
      if (curPosition_ != p) {
         double min = hyperImage_.getDisplayRangeMin();
         double max = hyperImage_.getDisplayRangeMax();
         hyperImage_.setStack(virtualStacks_.get(p-1));
         hyperImage_.setDisplayRange(min, max);
         virtualStacks_.get(p-1).setImagePlus(hyperImage_);
         if (numChannels_ > 1) {
            ((CompositeImage) hyperImage_).setChannelsUpdated();
         }
         hyperImage_.updateAndDraw();
         curPosition_ = p;
      }
   }

   boolean pause() {
      if (eng_.isPaused())
         eng_.setPause(false);
      else
         eng_.setPause(true);
      updateWindow();
      return (eng_.isPaused());
   }

   boolean abort() {
      if (eng_ != null)
         if (eng_.abortRequest()) {
            updateWindow();
            return true;
         }
      return false;
   }

   public void setEngine(AcquisitionEngine eng) {
      eng_ = eng;
   }

   public boolean acquisitionIsRunning() {
      if (eng_ != null)
         return eng_.isAcquisitionRunning();
      else
         return false;
   }

   public boolean abortRequested() {
      if (eng_ != null)
         return eng_.abortRequested();
      else
         return false;
   }

   private boolean isPaused() {
      if (eng_ != null)
         return eng_.isPaused();
      else
         return false;
   }

   boolean saveAs() {
      String prefix;
      String root;
      for (;;) {
         final JFileChooser fc = new JFileChooser(new File(dir_).getParent());
         fc.setDialogTitle("Please choose a location for the data set.");
         fc.showSaveDialog(hyperImage_.getWindow());
         File f = fc.getSelectedFile();
         if (f == null) // Canceled.
            return false;
         prefix = f.getName();
         root = new File(f.getParent()).getAbsolutePath();
         if (f.exists()) {
            ReportingUtils.showMessage(prefix + " already exists! Please choose another name.");
         } else {
            break;
         }
      }

      TaggedImageStorageDiskDefault newFileManager = new TaggedImageStorageDiskDefault(root + "/" + prefix, true,
                 summaryMetadata_);
      imageCache_.saveAs(newFileManager);
      diskCached_ = true;
      dir_ = root + "/" + prefix;
      name_ = prefix;
      newData_ = false;
      updateWindow();
      return true;
   }


   public void show(int position) {
      ImagePlus imgp = new MMImagePlus(dir_, virtualStacks_.get(position));
      for (AcquisitionVirtualStack virtualStack:virtualStacks_) {
         virtualStack.setImagePlus(imgp);
      }
      imgp.setDimensions(numGrayChannels_, numSlices_, numFrames_);
      if (numGrayChannels_ > 1) {
         hyperImage_ = new CompositeImage(imgp, CompositeImage.COMPOSITE);
      } else {
         hyperImage_ = imgp;
         imgp.setOpenAsHyperStack(true);
      }
      if (numGrayChannels_ == numChannels_)
         updateChannelColors();

      // final ImageWindow win = hyperImage_.getWindow();
      final ImageWindow win = new StackWindow(hyperImage_) {

         public void windowClosing(WindowEvent e) {
            if (eng_ != null && eng_.isAcquisitionRunning()) {
               if (!abort()) {
                  return;
               }
            }

            if (diskCached_ == false) {
               int result = JOptionPane.showConfirmDialog(this,
                       "This data set has not yet been saved.\n"
                       + "Do you want to save it?",
                       "Closing image...",
                       JOptionPane.YES_NO_CANCEL_OPTION);
               if (result == JOptionPane.CANCEL_OPTION) {
                  return;
               } else if (result == JOptionPane.YES_OPTION) {
                  if (!saveAs()) {
                     return;
                  }
               }
            }
            imageCache_ = null;
            virtualStacks_ = null;
            close();
            hyperImage_ = null;


            super.windowClosing(e);
         }

         public void windowActivated(WindowEvent e) {
            if (!isClosed()) {
               super.windowActivated(e);
            }
         }
      };
      
      hyperImage_.show();
      ScrollbarWithLabel pSelector = createPositionScrollbar(numPositions_);
      if (numPositions_ > 1) {
         win.add(pSelector);
      }

      hc_ = new HyperstackControls(this, win);
      win.add(hc_);
      ImagePlus.addImageListener(hc_);
      
      win.pack();

      if (!newData_) {
         ((CompositeImage) hyperImage_).setChannelsUpdated();
         hyperImage_.updateAndDraw();
      }
      updateWindow();
      System.out.println("1. focusListeners: " + win.getFocusListeners().length);
   }

   public ScrollbarWithLabel createPositionScrollbar(int nPositions) {
			pSelector = new ScrollbarWithLabel(null, 1, 1, 1, nPositions+1, 'p') {
            public void setValue(int v) {
               if (this.getValue() != v) {
                  super.setValue(v);
                  setPosition(v);
               }
            }
         };
			//if (ij!=null) cSelector.addKeyListener(ij);
			pSelector.setFocusable(false); // prevents scroll bar from blinking on Windows
			pSelector.setUnitIncrement(1);
			pSelector.setBlockIncrement(1);
			pSelector.addAdjustmentListener(new AdjustmentListener() {
            public void adjustmentValueChanged(AdjustmentEvent e) {
               setPosition(pSelector.getValue());
               ReportingUtils.logMessage(""+pSelector.getValue());
            }
         });
         return pSelector;
   }

   
   public void setChannelColor(int channel, int rgb) throws MMScriptException {
      setChannelAppearance(channel, new Color(rgb), 0, 255, 1.0);
   }

   public void setChannelDisplaySettings(int channel, ChannelDisplaySettings settings) {
      setChannelAppearance(channel, settings.color, settings.min, settings.max, settings.gamma);
      channelSettings_[channel] = settings;
   }

   public ChannelDisplaySettings getChannelDisplaySettings(int channel) {
      return channelSettings_[channel];
   }

   public void setChannelAppearance(int channel, Color color, int min, int max, double gamma) {
      int rgb = color.getRGB();
      displaySettings_[channel].put("ChannelColor", String.format("%d", rgb));
      LUT lut = ImageUtils.makeLUT(color, 0, 255, 1, 8);
      if (hyperImage_ instanceof CompositeImage) {
         CompositeImage ci = (CompositeImage) hyperImage_;
         int oldChan = ci.getChannel();
         setChannelWithoutUpdate(channel + 1);
         ci.setChannelColorModel(lut);
         ci.setChannelsUpdated();
         ci.updateAndRepaintWindow();
         setChannelWithoutUpdate(oldChan);
         ci.updateAndRepaintWindow();
      }
      //updateChannelColors();
   }

   public void setChannelContrast(int channel, int min, int max) throws MMScriptException {
      displaySettings_[channel].put("ChannelContrastMin", String.format("%d", min));
      displaySettings_[channel].put("ChannelContrastMax", String.format("%d", max));
   }

   public void setChannelName(int channel, String name) throws MMScriptException {
      displaySettings_[channel].put("ChannelName", name);
   }

   public Map<String,String> getCurrentMetadata() {
      int index = getCurrentFlatIndex();
      int posIndex = pSelector.getValue() - 1;
      return virtualStacks_.get(posIndex).getTaggedImage(index).tags;
   }

   private int getCurrentFlatIndex() {
      return hyperImage_.getCurrentSlice();
   }

   public ImagePlus getImagePlus() {
      return hyperImage_;
   }

   public void setComment(String comment) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setContrastBasedOnFrame(int frame, int slice) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setProperty(String propertyName, String value) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setProperty(int frame, int channel, int slice, String propName, String value) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setRootDirectory(String dir) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setSystemProperties(Map<String,String> md) throws MMScriptException {
      systemMetadata_ = md;
   }

   public void setSystemState(int frame, int channel, int slice, JSONObject state) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported.");
   }

   public boolean windowClosed() {
      return false;
   }

   void showFolder() {
      if (dir_.length() != 0) {
         try {
            if (JavaUtils.isWindows()) {
               Runtime.getRuntime().exec("Explorer /n,/select," + dir_);
            } else if (JavaUtils.isMac()) {
               Runtime.getRuntime().exec("open " + dir_);
            }
         } catch (IOException ex) {
            ReportingUtils.logError(ex);
         }
      }
   }

   private void setChannelWithoutUpdate(int channel) {
      int z = hyperImage_.getSlice();
      int t = hyperImage_.getFrame();
      
      hyperImage_.setPositionWithoutUpdate(channel, z, t);

   }

   private void updateChannelColors() {
      if (hyperImage_ instanceof CompositeImage) {
         if (displaySettings_ != null) {
            CompositeImage compositeImage = (CompositeImage) hyperImage_;
            for (int channel = 0; channel < compositeImage.getNChannels(); ++channel) {
               int color = Integer.parseInt(displaySettings_[channel].get("ChannelColor"));
               Color col = new Color(color);
               setChannelColor(compositeImage, channel, col);
               //compositeImage.setChannelsUpdated();
               //compositeImage.updateAndDraw();
            }
         }
      }
   }

   public void setSummaryProperties(Map<String,String> md) {
      summaryMetadata_.putAll(md);
   }

   public void setSystemState(Map<String,String> md) {
      systemMetadata_ = md;
   }

   void setPlaybackFPS(double fps) {
      if (hyperImage_ != null) {
         try {
            JavaUtils.setRestrictedFieldValue(null, Animator.class, "animationRate", (double) fps);
         } catch (NoSuchFieldException ex) {
            ReportingUtils.showError(ex);
         }
      }
   }

   double getPlaybackFPS() {
      return Animator.getFrameRate();
   }

   public void setCache(MMImageCache imageCache) {
      imageCache_ = imageCache;
   }


   Color getChannelColor(int channel) {
      if (! (hyperImage_ instanceof CompositeImage))
         return null;

      LUT cm = hyperImage_.getLuts()[channel-1];
      if (cm==null)
         return Color.black;
      int index = cm.getMapSize() - 1;
      int r = cm.getRed(index);
      int g = cm.getGreen(index);
      int b = cm.getBlue(index);
      //IJ.log(index+" "+r+" "+g+" "+b);
      if (r<100 || g<100 || b<100)
         return new Color(r, g, b);
      else
         return Color.black;
   }


   Color[] getChannelColors() {
      if (! (hyperImage_ instanceof CompositeImage))
         return null;
      
      int nChannels = hyperImage_.getNChannels();
      Color[] chanColors = new Color[nChannels];
      for (int i=0;i<nChannels;++i) {
         chanColors[i] = getChannelColor(i+1);
      }
      return chanColors;
   }


   int[] getCurrentSlices() {
      ImagePlus image = hyperImage_;
      int currentFlatIndex = image.getCurrentSlice();
      int frame = image.getFrame();
      int slice = image.getSlice();
      int nChannels = image.getNChannels();
      int [] indices = new int[nChannels];
      for (int i=0;i<nChannels;++i) {
         indices[i] = image.getStackIndex(i+1,slice,frame);
      }
      return indices;
   }


   String[] getChannelNames() {
      if (hyperImage_ instanceof CompositeImage) {
         AcquisitionVirtualStack stack = (AcquisitionVirtualStack) hyperImage_.getStack();

         int nChannels = hyperImage_.getNChannels();
         int[] indices = getCurrentSlices();
         String[] chanNames = new String[nChannels];
         for (int i=0;i<nChannels;++i) {
            try {
               chanNames[i] = MDUtils.getChannelName(stack.getTaggedImage(indices[i]).tags);
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
      if (! (hyperImage_ instanceof CompositeImage))
         return;
      CompositeImage ci = (CompositeImage) hyperImage_;
      ci.getActiveChannels()[channelIndex] = visible;
      ci.updateAllChannelsAndDraw();
   }

   public int[] getChannelHistogram(int channelIndex) {
      return hyperImage_.getStack().getProcessor(channelIndex+1).getHistogram();
   }

}
