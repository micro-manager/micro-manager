package org.micromanager.data.test;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import ij.ImagePlus;

import java.awt.Component;
import java.lang.Math;
import java.util.HashMap;

import org.micromanager.api.data.Coords;
import org.micromanager.api.data.Datastore;
import org.micromanager.api.data.Image;
import org.micromanager.api.data.NewImageEvent;

import org.micromanager.imagedisplay.DisplayWindow;
import org.micromanager.imagedisplay.IMMImagePlus;
import org.micromanager.imagedisplay.MMCompositeImage;
import org.micromanager.imagedisplay.MMImagePlus;

import org.micromanager.utils.ReportingUtils;


/**
 * This class is responsible for intermediating between the different
 * components that combine to form the image display.
 */
public class TestDisplay {
   private Datastore store_;
   private DisplayWindow window_;
   private ImagePlus ijImage_;
   private MMImagePlus plus_;
   private MMVirtualStack stack_;
   private HyperstackControls controls_;
   private HistogramsPanel histograms_;
   private MetadataPanel metadata_;

   private EventBus bus_;
   
   public TestDisplay(Datastore store) {
      store_ = store;
      store_.registerForEvents(this);
      bus_ = new EventBus();
      bus_.register(this);
      stack_ = new MMVirtualStack(store);
      plus_ = new MMImagePlus("foo", stack_, bus_);
      plus_.setOpenAsHyperStack(true);
      stack_.setImagePlus(plus_);
      // The ImagePlus object needs to be pseudo-polymorphic, depending on
      // the number of channels in the Datastore. However, we may not
      // have all of the channels available to us at the time this display is
      // created, so we may need to re-create things down the road.
      ijImage_ = plus_;
      if (store_.getMaxIndex("channel") > 0) {
         // Have multiple channels.
         shiftToCompositeImage();
      }
      setIJBounds();
      if (ijImage_ instanceof MMCompositeImage) {
         ((MMCompositeImage) ijImage_).reset();
      }

      window_ = new DisplayWindow(ijImage_, generateControls(), bus_);
      window_.setTitle("Hello, world!");
      histograms_.calcAndDisplayHistAndStats(true);
   }

   // Turns out we need to represent a multichannel image, so convert from
   // ImagePlus to CompositeImage.
   private void shiftToCompositeImage() {
      // TODO: assuming mode 1 for now.
      ReportingUtils.logError("Changing to multiple channels");
      ijImage_ = new MMCompositeImage(plus_, 1, "foo", bus_);
      ijImage_.setOpenAsHyperStack(true);
      MMCompositeImage composite = (MMCompositeImage) ijImage_;
      int numChannels = store_.getMaxIndex("channel") + 1;
      composite.setNChannelsUnverified(numChannels);
      ReportingUtils.logError("Set number of channels to " + numChannels);
      stack_.setImagePlus(ijImage_);
      composite.reset();

      if (window_ != null) {
         window_.setupLayout(generateControls());
         histograms_.calcAndDisplayHistAndStats(true);
      }
   }

   // Ensure that our ImageJ object has the correct number of channels, 
   // frames, and slices.
   private void setIJBounds() {
      IMMImagePlus temp = (IMMImagePlus) ijImage_;
      int numChannels = Math.max(1, store_.getMaxIndex("channel") + 1);
      int numFrames = Math.max(1, store_.getMaxIndex("frame") + 1);
      int numSlices = Math.max(1, store_.getMaxIndex("slice") + 1);
      temp.setNChannelsUnverified(numChannels);
      temp.setNFramesUnverified(numFrames);
      temp.setNSlicesUnverified(numSlices);
      // TODO: VirtualAcquisitionDisplay folds "components" into channels;
      // what are components used for?
      plus_.setDimensions(numChannels, numSlices, numFrames);
   }

   /**
    * Generate the controls that we'll stuff into the DisplayWindow, along
    * with the rules that will be used to lay them out.
    */
   private HashMap<Component, String> generateControls() {
      HashMap<Component, String> result = new HashMap<Component, String>();
      controls_ = new HyperstackControls(store_, stack_, bus_, false, false);
      result.put(controls_, "align center, wrap, growx");
      histograms_ = new HistogramsPanel(store_, ijImage_, bus_);
      result.put(histograms_, "align center, wrap");
      metadata_ = new MetadataPanel(store_);
      result.put(metadata_, "dock east, growy");
      return result;
   }

   /**
    * Datastore has received a new image.
    */
   @Subscribe
   public void onNewImage(NewImageEvent event) {
      ReportingUtils.logError("Display caught new image at " + event.getCoords());
      // Check if we're transitioning from grayscale to multi-channel at this
      // time.
      if (!(ijImage_ instanceof MMCompositeImage) && 
            event.getImage().getCoords().getPositionAt("channel") > 0) {
         // Have multiple channels.
         ReportingUtils.logError("Augmenting to MMCompositeImage now");
         shiftToCompositeImage();
         window_.setupLayout(generateControls());
      }
      if (ijImage_ instanceof MMCompositeImage) {
         // Verify that ImageJ has the right number of channels.
         int numChannels = store_.getMaxIndex("channel");
         MMCompositeImage composite = (MMCompositeImage) ijImage_;
         composite.setNChannelsUnverified(numChannels);
         ReportingUtils.logError("Set number of channels to " + store_.getMaxIndex("channel"));
         composite.reset();
         for (int i = 0; i < numChannels; ++i) {
            if (composite.getProcessor(i + 1) != null) {
               composite.getProcessor(i + 1).setPixels(event.getImage().getRawPixels());
            }
         }
      }
      setIJBounds();
      ijImage_.getProcessor().setPixels(event.getImage().getRawPixels());
   }

   /**
    * Something on our display bus (i.e. not the Datastore bus) wants us to
    * redisplay.
    */
   @Subscribe
   public void onDrawEvent(DrawEvent event) {
      Coords drawCoords = stack_.getCurrentImageCoords();
      ReportingUtils.logError("Drawing at " + drawCoords);
      ijImage_.updateAndDraw();
//      ijImage_.drawWithoutUpdate();
      histograms_.calcAndDisplayHistAndStats(true);
      metadata_.imageChangedUpdate(store_.getImage(drawCoords));
   }
}
