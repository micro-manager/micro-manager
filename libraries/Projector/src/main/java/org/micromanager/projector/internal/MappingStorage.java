package org.micromanager.projector.internal;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.util.HashMap;
import java.util.Map;
import mmcorej.CMMCore;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.projector.Mapping;
import org.micromanager.projector.ProjectionDevice;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * @author nico
 */
public class MappingStorage {

   private final static String MAP_NR_ENTRIES = "NrEntries";
   private final static String MAP_POLYGON_MAP = "polygonMap-";
   private final static String MAP_AFFINE_TRANSFORM = "affineTransform-";
   private final static String MAP_APPROXIMATE_AFFINE = "approximateAffine";
   private final static String MAP_CAMERA_ROI = "CameraROI";
   private final static String MAP_CAMERA_BINNING = "CameraBinning";

   /**
    * Returns the key where we store the Calibration mapping. Each channel/camera combination is
    * assigned a different node.
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
    * Save the mapping for the current calibration node. The mapping maps each polygon cell to an
    * AffineTransform.
    */
   static void saveMapping(final CMMCore core, final ProjectionDevice dev,
         final MutablePropertyMapView settings, final Mapping mapping) {
      settings.putPropertyMap(getCalibrationKey(core, dev),
            mapToPropertyMap(mapping));
   }

   /**
    * Builds a Property Map representing the input
    *
    * @param mapping Map<Polygon, AffineTransform>
    * @return Propertymap, structured as: - Integer - nrEntries - entries with keys: polygonMap-i  -
    * Contains a PropertyMap encoding Polygon affineTransform-i - Contained affineTransform
    * belonging to this Polygon
    * <p>
    * Polygon PropertyMaps are structured as: - Integer - nrEntries - x-i - x position of point #i -
    * y-i - y position of point #i
    */
   static PropertyMap mapToPropertyMap(Mapping mapping) {
      PropertyMap.Builder pMapBuilder = PropertyMaps.builder();
      pMapBuilder.putRectangle(MAP_CAMERA_ROI, mapping.getCameraROI());
      pMapBuilder.putInteger(MAP_CAMERA_BINNING, mapping.getBinning());
      pMapBuilder.putInteger(MAP_NR_ENTRIES, mapping.getMap().size());
      pMapBuilder.putAffineTransform(MAP_APPROXIMATE_AFFINE, mapping.getApproximateTransform());

      int counter = 0;
      for (Polygon key : mapping.getMap().keySet()) {
         PropertyMap.Builder polygonMapBuilder = PropertyMaps.builder();
         polygonMapBuilder.putInteger(MAP_NR_ENTRIES, key.npoints);
         for (int i = 0; i < key.npoints; i++) {
            polygonMapBuilder.putInteger("x-" + i, key.xpoints[i]);
            polygonMapBuilder.putInteger("y-" + i, key.ypoints[i]);
         }
         pMapBuilder.putPropertyMap(MAP_POLYGON_MAP + counter, polygonMapBuilder.build());
         pMapBuilder.putAffineTransform(MAP_AFFINE_TRANSFORM + counter, mapping.getMap().get(key));
         counter++;
      }

      return pMapBuilder.build();
   }

   /**
    * Load the mapping for the current calibration node. The mapping maps each polygon cell to an
    * AffineTransform.
    *
    * @param core     MMCore instance
    * @param dev      projection device for which we need the mapping
    * @param settings PropertyMap containing the mapping
    * @return Mapping
    */
   public static Mapping loadMapping(final CMMCore core, final ProjectionDevice dev,
         final PropertyMap settings) {
      PropertyMap pMap = settings.getPropertyMap(getCalibrationKey(core, dev), null);
      if (pMap != null) {
         return mapFromPropertyMap(core, pMap);
      }

      return null;
   }

   /**
    * Restores mapping from a PropertyMap created by the function mapToPropertyMap
    *
    * @param pMap propertyMap containing mapping data
    * @param core MMCore
    * @return
    */
   static Mapping mapFromPropertyMap(final CMMCore core, final PropertyMap pMap) {
      Rectangle roi = null;
      try {
         roi = core.getROI();
      } catch (Exception ex) {
         core.logMessage("Error obtaining ROI from Core");
      }
      Rectangle cameraROI = pMap.getRectangle(MAP_CAMERA_ROI, roi);
      int cameraBinning = pMap.getInteger(MAP_CAMERA_BINNING, 1);
      int nrEntries = pMap.getInteger(MAP_NR_ENTRIES, 0);
      AffineTransform approximateAF = pMap.getAffineTransform(MAP_APPROXIMATE_AFFINE, null);
      Map<Polygon, AffineTransform> mapping = new HashMap<>(nrEntries);
      for (int i = 0; i < nrEntries; i++) {
         if (pMap.containsPropertyMap(MAP_POLYGON_MAP + i)) {
            PropertyMap polygonMap = pMap.getPropertyMap(MAP_POLYGON_MAP + i, null);
            Polygon p = new Polygon();
            int nrPolygonCorners = polygonMap.getInteger(MAP_NR_ENTRIES, 0);
            for (int j = 0; j < nrPolygonCorners; j++) {
               p.addPoint(polygonMap.getInteger("x-" + j, 0),
                     polygonMap.getInteger("y-" + j, 0));
            }
            mapping.put(p, pMap.getAffineTransform(MAP_AFFINE_TRANSFORM + i, null));
         }
      }

      Mapping.Builder mb = new Mapping.Builder();
      mb.setROI(cameraROI).setApproximateTransform(approximateAF).setBinning(cameraBinning)
            .setMap(mapping);

      return mb.build();
   }

}
