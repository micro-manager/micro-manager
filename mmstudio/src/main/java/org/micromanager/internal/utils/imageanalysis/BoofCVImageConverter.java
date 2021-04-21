package org.micromanager.internal.utils.imageanalysis;

import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayU16;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageDataType;
import boofcv.struct.image.ImageGray;
import georegression.struct.affine.Affine2D_F64;
import georegression.struct.point.Point2D_I32;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.internal.DefaultImage;

/**
 * Collection of static functions that convert between ImageJ, Micro-Manager, and BoofCV images.
 * Unless otherwise noted, pixels are not copied, but references are handed over. Therefore, be
 * careful changing pixels
 *
 * @author Nico
 */
public final class BoofCVImageConverter {

  // TODO: Metadata; may need to make a class that inherits from ImageGray
  // and adds metadata field

  // Note: Types for ImageGray are not optimal.  Ideally, it would specify
  // the types that are actually handled (currently GrayU8 and GrayU16)
  // However, I can not figure out how to do that

  public static ImageGray<? extends ImageGray<?>> mmToBoofCV(Image image) {
    return mmToBoofCV(image, true);
  }

  public static ImageGray<? extends ImageGray<?>> mmToBoofCV(Image image, boolean copy) {
    ImageGray<? extends ImageGray<?>> outImage;
    switch (image.getBytesPerPixel()) {
      case 1:
        GrayU8 tmp8Image = new GrayU8();
        if (copy) {
          tmp8Image.setData((byte[]) image.getRawPixelsCopy());
        } else {
          tmp8Image.setData((byte[]) image.getRawPixels());
        }
        outImage = tmp8Image;
        break;
      case 2:
        GrayU16 tmp16Image = new GrayU16();
        if (copy) {
          tmp16Image.setData((short[]) image.getRawPixelsCopy());
        } else {
          tmp16Image.setData((short[]) image.getRawPixels());
        }
        outImage = tmp16Image;
        break;
        // TODO: RGB?
      default: // TODO: catch this as exception?
        GrayU8 tmpImage = new GrayU8();
        if (copy) {
          tmpImage.setData((byte[]) image.getRawPixelsCopy());
        } else {
          tmpImage.setData((byte[]) image.getRawPixels());
        }
        outImage = tmpImage;
    }
    outImage.setWidth(image.getWidth());
    outImage.setHeight(image.getHeight());
    outImage.setStride(image.getWidth());

    return outImage;
  }

  /**
   * Converts a boofCV image into a MM image Currently only supports GrayU8 and GrayU16, returns
   * null for other types
   *
   * @param input input boofCV
   * @param c Coords that will be added to the image
   * @param md Metadata to be added to the image
   * @return MM image
   */
  public static Image boofCVToMM(ImageGray<? extends ImageGray<?>> input, Coords c, Metadata md) {
    return boofCVToMM(input, true, c, md);
  }

  /**
   * Converts a boofCV image into a MM image Currently only supports GrayU8 and GrayU16, returns
   * null for other types
   *
   * @param input input boofCV
   * @param copy if true a copy of the pixel data will be used, when false, pixels will be shared
   *     between boofCV image and MM image
   * @param c Coords that will be added to the image
   * @param md Metadata to be added to the image
   * @return MM image
   */
  public static Image boofCVToMM(
      ImageGray<? extends ImageGray<?>> input, boolean copy, Coords c, Metadata md) {
    Image output = null;
    if (input.getDataType().equals(ImageDataType.U8)) {
      GrayU8 in8 = (GrayU8) input;
      Object pixels = copy ? in8.getData().clone() : in8.getData();
      output = new DefaultImage(pixels, input.getWidth(), input.getHeight(), 1, 1, c, md);
    }
    if (input.getDataType().equals(ImageDataType.U16)) {
      GrayU16 in16 = (GrayU16) input;
      Object pixels = copy ? in16.getData().clone() : in16.getData();
      output = new DefaultImage(pixels, input.getWidth(), input.getHeight(), 2, 1, c, md);
    } else {
      // Todo: throw exception?  return null for now
    }

    return output;
  }

  /**
   * Converts a BoofCV image into an ImageJ ImageProcessor
   *
   * <p>TODO: support other types besides GrayU8 and GrayU16
   *
   * @param imgG Input BoofCV image
   * @param copy If true, pixels will be copied, otherwise a reference to the BoofCV image's pixels
   *     will be handed to the ImageProcerros
   * @return ImageJ ImageProcessor
   */
  public static ImageProcessor convert(ImageGray<? extends ImageGray<?>> imgG, boolean copy) {
    ImageProcessor ip = null;
    if (imgG instanceof GrayU8) {
      if (copy) {
        ip = new ByteProcessor(imgG.width, imgG.height, ((GrayU8) imgG).getData().clone());
      } else {
        ip = new ByteProcessor(imgG.width, imgG.height, ((GrayU8) imgG).getData());
      }
    } else if (imgG instanceof GrayU16) {

      if (copy) {
        ip = new ShortProcessor(imgG.width, imgG.height, ((GrayU16) imgG).getData().clone(), null);
      } else {
        ip = new ShortProcessor(imgG.width, imgG.height, ((GrayU16) imgG).getData(), null);
      }
    } else if (imgG instanceof GrayF32) {
      if (copy) {
        ip = new FloatProcessor(imgG.width, imgG.height, ((GrayF32) imgG).getData().clone());
      } else {
        ip = new FloatProcessor(imgG.width, imgG.height, ((GrayF32) imgG).getData());
      }
    }
    return ip;
  }

  /**
   * Converts an ImageJ ImageProcessor to a BoofCV image
   *
   * @param ip
   * @param copy
   * @return
   */
  public static ImageGray<? extends ImageGray<?>> convert(ImageProcessor ip, boolean copy) {
    ImageGray<? extends ImageGray<?>> ig = null;
    if (ip instanceof ByteProcessor) {
      GrayU8 tmp8Image = new GrayU8(); // ip.getWidth(), ip.getHeight());
      if (copy) {
        tmp8Image.setData(((byte[]) ip.getPixels()).clone());
      } else {
        tmp8Image.setData((byte[]) ip.getPixels());
      }
      ig = tmp8Image;
    } else if (ip instanceof ShortProcessor) {
      GrayU16 tmp16Image = new GrayU16();
      if (copy) {
        tmp16Image.setData(((short[]) ip.getPixels()).clone());
      } else {
        tmp16Image.setData((short[]) ip.getPixels());
      }
      ig = tmp16Image;
    } else if (ip instanceof FloatProcessor) {
      GrayF32 tmpF32Image = new GrayF32();
      if (copy) {
        tmpF32Image.setData(((float[]) ip.getPixels()).clone());
      } else {
        tmpF32Image.setData((float[]) ip.getPixels());
      }
      ig = tmpF32Image;
    }
    if (ig != null) {
      ig.setWidth(ip.getWidth());
      ig.setStride(ip.getWidth());
      ig.setHeight(ip.getHeight());
    }

    return ig;
  }

  /**
   * Utility function. Extracts region from a MM dataset and returns as a BoofCV ImageGray. Points
   * to the same pixel data as the original
   *
   * @param dp Micro-Manager Datasource
   * @param cb Micro-Manager Coords Builder (for efficiency)
   * @param frame Frame number from which we want the image data
   * @param p point around which to build the ROI
   * @param halfBoxSize Half the width and length of the ROI
   * @return ImageGray Note that the pixels are not copied.
   * @throws IOException
   */
  public static ImageGray<? extends ImageGray<?>> subImage(
      final DataProvider dp,
      final Coords.Builder cb,
      final int frame,
      final Point2D_I32 p,
      final int halfBoxSize)
      throws IOException {
    Coords coord = cb.t(frame).build();
    Image img = dp.getImage(coord);

    ImageGray<? extends ImageGray<?>> ig = BoofCVImageConverter.mmToBoofCV(img, false);
    if (p.getX() - halfBoxSize < 0
        || p.getY() - halfBoxSize < 0
        || p.getX() + halfBoxSize >= ig.getWidth()
        || p.getY() + halfBoxSize >= ig.getHeight()) {
      return null; // TODO: we'll get stuck at the edge
    }
    /* this has very strange consequences...
          if (p.getX() - halfBoxSize < 0) {
       p.set(halfBoxSize, p.getY());
    }
    if (p.getY() - halfBoxSize < 0) {
       p.set(p.getX(), halfBoxSize);
    }
    if (p.getX() + halfBoxSize >= ig.getWidth()) {
       p.set(ig.getWidth() - halfBoxSize - 1, p.getY());
    }
    if (p.getY() + halfBoxSize >= ig.getHeight()) {
       p.set(p.getX(), ig.getHeight() - halfBoxSize -1);
       //return null; // TODO: we'll get stuck at the edge
    }
    */
    return (ImageGray<? extends ImageGray<?>>)
        ig.subimage(
            p.getX() - halfBoxSize,
            p.getY() - halfBoxSize,
            p.getX() + halfBoxSize,
            p.getY() + halfBoxSize);
  }

  /**
   * Converts a Java affine transform into a Georegression Affine transform
   *
   * @param in
   * @return
   */
  public static Affine2D_F64 convertAff(AffineTransform in) {
    return new Affine2D_F64(
        in.getScaleX(), in.getShearX(), in.getShearY(), in.getScaleY(), 0.0, 0.0);
  }
}
