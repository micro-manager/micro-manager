// Copyright (C) 2015-2017 Open Imaging, Inc.
//           (C) 2015 Regents of the University of California
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.internal.utils;

import com.google.common.base.Preconditions;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.WeakHashMap;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.internal.MMStudio;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * Save and restore window locations and sizes; cascade windows. *
 * The two aspects are coordinated by methods of this class.
 *
 * @author Mark A. Tsuchida
 */
public final class WindowPositioning {
   private WindowPositioning() {
   } // Non-instantiable

   private static final int CASCADING_OFFSET_HORIZONTAL = 20;
   private static final int CASCADING_OFFSET_VERTICAL = 24;

   // TODO Avoid static - before putting this in the API, we should tie it to
   // the application context.
   private static final Map<Class<?>, WindowCascade> cascades_ = new HashMap<>();
   private static final Map<Window, WindowCascade> windowCascades_ = new WeakHashMap<>();

   /**
    * Set the window bounds to the last saved ones, and arrange to save the
    * bounds for future recall by this method.
    *
    * <p>To create a JFrame or JDialog that opens at it last known position, all
    * you need to do is call this method once after creating (and before
    * showing) the window. No further action is necessary (no class to extend,
    * no method to call when the window closes).
    *
    * <p>This method is typically called by the controller object that creates the
    * window. However, where the old-fashioned (and discouraged) practice of
    * overriding JFrame or JDialog to add controller logic is followed, the
    * window itself may call this method.
    *
    * <p>This method is intended for a windows that are usually singletons. For
    * windows that will have many instances, use (TODO) instead.
    *
    * <p>The window bounds are saved per positioning class and per positioning key
    * (null being the default key). The positioning class is any class that
    * uniquely identifies the window type (typically the controller object's
    * class). The optional positioning key may be used when several distinct
    * instances of the same window need to be remembered (an example of this
    * in MMStudio is the Preview window versus other, normal image windows).
    *
    * <p>To provide a default bounds for the window (for use when displaying for
    * the first time in the current user profile), simply set the window bounds
    * <i>before</i> calling this method.
    *
    * <p>If you want to also cascade the window, you must call this method
    * <i>before</i> calling {@code cascade}.
    *
    * @param window           the window whose position should be restored and remembered
    * @param positioningClass a class that identifies the type of the window
    *                         (must not be null)
    * @param positioningKey   a string that further identifies the type of the
    *                         window (may be null)
    */
   @MustCallOnEDT
   public static void setUpBoundsMemory(Window window,
                                        Class<?> positioningClass, String positioningKey) {
      GeometrySaver.createAndRestoreBounds(
            window,
            positioningClass,
            positioningKey);
   }

   /**
    * Set the window location to the last saved one, and arrange to save the
    * location for future recall by this method.
    *
    * <p>This is exactly like {@code restoreAndMemorizeBounds}, except that the
    * window size is not saved or restored. Recommended for use on non-
    * resizable windows only.
    *
    * <p>See {@code restoreAndMemorizeBounds} for further details.
    *
    * @param window           window for which to set up location memory
    * @param positioningClass Class to which to tie the location memory
    * @param positioningKey   Some key, unsure if this can be empty
    */
   @MustCallOnEDT
   public static void setUpLocationMemory(Window window,
                                          Class<?> positioningClass, String positioningKey) {
      GeometrySaver.createAndRestoreLocation(window,
            positioningClass, positioningKey);
   }

   /**
    * Set the location of a window so that it cascades within a series of
    * windows.
    *
    * <p>This method should be called before showing the window. If no visible
    * windows exist in the series specified by {@code cascadingClass}, the
    * window location is not altered except to fit the window in the screen.
    *
    * <p>If you want to also set up memorization for the window position, you
    * must call either {@code setUpBoundsMemory} or {@code setUpLocationMemory}
    * <i>before</i> calling this method.
    *
    * <p>If the window is cascaded, the remembered position is that of the oldest
    * window in the series. However, since window positions are saved whenever
    * windows are hidden, the program must close a group of cascaded windows in
    * new-to-old order in order to get correct position memory behavior.
    * Front-to-back order might be a good enough approximation to this.
    *
    * @param window         the window to position
    * @param cascadingClass a class that identifies the group of windows to be
    *                       cascaded with respect to each other
    */
   @MustCallOnEDT
   public static void cascade(Window window, Class<?> cascadingClass) {
      WindowCascade cascade = cascades_.get(cascadingClass);
      if (cascade == null) {
         cascade = new WindowCascade();
         cascades_.put(cascadingClass, cascade);
      }
      cascade.addWindow(window);
      windowCascades_.put(window, cascade);
   }

   /**
    * Move and resize a window so that it is on screen and (to the extent
    * possible) fits in the screen.
    *
    * <p>This keeps the window in the same screen as much as possible. To position
    * a window in a particular screen, move it into that screen before calling
    * this method.
    *
    * <p>There is no need to call this method if position memory is set up or
    * cascading is enabled for the window (those methods internally call this
    * method).
    *
    * @param window the window to fit in the screen
    */
   @MustCallOnEDT
   public static void fitWindowInScreen(Window window) {
      // Move to the nearest screen if the top-left is off all screens.
      Rectangle screenBounds = null;

      Point location = window.getLocation();
      Point nearestOnScreenPoint = location;
      double minDistance = Double.MAX_VALUE;
      for (GraphicsDevice gDevice : GraphicsEnvironment
            .getLocalGraphicsEnvironment().getScreenDevices()) {
         GraphicsConfiguration gConfig = gDevice.getDefaultConfiguration();
         Rectangle bounds = Geometry.insettedRectangle(gConfig.getBounds(),
               Toolkit.getDefaultToolkit().getScreenInsets(gConfig));
         Point nearestPoint =
               Geometry.nearestPointInRectangle(location, bounds);
         double distance = nearestPoint.distance(location);
         nearestOnScreenPoint = nearestPoint;
         if (distance == 0.0) {
            screenBounds = bounds;
            break;
         }
         if (distance < minDistance) {
            screenBounds = bounds;
         }
      }
      window.setLocation(nearestOnScreenPoint);

      if (screenBounds == null) {
         return; // No screen? Bail.
      }

      // Determine whether we need to honor cascading
      WindowCascade cascade = windowCascades_.get(window);
      boolean cascading = (cascade != null && cascade.getOldestVisibleWindow() != window);
      if (cascading) {
         Point resetCursor = cascade.getResetCursor();
         if (screenBounds.contains(resetCursor)) {
            // The window is on a different screen from the one we are
            // cascading on. Ignore cascading.
            cascading = false;
         }
      }

      if (!cascading) {
         // Move up and left no more than necessary to try to fit in screen.
         Rectangle bounds = window.getBounds();
         bounds.translate(
               -Math.min(bounds.x - screenBounds.x,
                     Math.max(0,
                           (bounds.x + bounds.width) - (screenBounds.x + screenBounds.width))),
               -Math.min(bounds.y - screenBounds.y,
                     Math.max(0,
                           (bounds.y + bounds.height) - (screenBounds.y + screenBounds.height))));
         window.setLocation(bounds.getLocation());
      } else {
         // When we are cascading, we "reset" the cascade by moving to the top
         // of the screen, preserving the horizontal position. If we are
         // getting significantly beyond the right edge of the screen, we also
         // jump to the left edge, and move down one vertical offset.
         Point resetCursor = cascade.getResetCursor();
         location = window.getLocation();
         if (location.y + window.getHeight() > screenBounds.y + screenBounds.height) {
            location.y = resetCursor.y;
         }
         if (location.x + window.getWidth() - (screenBounds.x + screenBounds.width)
               > screenBounds.width / 2) {
            location.x = screenBounds.x;
            location.y = resetCursor.y + CASCADING_OFFSET_VERTICAL;
         }
         if (!screenBounds.contains(location)) {
            // Falling off the screen; do an all-reset
            location = screenBounds.getLocation();
         }
         if (!location.equals(window.getLocation())) {
            cascade.setResetCursor(location);
            window.setLocation(location);
         }
      }

      // Finally, if the window still extends beyond the right or bottom of the
      // screen, we resize it to the extent needed to fit and permitted by the
      // window's minimum size.
      Rectangle bounds = window.getBounds();
      Dimension minSize = window.getMinimumSize();
      window.setSize(
            Math.max(minSize.width,
                  Math.min(bounds.width,
                        screenBounds.x + screenBounds.width - bounds.x)),
            Math.max(minSize.height,
                  Math.min(bounds.height,
                        screenBounds.y + screenBounds.height - bounds.y)));
   }

   private static class GeometrySaver implements ComponentListener {
      private enum Mode {
         MEMORIZE_BOUNDS,
         MEMORIZE_LOCATION,
      }

      // The profile settings for this class stores nested property maps for
      // positioning class and then positioning key. Each of those pmaps can
      // contain these keys:
      private enum ProfileKey {
         WINDOW_LOCATION,
         WINDOW_BOUNDS,
      }

      private final Mode mode_;
      private final Class<?> positioningClass_;
      private final String positioningKey_;
      private final Window window_;

      static GeometrySaver createAndRestoreBounds(Window window,
                                                  Class<?> positioningClass,
                                                  String positioningKey) {
         GeometrySaver saver =
               new GeometrySaver(window, positioningClass, positioningKey,
                     Mode.MEMORIZE_BOUNDS);
         saver.restoreGeometry();
         saver.attachToWindow();
         return saver;
      }

      static GeometrySaver createAndRestoreLocation(Window window,
                                                    Class<?> positioningClass,
                                                    String positioningKey) {
         GeometrySaver saver =
               new GeometrySaver(window, positioningClass, positioningKey,
                     Mode.MEMORIZE_LOCATION);
         saver.restoreGeometry();
         saver.attachToWindow();
         return saver;
      }

      private GeometrySaver(Window window, Class<?> positioningClass,
                            String positioningKey, Mode mode) {
         Preconditions.checkNotNull(window);
         window_ = window;
         positioningClass_ = positioningClass;
         positioningKey_ = positioningKey == null ? "" : positioningKey;
         mode_ = mode;
      }

      private void attachToWindow() {
         window_.addComponentListener(this);
      }

      private void saveGeometry() {
         // If cascading was enabled for this window, we want to record the
         // position of the oldest extant window in the cascade, because that
         // is where the first new window of the same kind should be displayed
         // next time.
         Window window = window_;
         WindowCascade cascade = windowCascades_.get(window);
         if (cascade != null) {
            Window oldest = cascade.getOldestVisibleWindow();
            if (oldest != null) {
               window = oldest;
            }
         }

         Rectangle bounds = window.getBounds();
         MutablePropertyMapView settings = MMStudio.getInstance()
               .profile().getSettings(getClass());
         PropertyMap classPmap = settings.getPropertyMap(
               positioningClass_.getCanonicalName(),
               PropertyMaps.emptyPropertyMap());
         PropertyMap keyPmap = classPmap.getPropertyMap(positioningKey_,
               PropertyMaps.emptyPropertyMap());

         PropertyMap.Builder builder = keyPmap.copyBuilder();
         switch (mode_) {
            case MEMORIZE_BOUNDS:
               builder.putRectangle(ProfileKey.WINDOW_BOUNDS.name(), bounds);
               break;

            case MEMORIZE_LOCATION:
               builder.putPoint(ProfileKey.WINDOW_LOCATION.name(),
                     bounds.getLocation());
               break;

            default:
               throw new AssertionError(mode_);
         }
         keyPmap = builder.build();
         classPmap = classPmap.copyBuilder().putPropertyMap(positioningKey_, keyPmap).build();
         settings.putPropertyMap(positioningClass_.getCanonicalName(),
               classPmap);
      }

      private void restoreGeometry() {
         PropertyMap pmap;
         try {
            pmap = MMStudio.getInstance().profile().getSettings(getClass())
               .getPropertyMap(positioningClass_.getCanonicalName(),
                     PropertyMaps.emptyPropertyMap()).getPropertyMap(
                     positioningKey_, PropertyMaps.emptyPropertyMap());
         } catch (Exception e) {
            // Better to continue without restoring geometry than to throw.
            return;
         }

         switch (mode_) {
            case MEMORIZE_BOUNDS:
               window_.setBounds(pmap.getRectangle(
                     ProfileKey.WINDOW_BOUNDS.name(), window_.getBounds()));
               break;

            case MEMORIZE_LOCATION:
               window_.setLocation(pmap.getPoint(
                     ProfileKey.WINDOW_LOCATION.name(),
                     window_.getLocation()));
               break;

            default:
               throw new AssertionError(mode_);
         }
         fitWindowInScreen(window_);
      }

      @Override
      public void componentResized(ComponentEvent e) {
         saveGeometry();
      }

      @Override
      public void componentMoved(ComponentEvent e) {
         saveGeometry();
      }

      @Override
      public void componentShown(ComponentEvent e) {
         saveGeometry();
      }

      @Override
      public void componentHidden(ComponentEvent e) {
         saveGeometry();
      }
   }

   private static class WindowCascade {
      private final List<WeakReference<Window>> windows_ = new LinkedList<>();
      private Point resetCursor_;

      void addWindow(Window window) {
         Window previous = getNewestVisibleWindow();
         windows_.add(new WeakReference<>(window));
         if (previous != null) {
            Point location = previous.getLocation();
            location.translate(CASCADING_OFFSET_HORIZONTAL,
                  CASCADING_OFFSET_VERTICAL);
            window.setLocation(location);
         }
         fitWindowInScreen(window);
      }

      Window getNewestVisibleWindow() {
         ListIterator<WeakReference<Window>> iterator =
               windows_.listIterator(windows_.size());
         while (iterator.hasPrevious()) {
            Window window = iterator.previous().get();
            if (window == null) {
               iterator.remove();
               continue;
            }
            if (window.isVisible()) {
               return window;
            }
         }
         return null;
      }

      Window getOldestVisibleWindow() {
         ListIterator<WeakReference<Window>> iterator =
               windows_.listIterator();
         while (iterator.hasNext()) {
            Window window = iterator.next().get();
            if (window == null) {
               iterator.remove();
               continue;
            }
            if (window.isVisible()) {
               return window;
            }
         }
         return null;
      }

      void setResetCursor(Point cursor) {
         resetCursor_ = new Point(cursor);
      }

      Point getResetCursor() {
         return new Point(resetCursor_);
      }
   }
}
