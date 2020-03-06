/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.multiresviewer.api;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.Set;
import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.multiresviewer.DataViewCoords;
import org.micromanager.multiresviewer.MagellanDisplayController;

/**
 * Interface for a multiresolution data source
 * 
 * @author henrypinkard
 */
public interface DataSource {

   
   //TODO: remove?
   public int getTileWidth();

   public int getTileHeight();
   
      public Set<Point> getTileIndicesWithDataAt(int axisPosition);

      public String getUniqueAcqName();

   
   public Point2D.Double stageCoordinateFromPixelCoordinate(long l, long l0);

         public Point pixelCoordsFromStageCoords(double x, double y);

////////////
      
      
   public boolean isXYBounded();

   public long[] getImageBounds();
   //Same as above?

   public Point2D.Double getFullResolutionSize();

   public TaggedImage getImageForDisplay(Integer c, DataViewCoords viewCoords);

   public int getMaxResolutionIndex();

   public String getDiskLocation();

   public boolean anythingAcquired();

   public double getPixelSize_um();

   public void close();

   public JSONObject getSummaryMD();
   
}
