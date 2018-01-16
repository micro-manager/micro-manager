
package org.micromanager.data.internal;

import io.scif.ImageMetadata;
import io.scif.Metadata;
import io.scif.Plane;
import io.scif.Reader;
import io.scif.SCIFIO;
import io.scif.util.FormatTools;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.imagej.axis.CalibratedAxis;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Image;
import org.micromanager.data.SummaryMetadata;

/**
 *
 * @author nico
 */
public class SciFIODataProvider implements DataProvider {
   private final SCIFIO scifio_;
   private Reader reader_;
   private final Metadata metadata_;
   
   public SciFIODataProvider(String path) {
      // create the ScioFIO context that is needed for eveything
      scifio_ = new SCIFIO();
      try {
         reader_ = scifio_.initializer().initializeReader(path);
      } catch (io.scif.FormatException | IOException ex) {
         Logger.getLogger(SciFIODataProvider.class.getName()).log(Level.SEVERE, null, ex);
      }
      metadata_ = reader_.getMetadata();
   }
   
   
   public static Image planeToImage(Plane plane) {
      int pixelType = plane.getImageMetadata().getPixelType();
      int bytesPerPixel = 1;
      if (pixelType == FormatTools.UINT16) {
         bytesPerPixel = 2;
      } else if (pixelType == FormatTools.UINT32) {
         bytesPerPixel = 4;
      }
      // TODO: what to do with INT? How to recognize multiple components?
      // TODO: convert metadata
      return DefaultDataManager.getInstance().createImage(
              plane.getBytes(),
              (int) plane.getLengths()[0], 
              (int) plane.getLengths()[1],
              bytesPerPixel, 
              1, 
              planeAxisToCoords(plane), null);
   }
   
   public static Coords planeAxisToCoords(Plane plane) {
      Coords.Builder cb = Coordinates.builder();
      ImageMetadata im = plane.getImageMetadata();
      List<CalibratedAxis> axes = im.getAxes();
      for (CalibratedAxis axis : axes) {
         switch (axis.type().getLabel()) {
            case "Channel":
               cb.c(im.getAxisIndex(axis));
               break;
            case "Time":
               cb.t(im.getAxisIndex(axis));
               break;
            case "Position":
               cb.p(im.getAxisIndex(axis));
               break;
            default:
               String label = axis.type().getLabel();
               if (!label.equals("X") && !label.equals("Y")) {
                  cb.index(axis.type().getLabel(), im.getAxisIndex(axis));
               }
               break;
         }
      }
      return cb.build();
   }
   
   @Override
   public void close() throws IOException {
      if (reader_ != null) {
         reader_.close(true);
      }
      scifio_.getContext().dispose();
   }

   @Override
   public Image getAnyImage() throws IOException {
      try {
         return SciFIODataProvider.planeToImage(reader_.openPlane(0, 0));
      } catch (io.scif.FormatException ex) {
         throw new IOException(ex);
      }
   }

   @Override
   public List<String> getAxes() {
      ImageMetadata im = metadata_.get(0);
      List<CalibratedAxis> axes = im.getAxes();
      List<String> mmAxes = new ArrayList<>(axes.size());
      for (CalibratedAxis axis : axes) {
         mmAxes.add(axis.type().getLabel());
      }   
      return mmAxes;
   }

   @Override
   public int getAxisLength(String mmAxis) {
      ImageMetadata im = metadata_.get(0);
      List<CalibratedAxis> axes = im.getAxes();
      for (CalibratedAxis axis : axes) {
         if (axis.type().getLabel().equals(mmAxis)) {
            return (int) im.getAxisLength(axis);
         }
      }
      // Axis not found, I guess it is correct that the length i 0?
      return 0;
   }

   @Override
   public Image getImage(Coords coords) throws IOException {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public List<Image> getImagesMatching(Coords coords) throws IOException {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public boolean isFrozen() {
      // for now, we are not writing, so always frozen
      return true;
   }

   @Override
   public Coords getMaxIndices() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public int getNumImages() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public SummaryMetadata getSummaryMetadata() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public Iterable<Coords> getUnorderedImageCoords() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public boolean hasImage(Coords coords) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public String getName() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public void registerForEvents(Object obj) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public void unregisterForEvents(Object obj) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }
   
}
