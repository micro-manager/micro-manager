package org.micromanager.imagedisplay.dev;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import ij.ImagePlus;

import java.util.ArrayList;

import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import org.micromanager.api.data.Datastore;
import org.micromanager.api.data.Image;
import org.micromanager.api.display.OverlayPanel;

import org.micromanager.imagedisplay.CanvasDrawEvent;

/**
 * This class contains panels used to draw overlays on the image canvas.
 */
class OverlaysPanel extends JPanel {
   private ArrayList<OverlayPanel> panels_;
   private Datastore store_;
   private MMVirtualStack stack_;
   private ImagePlus plus_;
   private EventBus displayBus_;
   
   public OverlaysPanel(Datastore store, MMVirtualStack stack, ImagePlus plus,
         EventBus displayBus) {
      setLayout(new MigLayout("flowy"));
      store_ = store;
      stack_ = stack;
      plus_ = plus;
      displayBus_ = displayBus;

      displayBus_.register(this);

      panels_ = new ArrayList<OverlayPanel>();
      ScaleBarOverlayPanel scalebar = new ScaleBarOverlayPanel(store_);
      scalebar.setBus(displayBus_);
      panels_.add(scalebar);
      TimestampOverlayPanel timestamp = new TimestampOverlayPanel(store_);
      timestamp.setBus(displayBus_);
      panels_.add(timestamp);

      redoLayout();
   }

   private void redoLayout() {
      removeAll();
      for (OverlayPanel panel : panels_) {
         add(panel);
      }
      displayBus_.post(new LayoutChangedEvent());
   }

   @Subscribe
   public void onCanvasDraw(CanvasDrawEvent event) {
      Image image = store_.getImage(stack_.getCurrentImageCoords());
      for (OverlayPanel panel : panels_) {
         panel.drawOverlay(event.getGraphics(), store_, image,
               event.getCanvas());
      }
   }

   public void cleanup() {
      displayBus_.unregister(this);
   }
}
