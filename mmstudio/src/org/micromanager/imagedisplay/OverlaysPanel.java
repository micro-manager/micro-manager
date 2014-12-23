package org.micromanager.imagedisplay;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import ij.ImagePlus;

import java.util.ArrayList;

import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import org.micromanager.api.data.Image;
import org.micromanager.api.display.DisplayWindow;
import org.micromanager.api.display.OverlayPanel;
import org.micromanager.imagedisplay.events.CanvasDrawEvent;
import org.micromanager.imagedisplay.events.LayoutChangedEvent;

/**
 * This class contains panels used to draw overlays on the image canvas.
 */
class OverlaysPanel extends JPanel {
   private ArrayList<OverlayPanel> panels_;
   private DisplayWindow display_;
   private MMVirtualStack stack_;
   private ImagePlus plus_;
   private EventBus displayBus_;
   
   public OverlaysPanel(DisplayWindow display, MMVirtualStack stack, ImagePlus plus,
         EventBus displayBus) {
      setLayout(new MigLayout("flowy"));
      display_ = display;
      stack_ = stack;
      plus_ = plus;
      displayBus_ = displayBus;

      displayBus_.register(this);

      panels_ = new ArrayList<OverlayPanel>();
      ScaleBarOverlayPanel scalebar = new ScaleBarOverlayPanel(display_);
      scalebar.setBus(displayBus_);
      panels_.add(scalebar);
      TimestampOverlayPanel timestamp = new TimestampOverlayPanel();
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
      Image image = display_.getDatastore().getImage(stack_.getCurrentImageCoords());
      for (OverlayPanel panel : panels_) {
         panel.drawOverlay(event.getGraphics(), display_, image,
               event.getCanvas());
      }
   }

   public void cleanup() {
      displayBus_.unregister(this);
   }
}
