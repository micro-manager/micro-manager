/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.internal.magellanacq;

import java.awt.Color;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Set;
import org.json.JSONObject;
import org.micromanager.multiresviewer.NDViewer;
import org.micromanager.ndviewer.api.AcquisitionPlugin;
import org.micromanager.ndviewer.api.DataSource;
import org.micromanager.ndviewer.api.ViewerInterface;

/**
 * TODO add methods to get the info of viewcoords so that internal datastructure
 * not exposed
 *
 * @author henrypinkard
 */
public class MagellanViewer implements ViewerInterface {

   private ViewerInterface viewer_;
   private MagellanImageCache cache_;

   public MagellanViewer(MagellanImageCache cache, AcquisitionPlugin acq, JSONObject summmaryMD) {
      viewer_ = new NDViewer(cache, acq, summmaryMD, MagellanMD.getPixelSizeUm(summmaryMD));
      cache_ = cache;
   }

   int getTileHeight() {
      return cache_.getTileHeight();
   }

   int getTileWidth() {
      return cache_.getTileWidth();
   }

   boolean anythingAcquired() {
      return cache_.anythingAcquired();
   }

   /**
    * TODO: might want to move this out to simplyfy API used to keep explored
    * area visible in explire acquisitons
    */
   private void moveViewToVisibleArea() {

      //check for valid tiles (at lowest res) at this slice        
      Set<Point> tiles = cache_.getTileIndicesWithDataAt(viewer_.getAxisPosition("z"));
      if (tiles.size() == 0) {
         return;
      }
//      center of one tile must be within corners of current view 
      double minDistance = Integer.MAX_VALUE;
      //do all calculations at full resolution
      long currentX = (long) viewer_.getViewOffset().x;
      long currentY = (long) viewer_.getViewOffset().y;

      for (Point p : tiles) {
         //calclcate limits on margin of tile that must remain in view
         long tileX1 = (long) ((0.1 + p.x) * cache_.getTileWidth());
         long tileX2 = (long) ((0.9 + p.x) * cache_.getTileWidth());
         long tileY1 = (long) ((0.1 + p.y) * cache_.getTileHeight());
         long tileY2 = (long) ((0.9 + p.y) * cache_.getTileHeight());
//         long visibleWidth = (long) (0.8 * imageCache_.getTileWidth());
//         long visibleHeight = (long) (0.8 * imageCache_.getTileHeight());
         //get bounds of viewing area
         long fovX1 = (long) viewer_.getViewOffset().x;
         long fovY1 = (long) viewer_.getViewOffset().y;
         long fovX2 = (long) (fovX1 + viewer_.getSizeOfVisibleImage().x);
         long fovY2 = (long) (fovY1 + viewer_.getSizeOfVisibleImage().y);

         //check if tile and fov intersect
         boolean xInView = fovX1 < tileX2 && fovX2 > tileX1;
         boolean yInView = fovY1 < tileY2 && fovY2 > tileY1;
         boolean intersection = xInView && yInView;

         if (intersection) {
            return; //at least one tile is in view, don't need to do anything
         }
         //tile to fov corner to corner distances
         double tl = ((tileX1 - fovX2) * (tileX1 - fovX2) + (tileY1 - fovY2) * (tileY1 - fovY2)); //top left tile, botom right fov
         double tr = ((tileX2 - fovX1) * (tileX2 - fovX1) + (tileY1 - fovY2) * (tileY1 - fovY2)); // top right tile, bottom left fov
         double bl = ((tileX1 - fovX2) * (tileX1 - fovX2) + (tileY2 - fovY1) * (tileY2 - fovY1)); // bottom left tile, top right fov
         double br = ((tileX1 - fovX1) * (tileX1 - fovX1) + (tileY2 - fovY1) * (tileY2 - fovY1)); //bottom right tile, top left fov

         double closestCornerDistance = Math.min(Math.min(tl, tr), Math.min(bl, br));
         if (closestCornerDistance < minDistance) {
            minDistance = closestCornerDistance;
            if (tl <= tr && tl <= bl && tl <= br) { //top left tile, botom right fov
               currentX = (long) (xInView ? currentX : tileX1 - viewer_.getSizeOfVisibleImage().x);
               currentY = (long) (yInView ? currentY : tileY1 - viewer_.getSizeOfVisibleImage().y);
            } else if (tr <= tl && tr <= bl && tr <= br) { // top right tile, bottom left fov
               currentX = xInView ? currentX : tileX2;
               currentY = (long) (yInView ? currentY : tileY1 - viewer_.getSizeOfVisibleImage().y);
            } else if (bl <= tl && bl <= tr && bl <= br) { // bottom left tile, top right fov
               currentX = (long) (xInView ? currentX : tileX1 - viewer_.getSizeOfVisibleImage().x);
               currentY = yInView ? currentY : tileY2;
            } else { //bottom right tile, top left fov
               currentX = xInView ? currentX : tileX2;
               currentY = yInView ? currentY : tileY2;
            }
         }
      }
      viewer_.setViewOffset(currentX, currentY);
   }

   Point getTileIndicesFromDisplayedPixel(int x, int y) {
      double scale = viewer_.getDisplayToFullScaleFactor();
      int fullResX = (int) ((x / scale) + viewer_.getViewOffset().x);
      int fullResY = (int) ((y / scale) + viewer_.getViewOffset().y);
      int xTileIndex = fullResX / cache_.getTileWidth() - (fullResX >= 0 ? 0 : 1);
      int yTileIndex = fullResY / cache_.getTileHeight() - (fullResY >= 0 ? 0 : 1);
      return new Point(xTileIndex, yTileIndex);
   }

   /**
    * return the pixel location in coordinates at appropriate res level of the
    * top left pixel for the given row/column
    *
    * @param row
    * @param col
    * @return
    */
   public Point getDisplayedPixel(long row, long col) {
      double scale = viewer_.getDisplayToFullScaleFactor();
      int x = (int) ((col * cache_.getTileWidth() - viewer_.getViewOffset().x) * scale);
      int y = (int) ((row * cache_.getTileWidth() - viewer_.getViewOffset().y) * scale);
      return new Point(x, y);
   }

   //OVerride zoom and pan to restrain viewer to explored region in explore acqs
   public void pan(int dx, int dy) {
      viewer_.pan(dx, dy);
      if (viewer_.getBounds() == null) {
         moveViewToVisibleArea();
         viewer_.update();
      }
   }

   public void zoom(double factor, Point mouseLocation) {
      viewer_.zoom(factor, mouseLocation);
      if (viewer_.getBounds() == null) {
         moveViewToVisibleArea();
         viewer_.update();
      }
   }

   @Override
   public int getAxisPosition(String z) {
      return viewer_.getAxisPosition(z);
   }

   @Override
   public Point2D.Double getViewOffset() {
      return viewer_.getViewOffset();
   }

   @Override
   public Point2D.Double getSizeOfVisibleImage() {
      return viewer_.getSizeOfVisibleImage();
   }

   @Override
   public double getDisplayToFullScaleFactor() {
      return viewer_.getDisplayToFullScaleFactor();
   }

   @Override
   public void update() {
      viewer_.update();
   }

   @Override
   public void setViewOffset(double newX, double newY) {
      viewer_.setViewOffset(newX, newY);
   }

   @Override
   public int[] getBounds() {
      return viewer_.getBounds();
   }

   @Override
   public void setWindowTitle(String string) {
      viewer_.setWindowTitle(string);
   }

   @Override
   public JSONObject getDisplaySettingsJSON() {
      return viewer_.getDisplaySettingsJSON();
   }

   @Override
   public void close() {
      viewer_.close();
   }

   @Override
   public void onDataSourceClosing() {
      viewer_.onDataSourceClosing();
   }

   @Override
   public void newImageArrived(HashMap<String, Integer> axes, String channelName, int bitDepth) {
      viewer_.newImageArrived(axes, channelName, bitDepth);
   }

   @Override
   public void setAxisPosition(String axis, int pos) {
      viewer_.setAxisPosition(axis, pos);
   }

   @Override
   public void setChannel(String channelName) {
      viewer_.setChannel(channelName);
   }

   @Override
   public void setChannelColor(String chName, Color c) {
      viewer_.setChannelColor(chName, c);
   }
}
