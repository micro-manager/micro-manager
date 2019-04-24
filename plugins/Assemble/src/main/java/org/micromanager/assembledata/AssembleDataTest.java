package org.micromanager.assembledata;

import boofcv.abst.distort.FDistort;
import boofcv.alg.misc.GImageMiscOps;
import boofcv.alg.misc.GPixelMath;
import boofcv.core.image.GConvertImage;
import boofcv.struct.border.BorderType;
import boofcv.struct.image.GrayU16;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.GrayS32;
import boofcv.struct.image.ImageGray;
import georegression.struct.affine.Affine2D_F64;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.util.List;
import org.micromanager.MultiStagePosition;
import org.micromanager.Studio;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.internal.utils.imageanalysis.BoofCVImageConverter;

/**
 *
 * @author nico
 */
public class AssembleDataTest {
   
   public static void test(Studio studio, DataViewer dv1, DataViewer dv2,
           int xOffset, int yOffset) {
      
      DataProvider dp1 = dv1.getDataProvider();
      DataProvider dp2 = dv2.getDataProvider();
      double zoom = dv1.getDisplaySettings().getZoomRatio();
      if (dv2.getDisplaySettings().getZoomRatio() < zoom) { 
         zoom = dv2.getDisplaySettings().getZoomRatio(); 
      }
      try {
         SummaryMetadata smd1 = dp1.getSummaryMetadata();
         SummaryMetadata smd2 = dp2.getSummaryMetadata();
         List<MultiStagePosition> stageP1 = smd1.getStagePositionList();
         List<MultiStagePosition> stageP2 = smd2.getStagePositionList();
         Coords maxCoords1 = dp2.getMaxIndices();
         Coords maxCoord2 = dp2.getMaxIndices();
         
         Image img1 = dp1.getAnyImage();
         Image img2 = dp2.getAnyImage();
         Metadata imgMD1 = img1.getMetadata();
         Metadata imgMD2 = img2.getMetadata();
         double pSize1 = imgMD1.getPixelSizeUm();
         double pSize2 = imgMD2.getPixelSizeUm();
         AffineTransform aff1 = imgMD1.getPixelSizeAffine();
         AffineTransform aff2 = imgMD2.getPixelSizeAffine();
         double smallestPSize = pSize1;
         AffineTransform ha = aff1;
         AffineTransform ca = aff2;
         if (dp2.getMaxIndices().getP() == 0) {
            ha = aff2;
            ca = aff1;
         }
         Affine2D_F64 hamAff = new Affine2D_F64(ha.getScaleX(), ha.getShearX(), ha.getShearY(), ha.getScaleY(), 0.0, 0.0);
         Affine2D_F64 hamAffI = hamAff.invert(null);
         Affine2D_F64 confocalAff = new Affine2D_F64(ca.getScaleX(), ca.getShearX(), ca.getShearY(), ca.getScaleY(), 0.0, 0.0);
         
         if (pSize2 < smallestPSize) {
            smallestPSize = pSize2;
         }
         
         int bytesPerPixel = img1.getBytesPerPixel();
         if (img2.getBytesPerPixel() != bytesPerPixel) {
            // mm.scripter().message("Images differ in bytes per pixel");
            return;
         }
         
         DataProvider[] datas = {dp1, dp2};

         Coords.Builder cb = Coordinates.builder().t(0).c(0).p(0).z(0);
         // need to initialize these parameters with something sensible
         double xMinUm = img1.getMetadata().getXPositionUm() - (0.5 * img1.getWidth() * pSize1);
         double yMinUm = img1.getMetadata().getYPositionUm() - (0.5 * img1.getHeight() * pSize1);
         double xMaxUm = img1.getMetadata().getXPositionUm() + (0.5 * img1.getWidth() * pSize1);
         double yMaxUm = img1.getMetadata().getYPositionUm() + (0.5 * img1.getHeight() * pSize1);
         
         for (DataProvider data : datas) {
            for (int p = 0; p <= data.getMaxIndices().getP(); p++) {
               Image img = data.getImage(cb.p(p).build());
               double pSize = img.getMetadata().getPixelSizeUm();
               double tmp = img.getMetadata().getXPositionUm() - (0.5 * img.getWidth() * pSize);
               if (tmp < xMinUm) {
                  xMinUm = tmp;
               }
               tmp = img.getMetadata().getYPositionUm() - (0.5 * img.getHeight() * pSize);
               if (tmp < yMinUm) {
                  yMinUm = tmp;
               }
               tmp = img.getMetadata().getXPositionUm() + (0.5 * img.getWidth() * pSize);
               if (tmp > xMaxUm) {
                  xMaxUm = tmp;
               }
               tmp = img.getMetadata().getYPositionUm() + (0.5 * img.getHeight() * pSize);
               if (tmp > yMaxUm) {
                  yMaxUm = tmp;
               }
            }
         }
         
         double widthUm = xMaxUm - xMinUm;
         double heightUm = yMaxUm - yMinUm;
         double centerXUm = xMinUm + (widthUm / 2.0);
         double centerYUm = yMinUm + (heightUm / 2.0);
         
         int widthPixels = (int) (widthUm / smallestPSize) + 1;
         int heightPixels = (int) (heightUm / smallestPSize) + 1;

         // Not sure why, but it looks like the image will end up at the origin
         // rather then the center unless we set this translation to the center
         // of the target image.   
         hamAffI.tx = widthPixels / 2.0 + xOffset;
         hamAffI.ty = heightPixels / 2.0 + yOffset;
         
         Datastore targetStore = studio.data().createRAMDatastore();
         
         int c = 0;
         for (DataProvider data : datas) {
            cb.t(0).c(0).p(0).z(0);
            ImageGray newImgBoof, oldImgBoof, tmpImgBoof, tmp2ImgBoof;
            Metadata.Builder newMetadataB = data.getImage(cb.build()).getMetadata().
                    copyBuilderWithNewUUID().pixelSizeUm(smallestPSize);
            if (bytesPerPixel == 1) {
               newImgBoof = new GrayU8(widthPixels, heightPixels);
               tmpImgBoof = new GrayU8(widthPixels, heightPixels);
               tmp2ImgBoof = new GrayU16(widthPixels, heightPixels);
            } else { // bytesPerPixel == 2
               newImgBoof = new GrayU16(widthPixels, heightPixels);
               tmpImgBoof = new GrayU16(widthPixels, heightPixels);
               tmp2ImgBoof = new GrayS32(widthPixels, heightPixels);
            }
            if (data.getMaxIndices().getP() <= 1) {
               int p = 0;
               Image img = data.getImage(cb.p(p).build());
               double pSize = img.getMetadata().getPixelSizeUm();
               double tmpXMinUm = img.getMetadata().getXPositionUm() - (0.5 * img.getWidth() * pSize);
               double tmpYMinUm = img.getMetadata().getYPositionUm() - (0.5 * img.getHeight() * pSize);
               
               int xMinPixel = (int) ((tmpXMinUm - xMinUm) / smallestPSize);
               int yMinPixel = (int) ((tmpYMinUm - yMinUm) / smallestPSize);
               if (bytesPerPixel == 1) {
                  GrayU8 tmp = new GrayU8(img.getWidth(), img.getHeight());
                  tmp.setData((byte[]) img.getRawPixels());
                  oldImgBoof = tmp;
               } else { // bytesPerPixel == 2
                  GrayU16 tmp = new GrayU16(img.getWidth(), img.getHeight());
                  tmp.setData((short[]) img.getRawPixels());
                  oldImgBoof = tmp;
               }
               GImageMiscOps.copy(0, 0, xMinPixel, yMinPixel, img.getWidth(), img.getHeight(),
                       oldImgBoof, newImgBoof);
            } else { // p > 1	
               for (int p = 0; p <= data.getMaxIndices().getP(); p++) {
                  Image img = data.getImage(cb.p(p).build());
                  if (bytesPerPixel == 1) {
                     GrayU8 tmp = new GrayU8(img.getWidth(), img.getHeight());
                     tmp.setData((byte[]) img.getRawPixels());
                     oldImgBoof = tmp;
                  } else { // bytesPerPixel == 2
                     GrayU16 tmp = new GrayU16(img.getWidth(), img.getHeight());
                     tmp.setData((short[]) img.getRawPixels());
                     oldImgBoof = tmp;
                  }
                  double diffX = centerXUm - img.getMetadata().getXPositionUm();
                  double diffY = centerYUm - img.getMetadata().getYPositionUm();
                  
                  Affine2D_F64 aff = confocalAff.copy();
                  
                  aff.tx = -(diffX);
                  aff.ty = -(diffY);
                  //centerXUm - img.getMetadata().getXPositionUm(),.0
                  //centerYUm - img.getMetadata().getYPositionUm());

                  aff = aff.concat(hamAffI, null);
                  FDistort fd = new FDistort();
                  fd.input(oldImgBoof);
                  fd.output(tmpImgBoof);
                  fd.affine(aff);
                  fd.interpNN();
                  fd.border(BorderType.ZERO);
                  fd.apply();
                  GPixelMath.add(newImgBoof, tmpImgBoof, tmp2ImgBoof);
                  GConvertImage.convert(tmp2ImgBoof, newImgBoof);
                  
               }
            }
            Image newImage = BoofCVImageConverter.boofCVToMM(newImgBoof,
                    cb.p(0).c(c).build(), newMetadataB.build());
            targetStore.putImage(newImage);
            c++;
         }
         
         DisplayWindow disp = studio.displays().createDisplay(targetStore);
         DisplaySettings dispSettings = disp.getDisplaySettings();
         DisplaySettings.Builder dpb = dispSettings.copyBuilder();
         
         DisplaySettings newDP = dpb.zoomRatio(zoom).colorModeComposite().
                 channel(0, dispSettings.getChannelSettings(0).copyBuilder().colorGreen().build()).
                 channel(1, dispSettings.getChannelSettings(1).copyBuilder().colorRed().build()).
                 build();
         disp.compareAndSetDisplaySettings(dispSettings, newDP);
         studio.displays().manage(targetStore);
         targetStore.freeze();
      } catch (IOException io2) {
         studio.logs().showError(io2);
      }
   }
}
