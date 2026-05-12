/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.display.internal.imagestats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author mark
 */
public class ImageStats {
   private final int index_;
   private final List<ComponentStats> componentStats_;

   public static ImageStats create(int index, ComponentStats... componentStats) {
      return new ImageStats(index, componentStats);
   }

   private ImageStats(int index, ComponentStats... componentStats) {
      index_ = index;
      componentStats_ = new ArrayList<>(Arrays.asList(componentStats));
   }

   public int getNumberOfComponents() {
      return componentStats_.size();
   }

   public ComponentStats getComponentStats(int component) {
      return componentStats_.get(component);
   }

   // Index within request
   public int getIndex() {
      return index_;
   }
}