/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.ndviewer.api;

import java.awt.Color;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.HashMap;
import org.json.JSONObject;

/**
 *
 * @author henrypinkard
 */
public interface ViewerInterface {

   /**
    * Call this whenever a new image arrives to optionally show it,
    * and also so that the size of the scrollbars expands
    * 
    * @param axes map of axis labels to integer positions (does NOT include channel.
    * this is inferred automatically)
    * @param channelName Name of the channel of the image
    * @param bitDepth bits per pixel of the image
    */
   public void newImageArrived(HashMap<String, Integer> axes, String channelName, int bitDepth);

   public void setAxisPosition(String axis, int pos);
   
   public int getAxisPosition(String axis);
   
   /**
    * Sets the visible channel
    * @param channelName 
    */
   public void setChannel(String channelName);

   /**
    * 
    * @return 4 element vector with x_min y_min x_max y_max
    */
   public int[] getBounds();

   public void setWindowTitle(String string);

   public JSONObject getDisplaySettingsJSON();

   public void close();

   public void onDataSourceClosing();

   public void pan(int dx, int dy);

   public void zoom(double factor, Point mouseLocation);

   /**
    * Redraw current image and any overlay
    */
   public void update();

   public Point2D.Double getViewOffset();

   /**
    * Pixel size of the region currently being displayed in full resolution pixels
    * @return 
    */
   public Point2D.Double getSizeOfVisibleImage();

   public double getDisplayToFullScaleFactor();

   public void setViewOffset(double newX, double newY);

   public void setChannelColor(String chName, Color c);

}
