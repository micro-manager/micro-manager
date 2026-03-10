package org.micromanager.ndviewer2.internal.gui;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;
import mmcorej.org.json.JSONObject;
import org.micromanager.ndviewer2.NDViewer2DataSource;
import org.micromanager.ndviewer2.internal.NDViewer2;
import org.micromanager.ndviewer2.internal.gui.contrast.DisplaySettings;


/**
 * This class keeps track of the information about how to display the data the viewer is showing.
 */
public class DisplayModel {

   private DisplaySettings displaySettings_;
   protected DataViewCoords viewCoords_;
   private NDViewer2DataSource data_;
   private NDViewer2 display_;

   // Axes may use integer or string positions. Keep track of which
   // uses which ones do this here, and which string values map to which
   // Integer positions (because these are needed for display)
   private ConcurrentHashMap<String, LinkedList<String>> stringAxes_ = new ConcurrentHashMap<>();
   private final boolean rgb_;



   public DisplayModel(NDViewer2 display, NDViewer2DataSource data, Preferences prefs,
                       boolean rgb) {
      rgb_ = rgb;
      display_ = display;
      displaySettings_ = new DisplaySettings(prefs);
      data_ = data;

      int[] bounds = data.getBounds();
      viewCoords_ = new DataViewCoords(data, 0, 0,
              bounds == null ? null : (double) (bounds[2] - bounds[0]),
              bounds == null ? null : (double) (bounds[3] - bounds[1]),
              data.getBounds(), rgb);
   }

   /**
    * Need to call this when loading them from disk.
    */
   public void setDisplaySettings(DisplaySettings displaySettings) {
      this.displaySettings_ = displaySettings;
   }

   public DisplaySettings getDisplaySettings() {
      return displaySettings_;
   }

   public int getIntegerPositionFromStringPosition(String axisName, String axisPosition) {
      return stringAxes_.get(axisName).indexOf(axisPosition);
   }

   public String getStringPositionFromIntegerPosition(String axisName, int axisPosition) {
      return stringAxes_.get(axisName).get(axisPosition);
   }

   public void channelWasSetActiveByCheckbox(String channelName, boolean selected) {
      if (!displaySettings_.isCompositeMode()) {
         if (selected) {
            viewCoords_.setAxisPosition(NDViewer2.CHANNEL_AXIS, channelName);

            //only one channel can be active so inacivate others
            for (String channel : display_.getDisplayModel().getDisplayedChannels()) {
               displaySettings_.setActive(channel, channel.equals(
                        viewCoords_.getAxisPosition(NDViewer2.CHANNEL_AXIS)));
            }
         } else {
            //if channel turns off, nothing will show, so dont let this happen
         }
         //make sure other checkboxes update if they autochanged
         display_.updateActiveChannelCheckboxes();
      } else {
         //composite mode
         displaySettings_.setActive(channelName, selected);
      }
   }

   public void pan(int dx, int dy) {
      Point2D.Double offset = viewCoords_.getViewOffset();
      double newX = offset.x + (dx / viewCoords_.getMagnificationFromResLevel())
              * viewCoords_.getDownsampleFactor();
      double newY = offset.y + (dy / viewCoords_.getMagnificationFromResLevel())
              * viewCoords_.getDownsampleFactor();

      if (data_.getBounds() != null) {
         viewCoords_.setViewOffset(
                 Math.max(viewCoords_.xMin_, Math.min(newX, viewCoords_.xMax_
                         - viewCoords_.getFullResSourceDataSize().x)),
                 Math.max(viewCoords_.yMin_, Math.min(newY, viewCoords_.yMax_
                         - viewCoords_.getFullResSourceDataSize().y)));
      } else {
         viewCoords_.setViewOffset(newX, newY);
      }
   }

   public void zoom(double factor, Point mouseLocation) {
      //get zoom center in full res pixel coords
      Point2D.Double viewOffset = viewCoords_.getViewOffset();
      Point2D.Double sourceDataSize = viewCoords_.getFullResSourceDataSize();
      Point2D.Double zoomCenter;
      //compute centroid of the zoom in full res coordinates
      if (mouseLocation == null) {
         //if mouse not over image zoom to center
         zoomCenter = new Point2D.Double(viewOffset.x + sourceDataSize.x / 2,
                 viewOffset.y + sourceDataSize.y / 2);
      } else {
         zoomCenter = new Point2D.Double(
                 (long) viewOffset.x + mouseLocation.x
                         / viewCoords_.getMagnificationFromResLevel()
                         * viewCoords_.getDownsampleFactor(),
                 (long) viewOffset.y + mouseLocation.y
                         / viewCoords_.getMagnificationFromResLevel()
                         * viewCoords_.getDownsampleFactor());
      }

      //Do zooming--update size of source data
      double newSourceDataWidth = sourceDataSize.x * factor;
      double newSourceDataHeight = sourceDataSize.y * factor;
      if (newSourceDataWidth < 5 || newSourceDataHeight < 5) {
         return; //constrain maximum zoom
      }
      if (data_.getBounds() != null) {
         //don't let either of these go bigger than the actual data
         double overzoomXFactor = newSourceDataWidth / (viewCoords_.xMax_ - viewCoords_.xMin_);
         double overzoomYFactor = newSourceDataHeight / (viewCoords_.yMax_ - viewCoords_.yMin_);
         if (overzoomXFactor > 1 || overzoomYFactor > 1) {
            newSourceDataWidth = newSourceDataWidth / Math.max(overzoomXFactor, overzoomYFactor);
            newSourceDataHeight = newSourceDataHeight / Math.max(overzoomXFactor, overzoomYFactor);
         }
      }
      viewCoords_.setFullResSourceDataSize(newSourceDataWidth, newSourceDataHeight);

      double xOffset = (zoomCenter.x - (zoomCenter.x - viewOffset.x)
              * newSourceDataWidth / sourceDataSize.x);
      double yOffset = (zoomCenter.y - (zoomCenter.y - viewOffset.y)
              * newSourceDataHeight / sourceDataSize.y);
      //make sure view doesn't go outside image bounds
      if (data_.getBounds() != null) {
         viewCoords_.setViewOffset(
                 Math.max(viewCoords_.xMin_, Math.min(xOffset,
                         viewCoords_.xMax_ - viewCoords_.getFullResSourceDataSize().x)),
                 Math.max(viewCoords_.yMin_, Math.min(yOffset,
                         viewCoords_.yMax_ - viewCoords_.getFullResSourceDataSize().y)));
      } else {
         viewCoords_.setViewOffset(xOffset, yOffset);
      }
   }

   public void onCanvasResize(int w, int h) {
      Point2D.Double displaySizeOld = viewCoords_.getDisplayImageSize();
      //reshape the source image to match canvas aspect ratio
      //expand it, unless it would put it out of range
      double canvasAspect = w / (double) h;
      Point2D.Double source = viewCoords_.getFullResSourceDataSize();
      double sourceAspect = source.x / source.y;
      double newSourceX;
      double newSourceY;
      if (data_.getBounds() != null) {
         if (canvasAspect > sourceAspect) {
            newSourceX = canvasAspect / sourceAspect * source.x;
            newSourceY = source.y;
            //check that still within image bounds
         } else {
            newSourceX = source.x;
            newSourceY = source.y / (canvasAspect / sourceAspect);
         }

         double overzoomXFactor = newSourceX / (viewCoords_.xMax_ - viewCoords_.xMin_);
         double overzoomYFactor = newSourceY / (viewCoords_.yMax_ - viewCoords_.yMin_);
         if (overzoomXFactor > 1 || overzoomYFactor > 1) {
            newSourceX = newSourceX / Math.max(overzoomXFactor, overzoomYFactor);
            newSourceY = newSourceY / Math.max(overzoomXFactor, overzoomYFactor);
         }
      } else if (displaySizeOld.x != 0 && displaySizeOld.y != 0) {
         newSourceX = source.x * (w / displaySizeOld.x);
         newSourceY = source.y * (h / displaySizeOld.y);
      } else {
         newSourceX = source.x / sourceAspect * canvasAspect;
         newSourceY = source.y;
      }
      //move into visible area
      viewCoords_.setViewOffset(
              Math.max(viewCoords_.xMin_, Math.min(viewCoords_.xMax_
                      - newSourceX, viewCoords_.getViewOffset().x)),
              Math.max(viewCoords_.yMin_, Math.min(viewCoords_.yMax_
                      - newSourceY, viewCoords_.getViewOffset().y)));

      //set the size of the display iamge
      viewCoords_.setDisplayImageSize(w, h);
      //and the size of the source pixels from which it derives
      viewCoords_.setFullResSourceDataSize(newSourceX, newSourceY);
   }

   public void setViewOffset(double newX, double newY) {
      viewCoords_.setViewOffset(newX, newY);
   }

   public void setFullResSourceDataSize(double width, double height) {
      viewCoords_.setFullResSourceDataSize(width, height);
   }

   public Point2D.Double getFullResSourceDataSize() {
      return viewCoords_.getFullResSourceDataSize();
   }

   public DataViewCoords copyViewCoords() {
      return viewCoords_.copy();
   }

   public Point2D.Double getDisplayImageSize() {
      return viewCoords_.getDisplayImageSize();
   }

   public void setCompositeMode(boolean selected) {
      displaySettings_.setCompositeMode(selected);
      //select all channels if composite mode is being turned on
      if (selected) {
         for (String channel : getDisplayedChannels()) {
            displaySettings_.setActive(channel, true);
            display_.updateActiveChannelCheckboxes();
         }
      } else {
         for (String channel : getDisplayedChannels()) {
            if (viewCoords_.getAxesPositions().containsKey(NDViewer2.CHANNEL_AXIS)) {
               displaySettings_.setActive(channel, viewCoords_.getAxesPositions()
                        .get(NDViewer2.CHANNEL_AXIS).equals(channel));
               display_.updateActiveChannelCheckboxes();
            }
         }
      }
   }

   /**
    * Displayed channels are the actual channels, or if there are no channels, a dummy one is added.
    *
    * @return Displayed Channel names, or a dummy if there are no channels.
    */
   public List<String> getDisplayedChannels() {
      List<String> channels = new LinkedList<>();
      if (stringAxes_.containsKey(NDViewer2.CHANNEL_AXIS)) {
         channels = stringAxes_.get(NDViewer2.CHANNEL_AXIS);
      }
      if (channels.size() == 0) {
         channels.add(NDViewer2.NO_CHANNEL);
      }
      return channels;
   }

   /**
    * Called upon a new image arriving.
    */
   public void parseNewAxesToUpdateDisplayModel(HashMap<String, Object> axesPositions)  {
      // Update string valued axes, including channels
      for (String axis : axesPositions.keySet()) {
         if (!(axesPositions.get(axis) instanceof String)) {
            continue;
         }
         if (!stringAxes_.containsKey(axis)) {
            stringAxes_.put(axis, new LinkedList<String>());
         }
         if (!stringAxes_.get(axis).contains(axesPositions.get(axis))) {
            stringAxes_.get(axis).add((String) axesPositions.get(axis));
            if (axis.equals(NDViewer2.CHANNEL_AXIS)) {
               try {
                  SwingUtilities.invokeAndWait(new Runnable() {
                     @Override
                     public void run() {
                        // make sure GUI and display settings are in sync
                        display_.readHistogramControlsStateFromGUI();
                        String channelName = (String) axesPositions.get(NDViewer2.CHANNEL_AXIS);

                        if (!channelName.equals(NDViewer2.NO_CHANNEL)
                                 && displaySettings_.containsChannel(NDViewer2.NO_CHANNEL)) {
                           // remove the dummy channel
                           displaySettings_.removeChannel(NDViewer2.NO_CHANNEL);
                        }

                        int bitDepth = display_.getDataSource().getImageBitDepth(axesPositions);
                        //Add contrast controls and display settings
                        if (!displaySettings_.containsChannel(channelName)) {
                           displaySettings_.addChannel(channelName, bitDepth);
                        }
                        if (!displaySettings_.isCompositeMode()) {
                           // set only this new channel active
                           for (String cName : stringAxes_.get(NDViewer2.CHANNEL_AXIS)) {
                              displaySettings_.setActive(channelName, cName.equals(channelName));
                           }
                        }
                        display_.getGUIManager().addContrastControlsIfNeeded(channelName);
                     }
                  });
               } catch (Exception e) {
                  throw new RuntimeException(e);
               }
            }
         }
      }
   }

   public int[] getBounds() {
      return viewCoords_.getBounds();
   }

   public void setImageBounds(int[] newBounds) {
      viewCoords_.setImageBounds(newBounds);
   }

   public Point2D.Double getViewOffset() {
      return viewCoords_.getViewOffset();
   }

   public Object getAxisPosition(String axis) {
      return viewCoords_.getAxisPosition(axis);
   }

   public double getMagnification() {
      return viewCoords_.getMagnification();
   }

   public void setAxisPosition(String axis, Object o) {
      viewCoords_.setAxisPosition(axis, o);
   }

   public void scrollbarsMoved(HashMap<String, Object> axes) {
      // If the viewer is not in composite mode (i.e. one channel is shown at a time
      // then when the scrollbars are moved, the active channel should be changed
      // so that the checkbox changes
      if (!displaySettings_.isCompositeMode()) {
         //set all channels inactive except current one
         if (viewCoords_.getAxesPositions().containsKey(NDViewer2.CHANNEL_AXIS)) {
            String activeChannel = (String) viewCoords_.getAxesPositions()
                     .get(NDViewer2.CHANNEL_AXIS);
            for (String c : getDisplayedChannels()) {
               displaySettings_.setActive(c, activeChannel.equals(c));
               display_.getGUIManager().updateGUIFromDisplaySettings();
            }
         }
      }
   }

   public void updateDisplayBounds() {
      // Check for changed bounds of the underlying data
      if (display_.getDataSource().getBounds() != null) {
         int[] newBounds = display_.getDataSource().getBounds();
         int[] oldBounds = display_.getDisplayModel().getBounds();
         double xResize = (oldBounds[2] - oldBounds[0]) / (double) (newBounds[2] - newBounds[0]);
         double yResize = (oldBounds[3] - oldBounds[1]) / (double) (newBounds[3] - newBounds[1]);
         setImageBounds(newBounds);
         if (xResize < 1 || yResize < 1) {
            zoom(1 / Math.min(xResize, yResize), null);
         }
      }
   }

   public JSONObject getDisplaySettingsJSON() {
      if (displaySettings_ == null) {
         return null;
      }
      return displaySettings_.toJSON();
   }

   public DisplaySettings getDisplaySettingsObject() {
      return displaySettings_;
   }

   public boolean isCompositeMode() {
      return displaySettings_.isCompositeMode();
   }

   public boolean isIntegerAxis(String axis) {
      return !stringAxes_.containsKey(axis);
   }
}
