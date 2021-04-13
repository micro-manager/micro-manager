
package org.micromanager.data.internal;

import com.google.common.eventbus.EventBus;
import io.scif.ImageMetadata;
import io.scif.Metadata;
import io.scif.Plane;
import io.scif.Reader;
import io.scif.SCIFIO;
import io.scif.util.FormatTools;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.imagej.axis.Axes;
import net.imagej.axis.CalibratedAxis;
import org.micromanager.Studio;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Image;
import org.micromanager.data.SummaryMetadata;
import org.scijava.util.Bytes;

/**
 * Wrap the SciFIO library in a Micro-Manager dataProvider
 * So far, only uint8 and uint16 type datasources are supported
 * 
 * @author nico
 */
public class SciFIODataProvider implements DataProvider {
   private final SCIFIO scifio_;
   private Reader reader_;
   private final Metadata metadata_;
   private final SummaryMetadata sm_;
   private final EventBus bus_ = new EventBus();
   private double pixelSize_;
   private final static int IMAGEINDEX = 0; // ScioFIO image index.  It is unclear what this
   // is.  However, most datasets appear to have only a single imageindex (0)
   // Use it as a variable to be ready to use it if need be
   private int xAxisIndex_ = 0;
   private int yAxisIndex_ = 1;
   private int channelAxisIndex_ = 2;
   private boolean channelAxisNonPlanar_ = false;
   private final Coords genCoords_; // tempplate for Coords that we feed MM
   private final Studio studio_;
   
   /**
    * Initializes the reader and creates Micro-Manager's summaryMetData
    * It appears that most SciFIO datasources have a single image and
    * multiple Planes.  Conversion of plane index to Multi-D index is carried 
    * out by the SciFIO utility function FormatTools.rasterToPosition().
    * Data are always returned as Bytes.  
    * 
    * @param studio - only used to show error message
    * @param path - path to the data to be read by SciFIO
    */
   public SciFIODataProvider(Studio studio, String path) {
      studio_ = studio;
      // create the ScioFIO context that is needed for eveything
      scifio_ = new SCIFIO();
      try {
         reader_ = scifio_.initializer().initializeReader(path);
      } catch (io.scif.FormatException | IOException ex) {
         if (studio != null) {
            studio.getLogManager().showError(ex, "Failed to open: " + path);
         }
      }
      metadata_ = reader_.getMetadata();
      int nrImages = reader_.getImageCount();
      long nrPlanes = reader_.getPlaneCount(IMAGEINDEX);
      System.out.println(path + " has " + nrImages + " image(s), and " + nrPlanes + " plane(s)");
      System.out.println("Format:" + reader_.getFormatName());
      
      SummaryMetadata.Builder smb = new DefaultSummaryMetadata.Builder();
      ImageMetadata im = metadata_.get(IMAGEINDEX);
      List<String> channelNames = new ArrayList<>();
      List<CalibratedAxis> axes = im.getAxes();
      for (int index = 0; index < axes.size(); index++) {
         CalibratedAxis axis = axes.get(index);
         if (axis.type().equals(Axes.X)) {
             pixelSize_ = axis.calibratedValue(1.0);
             xAxisIndex_ = index;
         } if (axis.type().equals(Axes.Y)) {
            yAxisIndex_ = index;
         } if (axis.type().equals(Axes.Z)) {
            smb.zStepUm(axis.calibratedValue(1.0));
         } if (axis.type().equals(Axes.CHANNEL)) {
            channelAxisIndex_ = index;
            if (im.getAxesNonPlanar().contains(axis)) {
               channelAxisNonPlanar_ = true;
            }
            for (int i = 0; i < im.getAxisLength(axis); i++) {
               // TODO: once SciFIO supports channelnames. use those instead of numbering
               channelNames.add("Ch: " + i);
            }
         }
      }
      // Note: The Inspector insists on there always being a channel name
      // so, even if we do not have channels, provide one dummy channel name
      if (channelNames.isEmpty()) {
         channelNames.add("Ch: 0");
      }
      smb.channelNames(channelNames);
      Coords.Builder cb = Coordinates.builder();
      cb.c(1).t(1).p(1).z(1);
      smb.intendedDimensions(rasterPositionToCoords(im, im.getAxesLengths(), cb));
      Coords tmpCoords = cb.build();
      Coords.Builder gcb = tmpCoords.copyBuilder();
      for (String axis : tmpCoords.getAxes()) {
         gcb.index(axis, 0);
      }
      genCoords_ = gcb.build();
      String name = metadata_.getDatasetName();
      if (name.lastIndexOf(File.separator) > 0) {
         name = name.substring(name.lastIndexOf(File.separator) + 1);
      }
      smb.prefix(name);
      sm_ = smb.build();
   }
   
   public Image planeToImage(Plane plane, final Coords coords) throws IOException {
      int pixelType = plane.getImageMetadata().getPixelType();
      int bytesPerPixel = 1;
      switch (pixelType) {
         case FormatTools.UINT16:
            bytesPerPixel = 2;
            break;
         // note: Micro-Manager can not handle bytesPerPixel > 2
         case FormatTools.UINT32:
            bytesPerPixel = 4;
            break;
         case FormatTools.FLOAT:
            bytesPerPixel = 4;
            break;
         default:
            break;
      }
      
      if (bytesPerPixel == 4) {
         throw new IOException ("Unsupported image format");
      }
      
      org.micromanager.data.Metadata.Builder mb = new DefaultMetadata.Builder();
      mb.bitDepth(plane.getImageMetadata().getBitsPerPixel());
      mb.pixelSizeUm(pixelSize_);
      // TODO: translate more metadata
      Object pixels = Bytes.makeArray(plane.getBytes(), bytesPerPixel, 
              false, plane.getImageMetadata().isLittleEndian() );
      boolean isMultichannel = plane.getImageMetadata().isMultichannel();
      int interleavedCount = 1;
      if (isMultichannel && !channelAxisNonPlanar_) {
         interleavedCount = (int) plane.getImageMetadata().getAxesLengths()[channelAxisIndex_];
      }
      if (interleavedCount == 3) {
         if (bytesPerPixel == 1) {
            byte[] tmpP = new byte[ ((byte[]) pixels).length / 3 ];
            for (int i = 0; i < tmpP.length; i++) {
               tmpP[i] = ( (byte[]) pixels)[3 * i + coords.getC()];
            }
            pixels = tmpP;
         } else if (bytesPerPixel == 2) {
            short[] tmpP = new short[ ((short[]) pixels).length / 3 ];
            for (int i = 0; i < tmpP.length; i++) {
               tmpP[i] = ( (short[]) pixels)[3 * i + coords.getC()];
            }
            pixels = tmpP;
         }
      }
      
      // We need to set all the axes MM knows about
      // This seems to me like a bug in MM...
      Coords.Builder cb = genCoords_.copyBuilder();
      for (String axis : coords.getAxes()) {
         cb.index(axis, coords.getIndex(axis));
      }
      
      Image img =  studio_.data().wrapImage(pixels,
              (int) plane.getLengths()[xAxisIndex_], 
              (int) plane.getLengths()[yAxisIndex_],
              bytesPerPixel, 
              1, 
              cb.build(), 
              mb.build() );
      // bus_.post(new DefaultNewImageEvent(img, this));
      return img;
   }
   
   public Image planeToImage(final Plane plane, final long[] rasterPosition) 
           throws IOException {
      final Coords coords = rasterPositionToCoords(plane.getImageMetadata(), 
              rasterPosition);
      return planeToImage(plane, coords);
   }
   
   public Coords rasterPositionToCoords(final ImageMetadata im, final long[] rasterPosition) {
      Coords.Builder cb = Coordinates.builder();
      cb.c(0).t(0).p(0).z(0);
      return rasterPositionToCoords(im, rasterPosition, cb);
   }
      
   public final Coords rasterPositionToCoords(final ImageMetadata im, 
           final long[] rasterPosition, Coords.Builder cb) {
      List<CalibratedAxis> axes = im.getAxes();
      int diff = rasterPosition.length - axes.size();
      if (diff != 0) {
         // this is bad, how can we ever translate one into the other???
         // It looks like axis have only one rasterPostion
         if (reader_.getFormatName().equals("Audio Video Interleave")) {
            cb.t((int) rasterPosition[0]);
            return cb.build();
         } else {
            axes = im.getAxesNonPlanar();
         }
      }
      for (int index = 0; index <axes.size(); index++) {
         CalibratedAxis axis = axes.get(index);
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
   
   public long[] coordsToRasterPosition(final ImageMetadata im, Coords coords) {
      if (reader_.getFormatName().equals("Audio Video Interleave")) {
         long[] planeIndices = new long[1];
         planeIndices[0] = coords.getT();
         return planeIndices;
      }
      
      long[] planeIndices = new long[im.getAxesNonPlanar().size()];
      List<CalibratedAxis> axes = im.getAxesNonPlanar();
      for (int index = 0; index < planeIndices.length; index++) {
         CalibratedAxis axis = axes.get(index);
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
                 planeIndex, reader_.getMetadata());
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
      return getNextIndex(mmAxis);
   }
   
   @Override
    public int getNextIndex(String mmAxis) {
       ImageMetadata im = metadata_.get(IMAGEINDEX);
      List<CalibratedAxis> axes = im.getAxes();
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
            int axisLength = (int) im.getAxisLength(axis);
            return axisLength;
         }
      }
      // Axis not found, I guess it is correct that the length i 0?
      return 0;
    }

   @Override
   public Image getImage(Coords coords) throws IOException {
      return planeToImage(getPlane(coords), coords);
   }
   
   private Plane getPlane(Coords coords) throws IOException {
      try {
         long[] planeIndices = coordsToRasterPosition(metadata_.get(IMAGEINDEX), coords);
         long planeIndex = FormatTools.positionToRaster(IMAGEINDEX, reader_, planeIndices);
         return reader_.openPlane(IMAGEINDEX, planeIndex);
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
         if (tmpC.equals(coords)) {
            Plane plane = getPlane(coords);
            if (im.isMultichannel() && channelAxisIndex_ == 0) {
               for (int c = 0; c < im.getAxisLength(channelAxisIndex_); c++) {
                  result.add(planeToImage(plane, 
                          coords.copyBuilder().c(c).build()));
               }
            } else {
               result.add(planeToImage(plane, coords));
            }
         }
      }
      return result;
   }

   @Override
   public List<Image> getImagesIgnoringAxes(Coords coords, String... ignoreTheseAxes) throws IOException {
      List<Image> result = new ArrayList<>();
      ImageMetadata im = metadata_.get(IMAGEINDEX);
      for (int i = 0; i < im.getPlaneCount(); i++) {
         long[] rasterPosition = FormatTools.rasterToPosition(IMAGEINDEX, i, metadata_);
         Coords tmpC = rasterPositionToCoords(im, rasterPosition);
         if (coords.equals(tmpC.copyRemovingAxes(ignoreTheseAxes))) {
            Plane plane = getPlane(coords);
            if (im.isMultichannel() && channelAxisIndex_ == 0) {
               for (int c = 0; c < im.getAxisLength(channelAxisIndex_); c++) {
                  result.add(planeToImage(plane,
                          coords.copyBuilder().c(c).build()));
               }
            } else {
               result.add(planeToImage(plane, coords));
            }
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
   @Deprecated
   public Coords getMaxIndices() {
      ImageMetadata im = metadata_.get(IMAGEINDEX);
      // assume that there are always an X and a Y axis
      long[] rasterLengths = new long[im.getAxesLengths().length];
      List<CalibratedAxis> axes = im.getAxes();
      int index = 0; 
      for (CalibratedAxis axis : axes) {
            rasterLengths[index] = im.getAxisLength(axis);
            index++;
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
      List<Coords> cList = new ArrayList<>();
      ImageMetadata im = metadata_.get(IMAGEINDEX);
      for (int i = 0; i < im.getPlaneCount(); i++) {
         long[] rasterPosition = FormatTools.rasterToPosition(IMAGEINDEX, i, metadata_);
         cList.add(rasterPositionToCoords(im, rasterPosition));
      }
      return cList;
   }

   @Override
   public boolean hasImage(Coords coords) {
      ImageMetadata im = metadata_.get(IMAGEINDEX);
      for (int i = 0; i < im.getPlaneCount(); i++) {
         long[] rasterPosition = FormatTools.rasterToPosition(IMAGEINDEX, i, metadata_);
         Coords tmpC = rasterPositionToCoords(im, rasterPosition);
         if (tmpC.equals(coords)) {
            return true;
         }
      }
      return false;
   }

   @Override
   public String getName() {
      return sm_.getPrefix();
   }

   @Override
   public void registerForEvents(Object obj) {
       bus_.register(obj);
      try {
         // Very bizar.  This is needed to convince the viewer to display an image
         bus_.post(new DefaultNewImageEvent(getAnyImage(), this));
      } catch (IOException ex) {
         // todo
      }
   }

   @Override
   public void unregisterForEvents(Object obj) {
      bus_.unregister(obj);
   }


   
}
