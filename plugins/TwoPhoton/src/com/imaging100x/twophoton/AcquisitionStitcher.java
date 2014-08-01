/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.imaging100x.twophoton;

import ij.ImageListener;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.TextRoi;
import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import mmcorej.MMCoreJ;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.MMStudio;
import org.micromanager.imagedisplay.VirtualAcquisitionDisplay;
import org.micromanager.api.ImageCache;
import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;



/**
 *
 * @author Henry
 */
public class AcquisitionStitcher {
   
   private static final String ACQ_NAME = "Stitched";
   
   private MMStudio gui_;
   private ImageCache cache_;
   private VirtualAcquisitionDisplay display_;
   private ImageWindow imageWindow_;

   private boolean invertX_, invertY_, swapXandY_;
   private boolean drawPosNames_, showGrid_;
   private int numCols_, numRows_;
   private int oldImageWidth_, oldImageHeight_, newImageWidth_ = -1, newImageHeight_ = -1;
   private double pixelSize_ = 0;
   private Comparator gridSorter_;
   private double stitchedWindowZoom_ = 1;
   private Point stitchedWindowLocation_ = null;
   private JSONArray posList_;

   
    public AcquisitionStitcher() {
       try {
           gui_ = MMStudio.getInstance();
           String camera = gui_.getCore().getCameraDevice();
           swapXandY_ = gui_.getCore().getProperty(camera, MMCoreJ.getG_Keyword_Transpose_SwapXY()).equals("1");
           invertX_ = gui_.getCore().getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorX()).equals("1");
           invertY_ = gui_.getCore().getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorY()).equals("1");
       } catch (Exception ex) {
           ReportingUtils.showError(ex.toString());
       }
    }

   public void setStitchParameters(boolean drawPosNames, boolean showGrid, VirtualAcquisitionDisplay display) {
      drawPosNames_ = drawPosNames;
      showGrid_ = showGrid;
      display_ = display;
      cache_ = display_.getImageCache();
   }

   public void createStitchedFromCurrentFrame() {
      try {
         JSONObject tags = cache_.getLastImageTags();
         int frameIndex = MDUtils.getFrameIndex(tags);
         int sliceIndex = MDUtils.getSliceIndex(tags);
         int channelIndex = MDUtils.getChannelIndex(tags);
         int positionIndex = MDUtils.getPositionIndex(tags);
         if (frameIndex == 0 && sliceIndex + 1 == display_.getNumSlices() && positionIndex + 1 == display_.getNumPositions()
                 && channelIndex + 1 == display_.getNumChannels()) {
            //first time point complete, do nothing
         } else if (frameIndex == 0) {
            //first time point incomplete, return
            return;
         } else {
            //use last complete time point
            frameIndex--;
         }


         if (gui_.acquisitionExists(ACQ_NAME)) {
            gui_.getAcquisition(ACQ_NAME).closeImageWindow();
         }
         gui_.openAcquisition(ACQ_NAME, "", 1, cache_.getNumDisplayChannels(), display_.getNumSlices(), true, false);


         for (int slice = 0; slice < display_.getNumSlices(); slice++) {
            for (int channel = 0; channel < cache_.getNumDisplayChannels(); channel++) {
               LinkedList<TaggedImage> batch = new LinkedList<TaggedImage>();
               for (int position = 0; position < display_.getNumPositions(); position++) {
                  batch.add(cache_.getImage(channel, slice, frameIndex, position));
               }
               if (channel == 0 && slice == 0) {
                  try {
                     calcGridDimensions(batch, cache_.getSummaryMetadata());

                     gui_.initializeAcquisition(ACQ_NAME, newImageWidth_, newImageHeight_, 1,8);
                     gui_.getAcquisition(ACQ_NAME).getAcquisitionWindow().promptToSave(false);
                     imageWindow_ = gui_.getAcquisition(ACQ_NAME).getAcquisitionWindow().getHyperImage().getWindow();


                     //add windowclosing listener to record zoom and position
                     if (stitchedWindowLocation_ == null) { //only need to add this listener once
                        gui_.getAcquisition(ACQ_NAME).getAcquisitionWindow().getImagePlus().addImageListener(new ImageListener() {

                           public void imageOpened(ImagePlus ip) {
                           }

                           public void imageUpdated(ImagePlus ip) {
                           }

                           public void imageClosed(ImagePlus ip) {
                              stitchedWindowLocation_ = imageWindow_.getLocation();
                              stitchedWindowZoom_ = imageWindow_.getCanvas().getMagnification();

                           }
                        });
                     }


                     try {

                        if (stitchedWindowLocation_ != null) {
                           ImageWindow win = gui_.getAcquisition(ACQ_NAME).getAcquisitionWindow().getImagePlus().getWindow();
                           win.setLocation(stitchedWindowLocation_);

                           //Apply same 
                           ImageCanvas canvas = win.getCanvas();
                           if (stitchedWindowZoom_ < canvas.getMagnification()) {
                              while (stitchedWindowZoom_ < canvas.getMagnification()) {
                                 canvas.zoomOut(canvas.getWidth() / 2, canvas.getHeight() / 2);
                              }
                           } else if (stitchedWindowZoom_ > canvas.getMagnification()) {
                              while (stitchedWindowZoom_ > canvas.getMagnification()) {
                                 canvas.zoomIn(canvas.getWidth() / 2, canvas.getHeight() / 2);
                              }
                           }
                        }


                     } catch (Exception e) {
                        ReportingUtils.showError("Couldnt re use stitched window settings");
                     }

                  } catch (JSONException ex) {
                     ReportingUtils.showError("Couldn't calc grid dimensions");
                  }
               }
               gui_.getAcquisition(ACQ_NAME).insertImage(stitchBatch(batch), 0, channel, slice);
               //Add overlay
               addPositionNameAndGridOverlay(batch);

            }
         }
         GUIUtils.invokeAndWait(new Runnable() {

            @Override
            public void run() {
               for (int c = 0; c < cache_.getNumDisplayChannels(); c++) {
                  try {
                     gui_.getAcquisition(ACQ_NAME).getAcquisitionWindow().setChannelContrast(c, cache_.getChannelMin(c),
                             cache_.getChannelMax(c), cache_.getChannelGamma(c));
                     gui_.getAcquisition(ACQ_NAME).setChannelColor(c, cache_.getChannelColor(c).getRGB());
                     gui_.getAcquisition(ACQ_NAME).setChannelName(c, cache_.getChannelName(c));
                  } catch (MMScriptException ex) {
                  }
               }
            }
         });


      } catch (Exception e) {
         ReportingUtils.showError(e);
      }

   }

   private Object stitchBatch(LinkedList<TaggedImage> batch) {
      if (gridSorter_ == null) {
         gridSorter_ = makeGridSorter();
      }
      Collections.sort(batch, gridSorter_);
      if (batch.getFirst().pix instanceof short[]) {
         JOptionPane.showMessageDialog(null, "Henry hasn't added support for stitching of images with more than "
                 + "8 bits per pixel because no one has needed it yet. Go talk to him and he will add this");
      }

      return stitchPixelsNoOverlap(batch);
   }

   private void addPositionNameAndGridOverlay(LinkedList<TaggedImage> batch) throws MMScriptException, JSONException {
      if (!drawPosNames_ && !showGrid_) {
         return;
      }
      Overlay overlay = new Overlay();
      if (drawPosNames_) {
         TextRoi.setFont(Font.SANS_SERIF, 30, Font.BOLD);
         TextRoi.setColor(Color.white);
         for (int x = 0; x < numCols_; x++) {
            for (int y = 0; y < numRows_; y++) {
               String posName = batch.get(x + y * numCols_).tags.getString("PositionName");
               TextRoi text = new TextRoi(newImageWidth_ / numCols_ * x + 0.4 * oldImageWidth_,
                       newImageHeight_ / numRows_ * y + 0.45 * oldImageHeight_, posName);
               overlay.add(text);
            }
         }
      }

      if (showGrid_) {
         //draw vertical lines
         for (int i = 1; i < numCols_; i++) {
            Line l = new Line(oldImageWidth_ * i, 0, oldImageWidth_ * i, newImageHeight_);
            overlay.add(l);
         }
         //draw horizontal lines
         for (int i = 1; i < numRows_; i++) {
            Line l = new Line(0, oldImageHeight_ * i, newImageWidth_, oldImageHeight_ * i);
            overlay.add(l);
         }

      }
      gui_.getAcquisition(ACQ_NAME).getAcquisitionWindow().getImagePlus().setOverlay(overlay);

   }

   private byte[] stitchPixelsNoOverlap(LinkedList<TaggedImage> batch) {
      byte[] newPixels = new byte[oldImageHeight_ * oldImageWidth_ * numCols_ * numRows_];
      for (int line = 0; line < (numCols_ * numRows_) * (oldImageHeight_); line++) {
         int gridX = line % numCols_;
         int gridY = (line / numCols_) / oldImageHeight_;
         int oldImageLineNumber = (line / numCols_) % oldImageHeight_;
         int tileIndex = gridY * numCols_ + gridX;
         if (batch.get(tileIndex).pix == null) {
            return null;
         }
         System.arraycopy(batch.get(tileIndex).pix, oldImageLineNumber * oldImageWidth_,
                 newPixels, line * oldImageWidth_, oldImageWidth_);
      }
      return newPixels;
   }

    private void calcGridDimensions(LinkedList<TaggedImage> batch, JSONObject summaryMD) throws JSONException {
        numCols_ = 0;
        numRows_ = 0;
        oldImageWidth_ = MDUtils.getWidth(batch.getFirst().tags);
        oldImageHeight_ = MDUtils.getHeight(batch.getFirst().tags);
        pixelSize_ = batch.getFirst().tags.getDouble("PixelSizeUm");
        try {
            posList_ = summaryMD.getJSONArray("InitialPositionList");
           //get grid parameters
           for (int i = 0; i < posList_.length(); i++) {
              long colInd = posList_.getJSONObject(i).getLong("GridColumnIndex");
              long rowInd = posList_.getJSONObject(i).getLong("GridRowIndex");
              if (colInd >= numCols_) {
                 numCols_ = (int) (colInd + 1);
              }
              if (rowInd >= numRows_) {
                 numRows_ = (int) (rowInd + 1);
              }
           }
            
//            if (swapXandY_) {
//                int temp = numRows_;
//                numRows_ = numCols_;
//                numCols_ = temp;
//            }
        } catch (Exception ex) {
            ReportingUtils.showError("Couldn't get grid size position list");
        }
        newImageWidth_ = numCols_ * oldImageWidth_;
        newImageHeight_ = numRows_ * oldImageHeight_;
    }

    //sort left to right, top to bottom
    private Comparator<TaggedImage> makeGridSorter() {
        return new Comparator<TaggedImage>() {
            @Override
            public int compare(TaggedImage img1, TaggedImage img2) {
                try {
                    int pos1 = MDUtils.getPositionIndex(img1.tags);
                    int pos2 = MDUtils.getPositionIndex(img2.tags);

                    int row1 = (int) posList_.getJSONObject(pos1).getLong("GridRowIndex");
                    int row2 = (int) posList_.getJSONObject(pos2).getLong("GridRowIndex");
                    int col1 = (int) posList_.getJSONObject(pos1).getLong("GridColumnIndex");
                    int col2 = (int) posList_.getJSONObject(pos2).getLong("GridColumnIndex");

                    if (row1 != row2) {
                        return row1 - row2;
                    }
                    return col1 - col2;

                } catch (JSONException ex) {
                    ReportingUtils.showError("couldnt sort tile coordinates");
                    return 0;
                }
            }
        };
    }

    
//    private Comparator<TaggedImage> makeGridSorter() {
//      return new Comparator<TaggedImage>() {
//
//         @Override
//         public int compare(TaggedImage img1, TaggedImage img2) {
//
//            if (swapXandY_) {
//               try {
//                  double x1 = img1.tags.getDouble("XPositionUm");
//                  double x2 = img2.tags.getDouble("XPositionUm");
//                  if ( Math.abs( (x1-x2)/pixelSize_ / (double) oldImageHeight_ ) > 0.5 ) {                   
//                     return (int) (invertX_ ? (x1 - x2) : (x2 - x1));
//                  }
//                  double y1 = img1.tags.getDouble("YPositionUm");
//                  double y2 = img2.tags.getDouble("YPositionUm");
//                  if (Math.abs( (y1-y2)/pixelSize_ / (double) oldImageWidth_ ) > 0.5 ) {
//                     return (int) (invertY_ ? (y1 - y2) : (y2 - y1));
//                  }
//               } catch (JSONException ex) {
//                  ReportingUtils.showError("Couldn't find stage coordinates");
//               }
//            } else {
//               try {
//                  double y1 = img1.tags.getDouble("YPositionUm");
//                  double y2 = img2.tags.getDouble("YPositionUm");
//                  if (Math.abs( (y1-y2)/pixelSize_ / (double) oldImageHeight_ ) > 0.5) {
//                     return (int) (invertY_ ? (y1 - y2) : (y2 - y1));
//                  }
//                  double x1 = img1.tags.getDouble("XPositionUm");
//                  double x2 = img2.tags.getDouble("XPositionUm");
//                  if (Math.abs( (x1-x2)/pixelSize_ / (double) oldImageWidth_ ) > 0.5) {
//                     return (int) (invertX_ ? (x1 - x2) : (x2 - x1));
//                  }
//               } catch (JSONException ex) {
//                  ReportingUtils.showError("Couldn't find stage coordinates");
//               }
//            }
//            return 0;
//         }
//      };
//   }
}
