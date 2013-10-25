/*
 * Master stitched window to display real time stitched images, allow navigating of XY more easily
 */
package MMCustomization;

import ij.gui.ImageCanvas;
import java.awt.BorderLayout;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.acquisition.MMImageCache;
import org.micromanager.acquisition.VirtualAcquisitionDisplay;
import org.micromanager.api.ImageCache;
import org.micromanager.api.ImageCacheListener;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.internalinterfaces.DisplayControls;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.ReportingUtils;

public class DisplayPlus implements ImageCacheListener  {

   public static final String WINDOW_TITLE = "Stitched overview";
   //VirtualAcquisitionDisplay on top of which this display is built
   private VirtualAcquisitionDisplay vad_;

   public DisplayPlus(ImageCache stitchedCache) {
      vad_ = new VirtualAcquisitionDisplay(stitchedCache, null, WINDOW_TITLE);
      DisplayControls controls = new Controls();

      try {
         JavaUtils.setRestrictedFieldValue(vad_, VirtualAcquisitionDisplay.class, "controls_", controls);
      } catch (NoSuchFieldException ex) {
         ReportingUtils.showError("Couldn't create display controls");
      }
      vad_.show();
      //Zoom to 100%
      vad_.getImagePlus().getWindow().getCanvas().unzoom();

      stitchedCache.addImageCacheListener(this);
   }

   @Override
   public void imageReceived(TaggedImage taggedImage) {
      try {
         //remoake tags so original image storage doesnt get confused
         TaggedImage output = new TaggedImage(taggedImage.pix, new JSONObject(taggedImage.tags.toString()));
         //change so VAD position scrollbar doesnt render
         output.tags.put("PositionIndex", 0);
         vad_.imageReceived(output);
      } catch (JSONException ex) {
         ReportingUtils.showError("couldnt change image tags");
      }
   }

   @Override
   public void imagingFinished(String path) {
      vad_.imagingFinished(path);
   }

   
   
   
   class Controls extends DisplayControls {

      private JLabel statusLabel_;

      public Controls() {
         initComponents();
      }

      @Override
      public void imagesOnDiskUpdate(boolean bln) {
      }

      @Override
      public void acquiringImagesUpdate(boolean bln) {
      }

      @Override
      public void setStatusLabel(String string) {
      }

      @Override
      public void newImageUpdate(JSONObject jsono) {
      }

      private void initComponents() {

         setPreferredSize(new java.awt.Dimension(512, 45));

         this.setLayout(new BorderLayout());

         JPanel buttonPanel = new JPanel();
         buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));

         JPanel textPanel = new JPanel();
         textPanel.setLayout(new BorderLayout());

         this.add(buttonPanel, BorderLayout.CENTER);
         this.add(textPanel, BorderLayout.SOUTH);

         JButton demoButton = new JButton("Demo button");
         buttonPanel.add(demoButton);
         buttonPanel.add(new JLabel(" "));
         statusLabel_ = new JLabel("Test label");

         textPanel.add(new JLabel(" "));
         textPanel.add(statusLabel_, BorderLayout.CENTER);
      }
   }
}
