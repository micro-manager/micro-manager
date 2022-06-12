package org.micromanager.display.internal;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.micromanager.EventPublisher;
import org.micromanager.display.DataViewer;
import org.micromanager.display.internal.event.DataViewerAddedEvent;
import org.micromanager.display.internal.event.DataViewerDidBecomeActiveEvent;
import org.micromanager.display.internal.event.DataViewerDidBecomeInactiveEvent;
import org.micromanager.display.internal.event.DataViewerDidBecomeInvisibleEvent;
import org.micromanager.display.internal.event.DataViewerDidBecomeVisibleEvent;
import org.micromanager.display.internal.event.DataViewerWillCloseEvent;
import org.micromanager.internal.utils.EventBusExceptionLogger;
import org.micromanager.internal.utils.MustCallOnEDT;

/**
 * The collection of data viewers.
 * <p>
 * Manages all data viewers in the application, and keeps track of the
 * front-most viewer. Also publishes all viewer events.
 *
 * <p>
 * This class handles generic {@code DataViewer}s and does not perform tasks
 * that are specific to {@code DisplayWindow}s.
 *
 * @author Mark A. Tsuchida
 */
public class DataViewerCollection implements EventPublisher {
   // Viewers known to this manager
   // Access: only on EDT
   private final Set<DataViewer> viewers_ = new HashSet<>();

   // Viewers in most-recently-activated order. The first element is the
   // currently active (or at least, front-most) viewer.
   // Invariant: elements are unique
   // Invariant: elements are in viewers_
   // Access: only onEDT
   private final Deque<DataViewer> showingViewers_ = new ArrayDeque<>();

   private final EventBus eventBus_ = new EventBus(EventBusExceptionLogger.getInstance());

   public static DataViewerCollection create() {
      return new DataViewerCollection();
   }

   private DataViewerCollection() {
   }


   @MustCallOnEDT
   public boolean hasDataViewer(DataViewer viewer) {
      return viewers_.contains(viewer);
   }

   // Caveat: new viewer must be added before showing (and thus activating)
   @MustCallOnEDT
   public void addDataViewer(DataViewer viewer) {
      if (viewers_.contains(viewer)) {
         throw new IllegalArgumentException("DataViewer is already in collection");
      }
      viewers_.add(viewer);
      viewer.registerForEvents(this);
      eventBus_.post(DataViewerAddedEvent.create(viewer));
   }

   @MustCallOnEDT
   public void removeDataViewer(DataViewer viewer) {
      if (!viewers_.contains(viewer)) {
         throw new IllegalArgumentException("DataViewer is not in collection");
      }
      viewer.unregisterForEvents(this);
      viewers_.remove(viewer);
      if (viewer == getActiveDataViewer()) {
         eventBus_.post(DataViewerDidBecomeInactiveEvent.create(viewer));
      }
      showingViewers_.remove(viewer);
      // An event needs to be generated for the newly front-most Dataviewer
      // even though it may already have been "active", it is not in front
      // and event consumers (like the Inspector) will need to know about this.
      DataViewer activeViewer = getActiveDataViewer();
      if (activeViewer != null) {
         eventBus_.post(DataViewerDidBecomeActiveEvent.create(activeViewer));
      }

   }

   @MustCallOnEDT
   public List<DataViewer> getAllDataViewers() {
      return new ArrayList<>(viewers_);
   }

   @MustCallOnEDT
   public DataViewer getActiveDataViewer() {
      for (DataViewer viewer : showingViewers_) {
         if (viewer.isVisible()) {
            return viewer;
         }
      }
      return null;
   }

   @Subscribe
   public void onEvent(DataViewerDidBecomeVisibleEvent e) {
      eventBus_.post(e);
   }

   @Subscribe
   public void onEvent(DataViewerDidBecomeInvisibleEvent e) {
      eventBus_.post(e);
   }

   @Subscribe
   public void onEvent(DataViewerDidBecomeActiveEvent e) {
      // Since data viewers are not able to know when they become inactive
      // (defined as another viewer becoming active, or the viewer closing),
      // we generate that event here.
      DataViewer previous = getActiveDataViewer();
      if (previous != null) {
         if (previous == e.getDataViewer()) { // No change
            return;
         }
         eventBus_.post(DataViewerDidBecomeInactiveEvent.create(previous));
      }

      showingViewers_.remove(e.getDataViewer());
      showingViewers_.addFirst(e.getDataViewer());

      eventBus_.post(e);
   }

   @Subscribe
   public void onEvent(DataViewerWillCloseEvent e) {
      eventBus_.post(e);
      removeDataViewer(e.getDataViewer());
   }

   @Override
   public void registerForEvents(Object recipient) {
      eventBus_.register(recipient);
   }

   @Override
   public void unregisterForEvents(Object recipient) {
      eventBus_.unregister(recipient);
   }

}