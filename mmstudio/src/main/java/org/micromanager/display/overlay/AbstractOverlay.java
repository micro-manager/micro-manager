/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.overlay;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.List;
import javax.swing.JComponent;
import org.apache.commons.lang3.event.EventListenerSupport;
import org.micromanager.PropertyMap;
import org.micromanager.data.Image;
import org.micromanager.data.internal.DefaultPropertyMap;
import org.micromanager.display.DisplaySettings;

/**
 *
 * @author mark
 */
public abstract class AbstractOverlay implements Overlay {
   private final EventListenerSupport<OverlayListener> listeners_ =
         EventListenerSupport.create(OverlayListener.class);

   /**
    * {@inheritDoc}
    * <p>
    * This default implementation draws nothing.
    */
   @Override
   public void paintOverlay(Graphics2D graphicsContext, Rectangle screenRect,
         DisplaySettings displaySettings,
         List<Image> images, Image primaryImage,
         Rectangle2D.Float imageViewPort)
   {
      // Draw nothing
   }

   /**
    * {@inheritDoc}
    * <p>
    * This default implementation returns null.
    */
   @Override
   public JComponent getConfigurationComponent() {
      return null;
   }

   /**
    * {@inheritDoc}
    * <p>
    * This default implementation returns an empty property map.
    */
   @Override
   public PropertyMap getConfiguration() {
      return new DefaultPropertyMap.Builder().build();
   }

   /**
    * {@inheritDoc}
    * <p>
    * This default implementation does nothing.
    */
   @Override
   public void setConfiguration(PropertyMap config) {
      // Do nothing
   }

   /**
    * {@inheritDoc}
    * <p>
    * This implementation takes care of managing listeners.
    * @see #fireOverlayNeedsRepaint
    */
   @Override
   public final void addOverlayListener(OverlayListener listener) {
      listeners_.addListener(listener, true);
   }

   /**
    * {@inheritDoc}
    * <p>
    * This implementation takes care of managing listeners.
    * @see #fireOverlayNeedsRepaint
    */
   @Override
   public final void removeOverlayListener(OverlayListener listener) {
      listeners_.removeListener(listener);
   }

   /**
    * Call this method to notify the system that the overlay has changed.
    * <p>
    * This only needs to be called when the overlay needs a repaint due to its
    * configuration changing. The case when the displayed image has changed is
    * automatically handled by the system.
    *
    * @see OverlayListener#overlayNeedsRepaint
    */
   protected final void fireOverlayNeedsRepaint() {
      listeners_.fire().overlayNeedsRepaint(this);
   }
}