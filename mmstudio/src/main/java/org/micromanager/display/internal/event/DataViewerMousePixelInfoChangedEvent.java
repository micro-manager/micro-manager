/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.display.internal.event;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;
import org.micromanager.data.Coords;
import org.micromanager.data.Image;
import org.micromanager.data.internal.DefaultCoords;

/**
 * @author mark
 */
public class DataViewerMousePixelInfoChangedEvent {
   private final int x_;
   private final int y_;
   private final String[] indexingAxes_;
   private final List<Coords> coords_ = new ArrayList<Coords>();
   private final List<long[]> values_ = new ArrayList<long[]>();

   public static DataViewerMousePixelInfoChangedEvent fromImage(int x, int y,
                                                                Image image) {
      Preconditions.checkNotNull(image);
      return fromAxesAndImages(x, y, new String[] {},
            Collections.singletonList(image));
   }

   public static DataViewerMousePixelInfoChangedEvent fromAxesAndImages(
         int x, int y, String[] indexingAxes, List<Image> images) {
      Preconditions.checkNotNull(images);
      if (indexingAxes == null) {
         indexingAxes = new String[] {};
      }

      List<Coords> coords = new ArrayList<Coords>();
      List<long[]> componentValues = new ArrayList<long[]>();
      for (Image image : images) {
         Preconditions.checkNotNull(image);
         Coords c = image.getCoords();
         Coords.CoordsBuilder cb = new DefaultCoords.Builder();
         for (String axis : indexingAxes) {
            cb.index(axis, c.getIndex(axis));
         }
         coords.add(cb.build());
         componentValues.add(image.getComponentIntensitiesAt(x, y));
      }
      return create(x, y, indexingAxes, coords, componentValues);
   }

   public static DataViewerMousePixelInfoChangedEvent create(int x, int y,
                                                             String[] indexingAxes,
                                                             List<Coords> coords,
                                                             List<long[]> componentValues) {
      Preconditions.checkNotNull(coords);
      Preconditions.checkNotNull(componentValues);
      Preconditions.checkArgument(coords.size() == componentValues.size());
      if (indexingAxes == null) {
         indexingAxes = new String[] {};
      }
      if (indexingAxes.length == 0) {
         Preconditions.checkArgument(coords.size() <= 1,
               "Indexing axes required for multiple coords");
      }
      return new DataViewerMousePixelInfoChangedEvent(x, y,
            indexingAxes, coords, componentValues);
   }

   public static DataViewerMousePixelInfoChangedEvent createUnavailable() {
      return new DataViewerMousePixelInfoChangedEvent(-1, -1, new String[] {},
            Collections.<Coords>emptyList(), Collections.<long[]>emptyList());
   }

   private DataViewerMousePixelInfoChangedEvent(int x, int y,
                                                String[] indexingAxes,
                                                List<Coords> coords, List<long[]> componentValues) {
      for (int i = 0; i < coords.size(); ++i) {
         Coords c1 = coords.get(i).copyRetainingAxes(indexingAxes);
         for (Coords c2 : coords.subList(i + 1, coords.size())) {
            c2 = c2.copyRetainingAxes(indexingAxes);
            Preconditions.checkArgument(!c1.equals(c2), "Coords must be unique");
         }
      }

      x_ = x;
      y_ = y;
      indexingAxes_ = indexingAxes.clone();
      coords_.addAll(coords);
      values_.addAll(componentValues);
   }

   public boolean isInfoAvailable() {
      return x_ >= 0 && y_ >= 0 && !coords_.isEmpty();
   }

   public int getX() {
      return x_;
   }

   public int getY() {
      return y_;
   }

   public String getXYString() {
      if (!isInfoAvailable()) {
         return "NA";
      }
      return String.format("%d, %d", x_, y_);
   }

   public String[] getIndexingAxes() {
      return indexingAxes_.clone();
   }

   public int getNumberOfCoords() {
      return coords_.size();
   }

   public List<Coords> getAllCoords() {
      return Collections.unmodifiableList(coords_);
   }

   public List<Coords> getAllCoordsSorted() {
      List<Coords> ret = new ArrayList<Coords>(coords_);
      Collections.sort(ret, new Comparator<Coords>() {
         @Override
         public int compare(Coords o1, Coords o2) {
            for (String axis : indexingAxes_) {
               int c = new Integer(o1.getIndex(axis)).compareTo(o2.getIndex(axis));
               if (c != 0) {
                  return c;
               }
            }
            return 0;
         }
      });
      return ret;
   }

   public long[] getComponentValuesForCoords(Coords coords) {
      int i = coords_.indexOf(coords);
      if (i < 0) {
         throw new IllegalArgumentException();
      }
      return values_.get(i).clone();
   }

   public String getComponentValuesStringForCoords(Coords coords) {
      long[] values = getComponentValuesForCoords(coords);
      if (values.length == 1) {
         return String.valueOf(values[0]);
      }
      return "(" + Joiner.on(", ").join(ArrayUtils.toObject(
            getComponentValuesForCoords(coords))) + ")";
   }
}