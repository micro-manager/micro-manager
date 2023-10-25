package org.micromanager.magellan.internal.explore.gui;

import java.awt.Point;

public interface ExploreMouseListenerAPI {

   Point getExploreStartTile();

   Point getExploreEndTile();

   Point getMouseDragStartPointLeft();

   Point getCurrentMouseLocation();

}
