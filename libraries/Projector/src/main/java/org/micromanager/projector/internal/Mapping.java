
package org.micromanager.projector.internal;

import java.awt.Polygon;
import java.awt.geom.AffineTransform;
import java.util.HashMap;
import java.util.Map;
import mmcorej.CMMCore;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.projector.ProjectionDevice;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 *
 * @author nico
 */
public class Mapping {
   
   private final static String MAP_NR_ENTRIES = "NrEntries";
   private final static String MAP_POLYGON_MAP = "polygonMap-";
   private final static String MAP_AFFINE_TRANSFORM = "affineTransform-";
   
   /**
    * Returns the key where we store the Calibration mapping.
    * Each channel/camera combination is assigned a different node.
    */
   static String getCalibrationNode(CMMCore core, ProjectionDevice dev) {
      if (dev != null && core != null) {
         return "calibration" + dev.getChannel() + core.getCameraDevice();
      }
      return "";
   }
   
   static String getCalibrationKey(CMMCore core, ProjectionDevice dev) {
      if (dev != null) {
         return getCalibrationNode(core, dev) + "-" + dev.getName();
      }
      return "";
   }
   
   
   
    /**
    * Save the mapping for the current calibration node. The mapping
    * maps each polygon cell to an AffineTransform.
    */
   static void saveMapping(CMMCore core, ProjectionDevice dev, 
           MutablePropertyMapView settings, HashMap<Polygon, AffineTransform> mapping) {
      settings.putPropertyMap(getCalibrationKey(core, dev),
               mapToPropertyMap(mapping));
   }
   
   /**
    * Builds a Property Map representing the input 
    * @param mapping Map<Polygon, AffineTransform>
    * @return Propertymap, structured as:
    *   - Integer - nrEntries
    *   - entries with keys:
    *       polygonMap-i  - Contains a PropertyMap encoding Polygon
    *       affineTransform-i - Contained affineTransform belonging to this Polygon
    * 
    *    Polygon PropertyMaps are structured as:
    *       - Integer - nrEntries
    *       - x-i - x position of point #i
    *       - y-i - y position of point #i
    */
   static PropertyMap mapToPropertyMap(Map<Polygon, AffineTransform> mapping) {
      PropertyMap.Builder pMapBuilder = PropertyMaps.builder();
      pMapBuilder.putInteger(MAP_NR_ENTRIES, mapping.size());
      
      int counter = 0;
      for (Polygon key : mapping.keySet()) {
         PropertyMap.Builder polygonMapBuilder = PropertyMaps.builder();
         polygonMapBuilder.putInteger(MAP_NR_ENTRIES, key.npoints);
         for (int i = 0; i < key.npoints; i++) {
            polygonMapBuilder.putInteger("x-" + i, key.xpoints[i]);
            polygonMapBuilder.putInteger("y-" + i, key.ypoints[i]);
         }
         pMapBuilder.putPropertyMap(MAP_POLYGON_MAP + counter, polygonMapBuilder.build());
         pMapBuilder.putAffineTransform(MAP_AFFINE_TRANSFORM + counter, mapping.get(key));
         counter++;
      }
      
      return pMapBuilder.build();
   }
   
   /**
    * Load the mapping for the current calibration node. The mapping
    * maps each polygon cell to an AffineTransform.
    */
   static Map<Polygon, AffineTransform> loadMapping(CMMCore core, ProjectionDevice dev, 
           MutablePropertyMapView settings) {
      PropertyMap pMap = settings.getPropertyMap(getCalibrationKey(core, dev), null);
      if (pMap != null) {
         return mapFromPropertyMap(pMap);
      }
      
      return null;
   }
   
   /**
    * Restores mapping from a PropertyMap created by the function mapToPropertyMap
    * @param pMap
    * @return 
    */
   static Map<Polygon, AffineTransform> mapFromPropertyMap (PropertyMap pMap) {
      int nrEntries = pMap.getInteger(MAP_NR_ENTRIES, 0);
      Map<Polygon, AffineTransform> mapping = new HashMap<Polygon, AffineTransform>(nrEntries);
      for (int i = 0; i < nrEntries; i++) {
         if (pMap.containsPropertyMap(MAP_POLYGON_MAP + i)) {
            PropertyMap polygonMap = pMap.getPropertyMap(MAP_POLYGON_MAP + i, null);
            Polygon p = new Polygon();
            int nrPolygonCorners = polygonMap.getInteger(MAP_NR_ENTRIES, 0);
            for (int j=0; j < nrPolygonCorners; j++) {
               p.addPoint(polygonMap.getInteger("x-" + j, 0), 
                       polygonMap.getInteger("y-" + j, 0));
            }
            mapping.put(p, pMap.getAffineTransform(MAP_AFFINE_TRANSFORM + i, null));
         }
      }
         
      return mapping;
   }

}
