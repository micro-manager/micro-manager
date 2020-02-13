package org.micromanager.assembledata;

import boofcv.abst.distort.FDistort;
import boofcv.alg.misc.GImageMiscOps;
import boofcv.alg.misc.GPixelMath;
import boofcv.core.image.GConvertImage;
import boofcv.struct.border.BorderType;
import boofcv.struct.image.GrayS32;
import boofcv.struct.image.GrayU16;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageGray;
import georegression.struct.affine.Affine2D_F64;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.micromanager.Studio;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreRewriteException;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.internal.utils.imageanalysis.BoofCVImageConverter;

/**
 *
 * @author nico
 */
public class AssembleDataAlgo {

   public static Datastore assemble(Studio studio, 
           AssembleDataForm form, 
           Datastore output, 
           DataProvider dp1, 
           DataProvider dp2, int xOffset, 
           int yOffset, 
           int targetPosition, 
           boolean test) {

      DataProvider spd = Utils.singlePositionData(dp1, dp2);
      DataProvider mpd = Utils.multiPositionData(dp1, dp2);
      
      try {
         SummaryMetadata.Builder smb = spd.getSummaryMetadata().copyBuilder();
         List<String> channelNames = new ArrayList<>();
         channelNames.addAll(spd.getSummaryMetadata().getChannelNameList());
         channelNames.addAll(mpd.getSummaryMetadata().getChannelNameList());
         output.setSummaryMetadata(smb.channelNames(channelNames).build());
      } catch (IOException | DatastoreRewriteException ex) {}
   

      try {
         Image singlePositionImg = spd.getAnyImage();
         Image multiPositionImg = mpd.getAnyImage();
         Metadata singlePositionMD = singlePositionImg.getMetadata();
         Metadata multiPositionMD = multiPositionImg.getMetadata();
         double singlePositionPixelSize = singlePositionMD.getPixelSizeUm();
         double multiPositionPixelSize = multiPositionMD.getPixelSizeUm();
         AffineTransform singlePositionAff = singlePositionMD.getPixelSizeAffine();
         AffineTransform multiPositionAff = multiPositionMD.getPixelSizeAffine();
         double basePixelSize = singlePositionPixelSize;
         if (multiPositionPixelSize < basePixelSize) {
            basePixelSize = multiPositionPixelSize;
         }

         Affine2D_F64 singlePositionAf64 = BoofCVImageConverter.convertAff(singlePositionAff);
         Affine2D_F64 singlePositionAf64I = singlePositionAf64.invert(null);
         Affine2D_F64 multiPositionAf64 = BoofCVImageConverter.convertAff(multiPositionAff);

         int bytesPerPixel = singlePositionImg.getBytesPerPixel();
         if (multiPositionImg.getBytesPerPixel() != bytesPerPixel) {
            // mm.scripter().message("Images differ in bytes per pixel");
            return null;
         }

         DataProvider[] datas = {dp1, dp2};

         Coords.Builder cb = Coordinates.builder().t(0).c(0).p(targetPosition).z(0);
         // need to initialize these parameters with something sensible
         double xMinUm = singlePositionImg.getMetadata().getXPositionUm() - (0.5 * singlePositionImg.getWidth() * singlePositionPixelSize);
         double yMinUm = singlePositionImg.getMetadata().getYPositionUm() - (0.5 * singlePositionImg.getHeight() * singlePositionPixelSize);
         double xMaxUm = singlePositionImg.getMetadata().getXPositionUm() + (0.5 * singlePositionImg.getWidth() * singlePositionPixelSize);
         double yMaxUm = singlePositionImg.getMetadata().getYPositionUm() + (0.5 * singlePositionImg.getHeight() * singlePositionPixelSize);

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

         /*  Be Warned!
             reading new image size from metdata is the right size to do, but 
             leads to slightly different sizes for consecutive positions.
             MM deals very poorly with images of different sizes (i.e., currently
             silently accepts them but causes lots of problems downstream)
         */
         // int widthPixels = (int) (widthUm / basePixelSize) + 1;
         // int heightPixels = (int) (heightUm / basePixelSize) + 1;
         int widthPixels = spd.getAnyImage().getWidth();
         int heightPixels = spd.getAnyImage().getHeight();
         
         // Not sure why, but it looks like the image will end up at the origin
         // rather then the center unless we set this translation to the center
         // of the target image.   
         singlePositionAf64I.tx = widthPixels / 2.0 + xOffset;
         singlePositionAf64I.ty = heightPixels / 2.0 + yOffset;

         //for (DataProvider data : datas) {
         cb.t(0).c(0).p(0).z(0);
         ImageGray newImgBoof, oldImgBoof, tmpImgBoof, tmp2ImgBoof;
         if (bytesPerPixel == 1) {
            newImgBoof = new GrayU8(widthPixels, heightPixels);
            tmpImgBoof = new GrayU8(widthPixels, heightPixels);
            tmp2ImgBoof = new GrayU16(widthPixels, heightPixels);
         } else { // bytesPerPixel == 2
            newImgBoof = new GrayU16(widthPixels, heightPixels);
            tmpImgBoof = new GrayU16(widthPixels, heightPixels);
            tmp2ImgBoof = new GrayS32(widthPixels, heightPixels);
         }

         // single position data
         final int spdTLength = test ? 1 : spd.getAxisLength(Coords.T);
         final int spdCLength = test ? 1 : spd.getAxisLength(Coords.C);
         Metadata.Builder newMetadataB;
         for (int t = 0; t < spdTLength; t++) {
            for (int c = 0; c < spdCLength; c++) {
               cb.t(t).c(c).p(0).z(0);
               Image img = spd.getImage(cb.p(0).build());
               if (img != null) {
                  newMetadataB = img.getMetadata().
                          copyBuilderWithNewUUID().pixelSizeUm(basePixelSize);
                  /*
                  TODO: use stage position informatoin to correct for inaccuracies
                  this will currently cause errors in the GImageMiscOps.copy step
                  double pSize = img.getMetadata().getPixelSizeUm();
                  double tmpXMinUm = img.getMetadata().getXPositionUm() - (0.5 * img.getWidth() * pSize);
                  double tmpYMinUm = img.getMetadata().getYPositionUm() - (0.5 * img.getHeight() * pSize);

                  int xMinPixel = (int) ((tmpXMinUm - xMinUm) / basePixelSize);
                  int yMinPixel = (int) ((tmpYMinUm - yMinUm) / basePixelSize);
                   */
                  if (bytesPerPixel == 1) {
                     GrayU8 tmp = new GrayU8(img.getWidth(), img.getHeight());
                     tmp.setData((byte[]) img.getRawPixels());
                     oldImgBoof = tmp;
                  } else { // bytesPerPixel == 2
                     GrayU16 tmp = new GrayU16(img.getWidth(), img.getHeight());
                     tmp.setData((short[]) img.getRawPixels());
                     oldImgBoof = tmp;
                  }
                  //GImageMiscOps.copy(0, 0, xMinPixel, yMinPixel, img.getWidth(), img.getHeight(),
                  //        oldImgBoof, newImgBoof);
                  GImageMiscOps.copy(0, 0, 0, 0, img.getWidth(), img.getHeight(),
                          oldImgBoof, newImgBoof);
                  Coords coords = cb.p(targetPosition).c(c).t(t).build();
                  System.out.println(coords.toString());
                  newMetadataB.positionName("Site-" + targetPosition);
                  Image newImage = BoofCVImageConverter.boofCVToMM(newImgBoof,
                          cb.p(targetPosition).c(c).t(t).build(), newMetadataB.build());
                  output.putImage(newImage);
                  GImageMiscOps.fill(newImgBoof, 0.0);
               }
            }
            int progress = (int) (50.0 * t / spd.getAxisLength(Coords.T));
            form.setStatus(" " + progress + "%");
         }

         // multi position data
         final int mpdTLength = test ? 1 : mpd.getAxisLength(Coords.T);
         final int mpdCLenghth = test ? 1 : mpd.getAxisLength(Coords.C);
         for (int t = 0; t < mpdTLength; t++) {
            for (int c = 0; c < mpdCLenghth; c++) {
               newMetadataB = null;
               for (int p = 0; p <= mpd.getMaxIndices().getP(); p++) {
                  Image img = mpd.getImage(cb.c(c).t(t).p(p).build());
                  if (img != null) {
                     newMetadataB = img.getMetadata().
                             copyBuilderWithNewUUID().pixelSizeUm(basePixelSize);
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

                     Affine2D_F64 aff = multiPositionAf64.copy();

                     aff.tx = -(diffX);
                     aff.ty = -(diffY);
                     //centerXUm - img.getMetadata().getXPositionUm(),.0
                     //centerYUm - img.getMetadata().getYPositionUm());

                     aff = aff.concat(singlePositionAf64I, null);
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
               if (newMetadataB != null) {
                  Coords coords = cb.p(targetPosition).c(c + spdCLength).t(t).build();
                  System.out.println(coords.toString());                  
                  newMetadataB.positionName("Site-" + targetPosition);
                  Image newImage = BoofCVImageConverter.boofCVToMM(newImgBoof, 
                          coords, newMetadataB.build());
                  output.putImage(newImage);
                  GImageMiscOps.fill(newImgBoof, 0.0);
               }
            }
            int progress = (int) (50.0 + 50.0 * t / spdTLength);
            form.setStatus(" " + progress + "%");
         }

         return output;

      } catch (IOException io2) {
         studio.logs().showError(io2);
      }

      return null;
   }

}
