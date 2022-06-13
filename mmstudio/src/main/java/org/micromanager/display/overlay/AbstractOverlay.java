package org.micromanager.display.overlay;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.List;
import javax.swing.JComponent;
import org.apache.commons.lang3.event.EventListenerSupport;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.data.Image;
import org.micromanager.display.DisplaySettings;

/**
 * Abstract implementation of {@link Overlay}.
 *
 * <p>Custom overlays should extend this class.
 *
 * @author Mark A. Tsuchida
 */
public abstract class AbstractOverlay implements Overlay {
   private boolean visible_ = true;
   private final EventListenerSupport<OverlayListener> listeners_ =
         new EventListenerSupport(OverlayListener.class,
               this.getClass().getClassLoader());

   /**
    * {@inheritDoc}
    *
    * <p>This default implementation draws nothing. Override to draw the overlay
    * graphics.
    */
   @Override
   public void paintOverlay(Graphics2D graphicsContext, Rectangle screenRect,
                            DisplaySettings displaySettings,
                            List<Image> images, Image primaryImage,
                            Rectangle2D.Float imageViewPort) {
      // Draw nothing
   }

   /**
    * {@inheritDoc}
    *
    * <p>This default implementation returns null. Override to provide a user
    * interface.
    */
   @Override
   public JComponent getConfigurationComponent() {
      return null;
   }

   /**
    * {@inheritDoc}
    *
    * <p>This default implementation returns an empty property map. Override to
    * implement custom settings.
    */
   @Override
   public PropertyMap getConfiguration() {
      return PropertyMaps.emptyPropertyMap();
   }

   /**
    * {@inheritDoc}
    *
    * <p>This default implementation does nothing. Override to implement custom
    * settings.
    */
   @Override
   public void setConfiguration(PropertyMap config) {
      // Do nothing
   }

   /**
    * {@inheritDoc}
    *
    * <p>This implementation takes care of visibility management.
    */
   @Override
   public final boolean isVisible() {
      return visible_;
   }

   /**
    * {@inheritDoc}
    *
    * <p>This implementation takes care of visibility management.
    */
   @Override
   public final void setVisible(boolean visible) {
      if (visible == visible_) {
         return;
      }
      visible_ = visible;
      listeners_.fire().overlayVisibleChanged(this);
   }

   /**
    * {@inheritDoc}
    *
    * <p>This implementation takes care of managing listeners.
    *
    * @see #fireOverlayConfigurationChanged
    */
   @Override
   public final void addOverlayListener(OverlayListener listener) {
      listeners_.addListener(listener, true);
   }

   /**
    * {@inheritDoc}
    *
    * <p>This implementation takes care of managing listeners.
    *
    * @see #fireOverlayConfigurationChanged
    */
   @Override
   public final void removeOverlayListener(OverlayListener listener) {
      listeners_.removeListener(listener);
   }

   /**
    * Call this method to notify the system that the overlay title has changed.
    *
    * <p>If the overlay has a fixed title, this method need not ever be called.
    *
    * @see OverlayListener#overlayTitleChanged
    */
   protected final void fireOverlayTitleChanged() {
      listeners_.fire().overlayTitleChanged(this);
   }

   /**
    * Call this method to notify the system that the overlay has changed.
    *
    * <p>This only needs to be called when the overlay needs a repaint due to its
    * configuration changing. The case when the displayed image has changed is
    * automatically handled by the system.
    *
    * @see OverlayListener#overlayConfigurationChanged
    */
   protected final void fireOverlayConfigurationChanged() {
      listeners_.fire().overlayConfigurationChanged(this);
   }
}