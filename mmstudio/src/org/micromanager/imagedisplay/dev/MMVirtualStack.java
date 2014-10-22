package org.micromanager.imagedisplay.dev;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.process.ImageProcessor;

import java.util.HashMap;

import org.micromanager.api.data.Coords;
import org.micromanager.api.data.Datastore;
import org.micromanager.api.display.NewImagePlusEvent;

import org.micromanager.data.DefaultCoords;
import org.micromanager.data.DefaultImage;

import org.micromanager.imagedisplay.IMMImagePlus;

import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.ReportingUtils;

/**
 * This stack class provides the ImagePlus with images from the Datastore.
 * It is needed to let ImageJ access our data, so that ImageJ tools and plugins
 * can operate on it. Though, since ImageJ only knows about three axes
 * (channel, frame, and slice), it won't be able to access the *entire*
 * dataset if there are other axes (like, say, stage position). It can only
 * ever access an XYZTC volume.
 */
public class MMVirtualStack extends ij.VirtualStack {
   private Datastore store_;
   private EventBus displayBus_;
   private ImagePlus plus_;
   private Coords curCoords_;
   private HashMap<Integer, DefaultImage> channelToLastValidImage_;

   public MMVirtualStack(Datastore store, EventBus displayBus) {
      store_ = store;
      displayBus_ = displayBus;
      displayBus_.register(this);
      curCoords_ = new DefaultCoords.Builder().build();
      channelToLastValidImage_ = new HashMap<Integer, DefaultImage>();
   }

   public void setImagePlus(ImagePlus plus) {
      plus_ = plus;
   }

   /**
    * Retrieve the image at the specified index, which we map into a
    * channel/frame/z offset. This will also update curCoords_ as needed.
    * Note that we only pay attention to a given offset if we already have
    * a position along that axis (e.g. so that we don't try to ask the
    * Datastore for an image at time=0 when none of the images in the Datastore
    * have a time axis whatsoever).
    */
   private DefaultImage getImage(int flatIndex) {
      // Note: index is 1-based.
      if (flatIndex > plus_.getStackSize()) {
         ReportingUtils.logError("Stack asked for image at " + flatIndex + 
               " that exceeds total of " + plus_.getStackSize() + " images");
         return null;
      }
      // These coordinates are missing all axes that ImageJ doesn't know 
      // about (e.g. stage position), so we augment curCoords with them and
      // use that for our lookup.
      // If we have no ImagePlus yet to translate this index into something
      // more meaningful, then default to all zeros.
      int channel = -1, z = -1, frame = -1;
      if (plus_ != null) {
         int[] pos3D = plus_.convertIndexToPosition(flatIndex);
         // ImageJ coordinates are 1-indexed.
         channel = pos3D[0] - 1;
         z = pos3D[1] - 1;
         frame = pos3D[2] - 1;
      }
      // Only augment a given axis if it's actually present in our datastore.
      Coords.CoordsBuilder builder = curCoords_.copy();
      if (store_.getMaxIndex("channel") != -1) {
         builder.position("channel", channel);
      }
      if (store_.getMaxIndex("z") != -1) {
         builder.position("z", z);
      }
      if (store_.getMaxIndex("time") != -1) {
         builder.position("time", frame);
      }
      // Augment all missing axes with zeros.
      // TODO: is this always what we want to do? It makes an implicit
      // assumption that all images in the datastore have the same axes.
      for (String axis : store_.getAxes()) {
         if (builder.getPositionAt(axis) == -1) {
            builder.position(axis, 0);
         }
      }
      curCoords_ = builder.build();
      DefaultImage result = (DefaultImage) store_.getImage(curCoords_);
      if (result == null) {
         // HACK: ImageJ may ask us for images that aren't available yet,
         // for example if a draw attempt happens in-between images for a
         // multichannel Z-stack. For now, we return the most recent image
         // we've received for that channel (if any).
         // TODO: In the future we may want multiple strategies for handling
         // missing images, depending on context.
         if (channel != -1 && channelToLastValidImage_.containsKey(channel)) {
            result = channelToLastValidImage_.get(channel);
         }
      }
      if (channel != -1) {
         channelToLastValidImage_.put(channel, result);
      }
      return result;
   }

   /**
    * Retrieve the pixel buffer for the image at the specified offset. See
    * getImage() for more details.
    */
   @Override
   public Object getPixels(int flatIndex) {
      if (plus_ == null) {
         ReportingUtils.logError("Asked to get pixels when I don't have an ImagePlus");
         return null;
      }
      DefaultImage image = getImage(flatIndex);
      if (image != null) {
         if (image.getNumComponents() != 1) {
            // Extract the appropriate component.
            return image.getRawPixelsForComponent((flatIndex - 1) % image.getNumComponents());
         }
         return image.getRawPixels();
      }
      ReportingUtils.logError("Null image at " + curCoords_);
      return null;
   }

   @Override
   public ImageProcessor getProcessor(int flatIndex) {
      if (plus_ == null) {
         ReportingUtils.logError("Tried to get a processor when there's no ImagePlus");
         return null;
      }
      DefaultImage image = getImage(flatIndex);
      if (image == null) {
         ReportingUtils.logError("Tried to get a processor for an invalid image index " + flatIndex);
         return null;
      }
      int width = image.getWidth();
      int height = image.getHeight();
      Object pixels = getPixels(flatIndex);
      int mode = -1;
      switch(image.getBytesPerPixel()) {
         case 1:
            mode = ImagePlus.GRAY8;
            break;
         case 2:
            mode = ImagePlus.GRAY16;
            break;
         case 4:
            if (image.getNumComponents() == 3) {
               // Rely on our getPixels() call to have split out the
               // appropriate component already.
               mode = ImagePlus.GRAY8;
            }
            else {
               mode = ImagePlus.COLOR_RGB;
            }
            break;
         default:
            ReportingUtils.showError("Unrecognized image with " + image.getBytesPerPixel() + " bytes per pixel");
      }
      ImageProcessor result = ImageUtils.makeProcessor(mode, width, height, pixels);
      return result;
   }

   @Override
   public int getSize() {
      // Calculate the total number of "addressable" images along the
      // axes that ImageJ knows about.
      String[] axes = {"channel", "time", "z"};
      int result = 1;
      for (String axis : axes) {
         if (store_.getMaxIndex(axis) != -1) {
            result *= (store_.getMaxIndex(axis) + 1);
         }
      }
      return result;
   }

   @Override
   public String getSliceLabel(int n) {
      return Integer.toString(n);
   }

   /**
    * Update the current position we are centered on. This in turn will affect
    * future calls to getPixels(). In the process we also ensure that the
    * ImagePlus object has the right dimensions to encompass these coordinates.
    */
   public void setCoords(Coords coords) {
      curCoords_ = coords;
      int numChannels = Math.max(1, store_.getMaxIndex("channel") + 1);
      int numFrames = Math.max(1, store_.getMaxIndex("time") + 1);
      int numSlices = Math.max(1, store_.getMaxIndex("z") + 1);
      if (plus_ instanceof IMMImagePlus) {
         IMMImagePlus temp = (IMMImagePlus) plus_;
         temp.setNChannelsUnverified(numChannels);
         temp.setNFramesUnverified(numFrames);
         temp.setNSlicesUnverified(numSlices);
      }
      // HACK: if we call this with all 1s then ImageJ will create a new
      // display window in addition to our own (WHY?!), so only call it if
      // we actually have at least 2 images across these axes.
      if (numChannels != 1 || numSlices != 1 || numFrames != 1) {
         plus_.setDimensions(numChannels, numSlices, numFrames);
      }
      int channel = coords.getPositionAt("channel") + 1;
      int z = coords.getPositionAt("z") + 1;
      int time = coords.getPositionAt("time") + 1;
      boolean isCompositeMode = (plus_.isComposite() &&
            ((CompositeImage) plus_).getMode() == CompositeImage.COMPOSITE);
      if (z != plus_.getSlice() || time != plus_.getFrame() ||
            (!isCompositeMode && channel != plus_.getChannel())) {
         plus_.setPosition(channel, z, time);
      }
      displayBus_.post(new StackPositionChangedEvent(curCoords_));
   }

   /**
    * Return our current coordinates.
    */
   public Coords getCurrentImageCoords() {
      return curCoords_;
   }

   @Subscribe
   public void onNewImagePlus(NewImagePlusEvent event) {
      setImagePlus(event.getImagePlus());
   }

   public Datastore getDatastore() {
      return store_;
   }
}
