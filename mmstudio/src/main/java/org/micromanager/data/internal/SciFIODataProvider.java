
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
import net.imagej.axis.Axes;
import net.imagej.axis.CalibratedAxis;
import org.micromanager.Studio;
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
   private final SummaryMetadata sm_;
   private double pixelSize_;
   private final static int IMAGEINDEX = 0; // ScioFIO image index.  It is unclear what this
   // is.  However, most datasets appear to have only a single imageindex (0)
   // Use it as a variable to be ready to use it if need be
   private final List<Object> listeners_ = new ArrayList<>();
   private final Studio studio_;
   
   public SciFIODataProvider(Studio studio, String path) {
      // create the ScioFIO context that is needed for eveything
      scifio_ = new SCIFIO();
      studio_ = studio;
      try {
         reader_ = scifio_.initializer().initializeReader(path);
      } catch (io.scif.FormatException | IOException ex) {
         Logger.getLogger(SciFIODataProvider.class.getName()).log(Level.SEVERE, null, ex);
      }
      metadata_ = reader_.getMetadata();
      int nrImages = reader_.getImageCount();
      long nrPlanes = reader_.getPlaneCount(IMAGEINDEX);
      System.out.println(path + "has " + nrImages + "images, and " + nrPlanes + " planes");
      System.out.println("Format:" + reader_.getFormatName());
      
      SummaryMetadata.Builder smb = studio_.data().getSummaryMetadataBuilder();
      ImageMetadata im = metadata_.get(IMAGEINDEX);
      List<String> channelNames = new ArrayList<>();
      List<CalibratedAxis> axes = im.getAxes();
      for (CalibratedAxis axis : axes) {
         if (axis.type().equals(Axes.X)) {
             pixelSize_ = axis.calibratedValue(1.0);
         } if (axis.type().equals(Axes.Z)) {
            smb.zStepUm(axis.calibratedValue(1.0));
         } if (axis.type().equals(Axes.CHANNEL)) {
            for (int i = 0; i < im.getAxisLength(axis); i++) {
               // TODO: once SciFIO supports channelnames. use those instead of numbering
               channelNames.add("Ch: " + i);
            }
            smb.channelNames(channelNames);
         }
      }
      smb.intendedDimensions(rasterPositionToCoords(im, im.getAxesLengthsNonPlanar()));
      smb.prefix(im.getName());
      sm_ = smb.build();
   }
   
      public Image planeToImage(Plane plane, final Coords coords) {
      int pixelType = plane.getImageMetadata().getPixelType();
      int bytesPerPixel = 1;
      if (pixelType == FormatTools.UINT16) {
         bytesPerPixel = 2;
      } else if (pixelType == FormatTools.UINT32) {
         bytesPerPixel = 4;
      }
      org.micromanager.data.Metadata.Builder mb = studio_.data().getMetadataBuilder();
      mb.bitDepth(plane.getImageMetadata().getBitsPerPixel());
      mb.pixelSizeUm(pixelSize_);
      // TODO: what to do with INT? How to recognize multiple components?
      // TODO: convert metadata
      return DefaultDataManager.getInstance().createImage(
              plane.getBytes(),
              (int) plane.getLengths()[0], 
              (int) plane.getLengths()[1],
              bytesPerPixel, 
              1, 
              coords, 
              mb.build() );
   }
   
   public Image planeToImage(final Plane plane, final long[] rasterPosition) {
      final Coords coords = rasterPositionToCoords(plane.getImageMetadata(), rasterPosition);
      return planeToImage(plane, coords);
   }
   
   public static Coords rasterPositionToCoords(final ImageMetadata im, final long[] rasterPosition) {
      Coords.Builder cb = Coordinates.builder();
      cb.c(0).t(0).p(0).z(0);
      List<CalibratedAxis> axes = im.getAxes();
      for (CalibratedAxis axis : axes) {
         int index = im.getAxisIndex(axis) - 2;
         if (axis.type().getLabel().equals(Axes.CHANNEL.getLabel())) {
            cb.c((int) rasterPosition[index]);
         } else if  (axis.type().getLabel().equals(Axes.TIME.getLabel())) {
            cb.t((int) rasterPosition[index]);
         } else if  (axis.type().getLabel().equals(Axes.Z.getLabel())) {
            cb.z((int) rasterPosition[index]);
         } else {
            String label = axis.type().getLabel();
            if (!label.equals("X") && !label.equals("Y")) {
               cb.index(axis.type().getLabel(), (int) rasterPosition[index]);
            }
         }
      }
      return cb.build();
   }
   
   public static long[] coordsToRasterPosition(final ImageMetadata im, Coords coords) {
      long[] planeIndices = new long[im.getAxesNonPlanar().size()];
      List<CalibratedAxis> axes = im.getAxesNonPlanar();
      for (CalibratedAxis axis : axes) {
         int index = im.getAxisIndex(axis) - 2;
         if (axis.type().getLabel().equals(Axes.CHANNEL.getLabel())) {
            planeIndices[index] = coords.getC();
         } else if  (axis.type().getLabel().equals(Axes.TIME.getLabel())) {
            planeIndices[index] = coords.getT();
         } else if  (axis.type().getLabel().equals(Axes.Z.getLabel())) {
            planeIndices[index] = coords.getZ();
         } else {
            String label = axis.type().getLabel();
            if (!label.equals("X") && !label.equals("Y")) {
               planeIndices[index] = coords.getIndex(label);
            }
         }
      }
      return planeIndices;
     
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
         long planeIndex = 0; // TODO: check we actually have a plane at index 0?
         final long[] rasterPosition = FormatTools.rasterToPosition(IMAGEINDEX, 
                 planeIndex, reader_);
         return planeToImage(reader_.openPlane(IMAGEINDEX, planeIndex), 
                 rasterPosition);
      } catch (io.scif.FormatException ex) {
         throw new IOException(ex);
      }
   }

   @Override
   public List<String> getAxes() {
      ImageMetadata im = metadata_.get(IMAGEINDEX);
      List<CalibratedAxis> axes = im.getAxesNonPlanar();
      List<String> mmAxes = new ArrayList<>(axes.size());
      for (CalibratedAxis axis : axes) {
         if (axis.type().getLabel().equals(Axes.CHANNEL.getLabel())) {
             mmAxes.add(Coords.C);
         } else if  (axis.type().getLabel().equals(Axes.TIME.getLabel())) {
            mmAxes.add(Coords.T);
         } else if  (axis.type().getLabel().equals(Axes.Z.getLabel())) {
            mmAxes.add(Coords.Z);
         } else {
            String label = axis.type().getLabel();
            if (!label.equals("X") && !label.equals("Y")) {
               mmAxes.add(label);
            }
         }
      }   
      return mmAxes;
   }

   @Override
   public int getAxisLength(String mmAxis) {
      ImageMetadata im = metadata_.get(IMAGEINDEX);
      List<CalibratedAxis> axes = im.getAxesNonPlanar();
      String sciFioAxis = mmAxis;
      switch (mmAxis) {
         case Coords.C:
            sciFioAxis = Axes.CHANNEL.getLabel();
            break;
         case Coords.T:
            sciFioAxis = Axes.TIME.getLabel();
            break;
         case Coords.Z:
            sciFioAxis = Axes.Z.getLabel();
            break;
         default:
            break;
      }
      for (CalibratedAxis axis : axes) {
         if (axis.type().getLabel().equals(sciFioAxis)) {
            return (int) im.getAxisLength(axis);
         }
      }
      // Axis not found, I guess it is correct that the length i 0?
      return 0;
   }

   @Override
   public Image getImage(Coords coords) throws IOException {
      try {
         long[] planeIndices = coordsToRasterPosition(metadata_.get(IMAGEINDEX), coords);
         long planeIndex = FormatTools.positionToRaster(IMAGEINDEX, reader_, planeIndices);
         Plane plane = reader_.openPlane(IMAGEINDEX, planeIndex);
         return planeToImage(plane, coords);
      } catch (io.scif.FormatException ex) {
         throw new IOException(ex);
      }
   }

   @Override
   public List<Image> getImagesMatching(Coords coords) throws IOException {
      List<Image> result = new ArrayList<>();
      ImageMetadata im = metadata_.get(IMAGEINDEX);
      for (int i = 0; i < im.getPlaneCount(); i++) {
         long[] rasterPosition = FormatTools.rasterToPosition(IMAGEINDEX, i, metadata_);
         Coords tmpC = rasterPositionToCoords(im, rasterPosition);
         if (tmpC.isSubspaceCoordsOf(coords)) {
            result.add(getImage(tmpC));
         }
      }
      return result;
   }

   @Override
   public boolean isFrozen() {
      // for now, we are not writing, so always frozen
      return true;
   }

   @Override
   public Coords getMaxIndices() {
      ImageMetadata im = metadata_.get(IMAGEINDEX);
      long[] rasterLengths = new long[im.getAxesLengths().length - 2];
      for (int i = 2; i < im.getAxesLengths().length; i++) {
         rasterLengths[i - 2] = im.getAxesLengths()[i];
      }
      return rasterPositionToCoords(im, rasterLengths);
   }

   @Override
   public int getNumImages() {
      return (int) metadata_.get(IMAGEINDEX).getPlaneCount();
   }

   @Override
   public SummaryMetadata getSummaryMetadata() {
      return sm_;
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
      return sm_.getPrefix();
      }

   @Override
   public void registerForEvents(Object obj) {
      listeners_.add(obj);
   }

   @Override
   public void unregisterForEvents(Object obj) {
      listeners_.remove(obj);
   }
   
}
