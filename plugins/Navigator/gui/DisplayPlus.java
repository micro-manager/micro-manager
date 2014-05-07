package gui;

/*
 * Master stitched window to display real time stitched images, allow navigating of XY more easily
 */
import acq.CustomAcqEngine;
import acq.DynamicStitchingImageStorage;
import ij.CompositeImage;
import ij.IJ;
import ij.gui.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.text.ParseException;
import java.util.ArrayList;
import javax.swing.*;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.imageDisplay.VirtualAcquisitionDisplay;
import org.micromanager.api.ImageCache;


import org.micromanager.internalinterfaces.DisplayControls;
import org.micromanager.utils.*;

public class DisplayPlus extends VirtualAcquisitionDisplay  {

   private static final Color TRANSPARENT_BLUE = new Color(0, 0, 255, 100);
   private static final Color TRANSPARENT_MAGENTA = new Color(255, 0, 255, 100);
   private DynamicStitchingImageStorage storage_;
   private ImageCanvas canvas_;
   private Controls controls_;
   private CustomAcqEngine eng_;
   private JSpinner gridXSpinner_, gridYSpinner_;
   private Point clickStart_;
   private Point gridStart_, mouseDragStartPoint_;
   private boolean suspendUpdates_ = false;
   private int mouseRowIndex_ = -1, mouseColIndex_ = -1;
   private ArrayList<Point> selectedPositions_ = new ArrayList<Point>();
   private ScrollbarWithLabel tSelector_;
   private boolean gotoMode_ = false, newGridMode_ = false,
           zoomMode_ = false, zoomAreaSelectMode_ = false;
   private ZoomableVirtualStack zoomableStack_;
   private final boolean exploreMode_;

   public DisplayPlus(final ImageCache stitchedCache, CustomAcqEngine eng, JSONObject summaryMD, 
           DynamicStitchingImageStorage storage, boolean exploreMode) {
      super(stitchedCache, null, "test");
      
      exploreMode_ = exploreMode;
      //Set parameters for tile dimensions, num rows and columns, overlap, and image dimensions
      eng_ = eng;

//      String name = "Untitled";
//      try {
//         String pre = summaryMD.getString("Prefix");
//         if (pre != null && pre.length() > 0) {
//            name = pre;
//         }
//      } catch (Exception e) {
//      }
      
      storage_ = storage;
      controls_ = new Controls();

      //Add in custom controls
      try {
         JavaUtils.setRestrictedFieldValue(this, VirtualAcquisitionDisplay.class, "controls_", controls_);
      } catch (NoSuchFieldException ex) {
         ReportingUtils.showError("Couldn't create display controls");
      }

      //add in customized zoomable acquisition virtual stack
      try {
         int nSlices = MDUtils.getNumChannels(summaryMD) * MDUtils.getNumFrames(summaryMD) * MDUtils.getNumSlices(summaryMD);
         zoomableStack_ = new ZoomableVirtualStack(MDUtils.getIJType(summaryMD), stitchedCache, nSlices, this, storage);
         this.show(zoomableStack_);
      } catch (Exception e) {
         ReportingUtils.showError("Problem with initialization due to missing summary metadata tags");
         return;
      }

      try {
         //get reference to tSelector so it can be updated without showing latest images         
         tSelector_ = (ScrollbarWithLabel) JavaUtils.getRestrictedFieldValue(
                 this, VirtualAcquisitionDisplay.class, "tSelector_");
      } catch (NoSuchFieldException ex) {
         ReportingUtils.showError("Couldnt get refernce to t Selctor");
      }
      
      canvas_ = this.getImagePlus().getCanvas();
      
      //Zoom to 100%
      canvas_.unzoom();

      setupMouseListeners();

      IJ.setTool(Toolbar.SPARE6);

      stitchedCache.addImageCacheListener(this);
   }
//
//   public void readGridInfoFromPositionList(JSONArray posList) {
//      try {
//         //get grid parameters
//         for (int i = 0; i < posList.length(); i++) {
//            long colInd = posList.getJSONObject(i).getLong("GridColumnIndex");
//            long rowInd = posList.getJSONObject(i).getLong("GridRowIndex");
//            if (colInd >= numCols_) {
//               numCols_ = (int) (colInd + 1);
//            }
//            if (rowInd >= numRows_) {
//               numRows_ = (int) (rowInd + 1);
//            }
//
//         }
//      } catch (Exception e) {
//         ReportingUtils.showError("Couldnt get grid info");
//      }
//      fullResHeight_ = numRows_ * tileHeight_ - (numRows_ - 1) * yOverlap_;
//      fullResWidth_ = numCols_ * tileWidth_ - (numCols_ - 1) * xOverlap_;
//   }

    
   private void drawZoomIndicatorOverlay() {
      //draw zoom indicator
      Overlay overlay = new Overlay();
      Point zoomPos = zoomableStack_.getZoomPosition();      
      int outerWidth = 100;
      int outerHeight = (int) ((double) storage_.getFullResHeight() / (double) storage_.getFullResWidth() * outerWidth);
      //draw outer rectangle representing full image
      Roi outerRect = new Roi(10, 10, outerWidth, outerHeight);
      outerRect.setStrokeColor(new Color(255, 0, 255)); //magenta
      overlay.add(outerRect);
      int innerX = (int) Math.round(( (double) outerWidth / (double) storage_.getFullResWidth() ) * zoomPos.x);
      int innerY = (int) Math.round(( (double) outerHeight / (double) storage_.getFullResHeight() ) * zoomPos.y);
      int innerWidth = (int) Math.round(((double) outerWidth / (double) storage_.getFullResWidth() ) * 
              (storage_.getFullResWidth() / storage_.getDSFactor()));
      int innerHeight = (int) Math.round(((double) outerHeight / (double) storage_.getFullResHeight() ) * 
              (storage_.getFullResHeight() / storage_.getDSFactor()));
      Roi innerRect = new Roi(10+innerX,10+innerY,innerWidth,innerHeight );
      innerRect.setStrokeColor(new Color(255, 0, 255)); 
      overlay.add(innerRect);
      canvas_.setOverlay(overlay);
   }

   private void zoomIn(Point p) {
      zoomMode_ = true;
      zoomAreaSelectMode_ = false;
      //This assumes 100% display of tiled image
      zoomableStack_.activateZoomMode(p.x, p.y);
      this.getHyperImage().setOverlay(null);
      redrawPixels();
      drawZoomIndicatorOverlay();   
   }
   
   private void zoomOut() {
      zoomMode_ = false;
      zoomableStack_.activateFullImageMode();
      redrawPixels();
      canvas_.setOverlay(null);
   }

   //TODO account for overlap?
   private void highlightTiles(int row1, int row2, int col1, int col2, Color color) {
      //need to convert from canvas coordinates to pixel coordinates
      final ImageCanvas canvas = this.getImagePlus().getCanvas();
       double tileHeight = storage_.getTileHeight() / (double) storage_.getDSFactor(),
               tileWidth = storage_.getTileWidth() / (double) storage_.getDSFactor();
       int x = (int) Math.round(col1 * tileWidth);
       int y = (int) Math.round(row1 * tileHeight);
       int width = (int) Math.round(tileWidth * (col2 - col1 + 1));
       int height = (int) Math.round(tileHeight * (row2 - row1 + 1));
      Roi rect = new Roi(x, y, width, height);
      rect.setFillColor(color);
      Overlay overlay = new Overlay();
      overlay.add(rect);
      canvas.setOverlay(overlay);
   }
   
   private void setupMouseListeners() {  
      //remove channel switching scroll wheel listener
      this.getImagePlus().getWindow().removeMouseWheelListener(
              this.getImagePlus().getWindow().getMouseWheelListeners()[0]);
      //remove canvas mouse listener and virtualacquisitiondisplay as mouse listener
      this.getImagePlus().getCanvas().removeMouseListener(
              this.getImagePlus().getCanvas().getMouseListeners()[0]);
      this.getImagePlus().getCanvas().removeMouseListener(
              this.getImagePlus().getCanvas().getMouseListeners()[0]);
      
      canvas_.addMouseMotionListener(new MouseMotionListener() {

         @Override
         public void mouseDragged(MouseEvent e) {               
            Point currentPoint = e.getPoint();
            if (newGridMode_) {
               int dx = (int) ((currentPoint.x - clickStart_.x) / canvas_.getMagnification());
               int dy = (int) ((currentPoint.y - clickStart_.y) / canvas_.getMagnification());
               DisplayPlus.this.getImagePlus().getOverlay().get(0).setLocation(gridStart_.x + dx, gridStart_.y + dy);
               if (!CanvasPaintPending.isMyPaintPending(canvas_, this)) {
                  canvas_.setPaintPending(true);
                  canvas_.paint(canvas_.getGraphics());
               }
            } else if  (zoomMode_ && e.isShiftDown()) {
               zoomableStack_.translateZoomPosition(mouseDragStartPoint_.x - currentPoint.x, 
                       mouseDragStartPoint_.y - currentPoint.y);
               redrawPixels();
               mouseDragStartPoint_ = currentPoint;
               drawZoomIndicatorOverlay();               
            } else if (exploreMode_ && !zoomMode_) {
               Point p2 = e.getPoint();
               //find top left row and column and number of columns spanned by drage event
               Point topLeftTile = storage_.tileIndicesFromLoResPixel(Math.min(p2.x, mouseDragStartPoint_.x),
                       Math.min(p2.y, mouseDragStartPoint_.y));
               Point bottomRightTile = storage_.tileIndicesFromLoResPixel(Math.max(p2.x, mouseDragStartPoint_.x),
                       Math.max(p2.y, mouseDragStartPoint_.y));
               highlightTiles(topLeftTile.x, bottomRightTile.x, topLeftTile.y, bottomRightTile.y, TRANSPARENT_MAGENTA);              
            }
         }

         @Override
         public void mouseMoved(MouseEvent e) {
            if (zoomAreaSelectMode_) {
               //draw rectangle of area that will be zoomed in on
               Overlay overlay = new Overlay();
               int width = (int) Math.round(zoomableStack_.getWidth() / storage_.getDSFactor());
               int height = (int) Math.round(zoomableStack_.getHeight() / storage_.getDSFactor());
               Point center = e.getPoint();
               Roi rect = new Roi(Math.min(Math.max(0,center.x - width / 2), canvas_.getWidth() - width),
                       Math.min(Math.max(0,center.y - height / 2), canvas_.getHeight() - height),
                       width, height);
               rect.setFillColor(TRANSPARENT_BLUE);
               overlay.add(rect);
               canvas_.setOverlay(overlay);
            } else if (exploreMode_ && !zoomMode_ && !newGridMode_) {
               Point coords = storage_.tileIndicesFromLoResPixel(e.getPoint().x, e.getPoint().y);
               int row = coords.x, col = coords.y;
               //highlight tile(s)
               highlightTiles(row, row, col, col, TRANSPARENT_MAGENTA);
            }
         }
      });

      canvas_.addMouseListener(new MouseListener() {

         @Override
         public void mouseClicked(MouseEvent e) {
            if (gotoMode_) {
//               //translate point into stage coordinates and move there
//               Point p = e.getPoint();
//               double xPixelDisp = (p.x / canvas_.getMagnification())
//                       + canvas_.getSrcRect().x - vad_.getImagePlus().getWidth() / 2;
//               double yPixelDisp = (p.y / canvas_.getMagnification())
//                       + canvas_.getSrcRect().y - vad_.getImagePlus().getHeight() / 2;
//
//               Point2D stagePos = stagePositionFromPixelPosition(xPixelDisp, yPixelDisp);
//               try {
//                  MMStudioMainFrame.getInstance().setXYStagePosition(stagePos.getX(), stagePos.getY());
//               } catch (MMScriptException ex) {
//                  ReportingUtils.showError("Couldn't move xy stage");
//               }
//               controls_.clearSelectedButtons();
            } else if (zoomAreaSelectMode_) {
               zoomIn(e.getPoint());
            } else {
               if (e.getClickCount() > 1 && e.isShiftDown()) {
                  if (zoomMode_) {
                     zoomOut();
                  } else {
                     zoomIn(e.getPoint());
                  }
               }  
            }
         }

         @Override
         public void mousePressed(MouseEvent e) {
            if (newGridMode_) {
               clickStart_ = e.getPoint();
               Roi rect = DisplayPlus.this.getImagePlus().getOverlay().get(0);
               Rectangle2D bounds = rect.getFloatBounds();
               gridStart_ = new Point((int) bounds.getX(), (int) bounds.getY());
            } else if (zoomMode_ || exploreMode_) {
               mouseDragStartPoint_ = e.getPoint();
            } 
         }

         @Override
         public void mouseReleased(MouseEvent e) {
            if (exploreMode_ && !e.isShiftDown() && !newGridMode_ && !zoomMode_) {

               Point p2 = e.getPoint();
               //find top left row and column and number of columns spanned by drage event
               final Point topLeftTile = storage_.tileIndicesFromLoResPixel(Math.min(p2.x, mouseDragStartPoint_.x),
                       Math.min(p2.y, mouseDragStartPoint_.y));
               final Point bottomRightTile = storage_.tileIndicesFromLoResPixel(Math.max(p2.x, mouseDragStartPoint_.x),
                       Math.max(p2.y, mouseDragStartPoint_.y));
               //create events to acquire one or more tiles
               eng_.acquireTiles(topLeftTile.x, topLeftTile.y, bottomRightTile.x, bottomRightTile.y);

               //redraw overlay to potentially reflect new grid   
               Point coords = storage_.tileIndicesFromLoResPixel(e.getPoint().x, e.getPoint().y);
               int row = coords.x, col = coords.y;
               //highlight tile(s)
               highlightTiles(row, row, col, col, TRANSPARENT_MAGENTA);
            }
         }

         @Override
         public void mouseEntered(MouseEvent e) {
         }

         @Override
         public void mouseExited(MouseEvent e) {
            if (!gotoMode_) {
               return;
            }
            mouseRowIndex_ = -1;
            mouseColIndex_ = -1;
         }
      });
   }

   private void createGrid() {
//      try {
//         //get displacements of center of rectangle from center of stitched image
//         double rectCenterXDisp = this.getImagePlus().getOverlay().get(0).getFloatBounds().getCenterX()
//                 - this.getImagePlus().getWidth() / 2;
//         double rectCenterYDisp = this.getImagePlus().getOverlay().get(0).getFloatBounds().getCenterY()
//                 - this.getImagePlus().getHeight() / 2;
//
//         Point2D.Double stagePos = Util.stagePositionFromPixelPosition(rectCenterXDisp, rectCenterYDisp);
//
////         int xOverlap = SettingsDialog.getXOverlap(), yOverlap = SettingsDialog.getYOverlap();
//         int xOverlap = 0, yOverlap = 0;
//         Util.createGrid(stagePos.x, stagePos.y,
//                 (Integer) gridXSpinner_.getValue(), (Integer) gridYSpinner_.getValue(),
//                 xOverlap, yOverlap);
//
//      } catch (Exception e) {
//         ReportingUtils.showError("Couldnt create grid");
//      }
   }

   private void makeGridOverlay(int centerX, int centerY, int rows, int cols, int xOverlap, int yOverlap) {
      Overlay overlay = this.getImagePlus().getOverlay();
      if (overlay == null || overlay.size() == 0) {
         overlay = new Overlay();
      } else {
         overlay.clear();
      }

      double dsTileWidth = storage_.getTileWidth() / (double) storage_.getDSFactor();
      double dsTileHeight = storage_.getTileHeight() / (double) storage_.getDSFactor();
      int roiWidth = (int) ((cols * dsTileWidth) - ((cols - 1) * xOverlap) / storage_.getDSFactor());
      int roiHeight = (int) ((rows * dsTileHeight) - ((rows - 1) * yOverlap) / storage_.getDSFactor());

      Roi rectangle = new Roi(centerX - roiWidth / 2, centerY - roiHeight / 2, roiWidth, roiHeight);
      rectangle.setStrokeWidth(10f);
      overlay.add(rectangle);
      this.getImagePlus().setOverlay(overlay);
   }

   public void resizeGrid(int rows, int cols, int xOverlap, int yOverlap) {
      //resize exisiting grid but keep centered on same area
      Overlay overlay = this.getImagePlus().getOverlay();
      if (overlay == null || overlay.get(0) == null) {
         return;
      }
      Rectangle2D oldBounds = overlay.get(0).getFloatBounds();
      int centerX = (int) oldBounds.getCenterX();
      int centerY = (int) oldBounds.getCenterY();
      makeGridOverlay(centerX, centerY, rows, cols, xOverlap, yOverlap);
   }
   
   public void newGrid(boolean selected, int rows, int cols, int xOverlap, int yOverlap) {
      if (selected) {
         newGridMode_ = true;
         makeGridOverlay(this.getImagePlus().getWidth() / 2, this.getImagePlus().getHeight() / 2,
                 rows, cols, xOverlap, yOverlap);
      } else {
         newGridMode_ = false;
         this.getImagePlus().setOverlay(null);
         canvas_.repaint();
      }
   }

   @Override
   public void imageReceived(TaggedImage taggedImage) {
      try {
         //duplicate so image storage doesnt see incorrect tags
         JSONObject newTags = new JSONObject(taggedImage.tags.toString());
         MDUtils.setPositionIndex(newTags, 0);
         taggedImage = new TaggedImage(taggedImage.pix, newTags);
      } catch (JSONException ex) {
         ReportingUtils.showError("Couldn't manipulate image tags for display");
      }

      if (!suspendUpdates_) {
         super.imageReceived(taggedImage);
      } else {
         try {
            //tSelector will be null on first frame
            if (tSelector_ != null) {
               int frame = MDUtils.getFrameIndex(taggedImage.tags);
               if (tSelector_.getMaximum() <= (1 + frame)) {
                  ((VirtualAcquisitionDisplay.IMMImagePlus) this.getHyperImage()).setNFramesUnverified(frame + 1);
                  tSelector_.setMaximum(frame + 2);
                  tSelector_.invalidate();
                  tSelector_.validate();
               }
            }
         } catch (Exception ex) {
            ReportingUtils.showError("Couldn't suspend updates");
         }
      }

   }

   private void redrawPixels() {
      if (!this.getHyperImage().isComposite()) {
         int index = this.getHyperImage().getCurrentSlice();
         Object pixels = zoomableStack_.getPixels(index);
         this.getHyperImage().getProcessor().setPixels(pixels);
      } else {
         CompositeImage ci = (CompositeImage) this.getHyperImage();
         if (ci.getMode() == CompositeImage.COMPOSITE) {
            for (int i = 0; i < ((VirtualAcquisitionDisplay.MMCompositeImage) ci).getNChannelsUnverified(); i++) {
               //Dont need to set pixels if processor is null because it will get them from stack automatically  
               Object pixels = zoomableStack_.getPixels(ci.getCurrentSlice() - ci.getChannel() + i + 1);
               if (ci.getProcessor(i + 1) != null && pixels != null) {
                  ci.getProcessor(i + 1).setPixels(pixels);
               }
            }
         }
         Object pixels = zoomableStack_.getPixels(this.getHyperImage().getCurrentSlice());
         if (pixels != null) {
            ci.getProcessor().setPixels(pixels);
         }
      }
      if (CanvasPaintPending.isMyPaintPending(canvas_, this)) {
         return;
      }
      CanvasPaintPending.setPaintPending(canvas_, this);
      this.updateAndDraw(true);
   }

   class Controls extends DisplayControls {

      private JButton pauseButton_, abortButton_;
      private JTextField fpsField_;
      private JLabel zPosLabel_, timeStampLabel_, nextFrameLabel_, posNameLabel_;
      private JToggleButton gotoButton_, suspendUpdatesButton_;
              
      private Timer nextFrameTimer_;

      public Controls() {
         initComponents();
         nextFrameTimer_ = new Timer(1000, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
               long nextImageTime = 0;
               try {
                  nextImageTime = DisplayPlus.this.getNextWakeTime();
               } catch (NullPointerException ex) {
                  nextFrameTimer_.stop();
               }
               if (!DisplayPlus.this.acquisitionIsRunning()) {
                  nextFrameTimer_.stop();
               }
               double timeRemainingS = (nextImageTime - System.nanoTime() / 1000000) / 1000;
               if (timeRemainingS > 0 && DisplayPlus.this.acquisitionIsRunning()) {
                  nextFrameLabel_.setText("Next frame: " + NumberUtils.doubleToDisplayString(1 + timeRemainingS) + " s");
                  nextFrameTimer_.setDelay(100);
               } else {
                  nextFrameTimer_.setDelay(1000);
                  nextFrameLabel_.setText("");
               }

            }
         });
         nextFrameTimer_.start();
      }



      private void gotoButtonAction() {
         if (gotoButton_.isSelected()) {
            gotoButton_.setSelected(true);
            gotoMode_ = true;
            canvas_.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR), 0);
         } else {
            gotoMode_ = false;
            canvas_.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR), 0);
         }
      }


      public void acquiringImagesUpdate(boolean state) {
         abortButton_.setEnabled(state);
         pauseButton_.setEnabled(state);
      }

      private void updateFPS() {
         try {
            double fps = NumberUtils.displayStringToDouble(fpsField_.getText());
            DisplayPlus.this.setPlaybackFPS(fps);
         } catch (ParseException ex) {
         }
      }

      public void updateSelectedPosition(String posName) {
         posNameLabel_.setText(posName);
      }

      @Override
      public void imagesOnDiskUpdate(boolean bln) {
//         abortButton_.setEnabled(bln);
//         pauseButton_.setEnabled(bln);
      }

      @Override
      public void setStatusLabel(String string) {
      }

      private void updateLabels(JSONObject tags) {
         //Z position label
         String zPosition = "";
         try {
            zPosition = NumberUtils.doubleStringCoreToDisplay(tags.getString("ZPositionUm"));
         } catch (Exception e) {
            try {
               zPosition = NumberUtils.doubleStringCoreToDisplay(tags.getString("Z-um"));
            } catch (Exception e1) {
               // Do nothing...
            }
         }
         zPosLabel_.setText("Z Position: " + zPosition + " um ");

         //time label
         try {
            int ms = (int) tags.getDouble("ElapsedTime-ms");
            int s = ms / 1000;
            int min = s / 60;
            int h = min / 60;

            String time = twoDigitFormat(h) + ":" + twoDigitFormat(min % 60)
                    + ":" + twoDigitFormat(s % 60) + "." + threeDigitFormat(ms % 1000);
            timeStampLabel_.setText("Elapsed time: " + time + " ");
         } catch (JSONException ex) {
//            ReportingUtils.logError("MetaData did not contain ElapsedTime-ms field");
         }
      }

      private String twoDigitFormat(int i) {
         String ret = i + "";
         if (ret.length() == 1) {
            ret = "0" + ret;
         }
         return ret;
      }

      private String threeDigitFormat(int i) {
         String ret = i + "";
         if (ret.length() == 1) {
            ret = "00" + ret;
         } else if (ret.length() == 2) {
            ret = "0" + ret;
         }
         return ret;
      }

      @Override
      public void newImageUpdate(JSONObject tags) {
         if (tags == null) {
            return;
         }
         updateLabels(tags);
      }



      private void initComponents() {
         setPreferredSize(new java.awt.Dimension(700, 40));
         this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
         final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
         this.add(panel);
     
         suspendUpdatesButton_ = new JToggleButton("Suspend updates");
         suspendUpdatesButton_.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
               if (suspendUpdatesButton_.isSelected()) {
                  suspendUpdatesButton_.setText("Resume updates");
                  suspendUpdates_ = true;
               } else {
                  suspendUpdatesButton_.setText("Suspend updates");
                  suspendUpdates_ = false;
               }
            }
         });


         //button area
         abortButton_ = new JButton();
         abortButton_.setBackground(new java.awt.Color(255, 255, 255));
         abortButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/icons/cancel.png"))); // NOI18N
         abortButton_.setToolTipText("Abort acquisition");
         abortButton_.setFocusable(false);
         abortButton_.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
         abortButton_.setMaximumSize(new java.awt.Dimension(25, 25));
         abortButton_.setMinimumSize(new java.awt.Dimension(25, 25));
         abortButton_.setPreferredSize(new java.awt.Dimension(25, 25));
         abortButton_.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
         abortButton_.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
               try {
                  JavaUtils.invokeRestrictedMethod(DisplayPlus.this, VirtualAcquisitionDisplay.class, "abort");
               } catch (Exception ex) {
                  ReportingUtils.showError("Couldn't abort. Try pressing stop on Multi-Dimensional acquisition Window");
               }
            }
         });

         pauseButton_ = new JButton();
         pauseButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/icons/control_pause.png"))); // NOI18N
         pauseButton_.setToolTipText("Pause acquisition");
         pauseButton_.setFocusable(false);
         pauseButton_.setMargin(new java.awt.Insets(0, 0, 0, 0));
         pauseButton_.setMaximumSize(new java.awt.Dimension(25, 25));
         pauseButton_.setMinimumSize(new java.awt.Dimension(25, 25));
         pauseButton_.setPreferredSize(new java.awt.Dimension(25, 25));
         pauseButton_.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed(java.awt.event.ActionEvent evt) {
               try {
                  JavaUtils.invokeRestrictedMethod(DisplayPlus.this, VirtualAcquisitionDisplay.class, "pause");
               } catch (Exception ex) {
                  ReportingUtils.showError("Couldn't pause");
               }
//               if (eng_.isPaused()) {
//                  pauseButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/icons/resultset_next.png"))); // NOI18N
//               } else {
//                  pauseButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/icons/control_pause.png"))); // NOI18N
//               }
            }
         });

         //text area
         zPosLabel_ = new JLabel("Z position:") {

            @Override
            public void setText(String s) {
               Controls.this.invalidate();
               super.setText(s);
               Controls.this.validate();
            }
         };
         timeStampLabel_ = new JLabel("Elapsed time:") {

            @Override
            public void setText(String s) {
               Controls.this.invalidate();
               super.setText(s);
               Controls.this.validate();
            }
         };
         nextFrameLabel_ = new JLabel("Next frame: ") {

            @Override
            public void setText(String s) {
               Controls.this.invalidate();
               super.setText(s);
               Controls.this.validate();
            }
         };
         fpsField_ = new JTextField();
         fpsField_.setText("7");
         fpsField_.setToolTipText("Set the speed at which the acquisition is played back.");
         fpsField_.setPreferredSize(new Dimension(25, 18));
         fpsField_.addFocusListener(new java.awt.event.FocusAdapter() {

            public void focusLost(java.awt.event.FocusEvent evt) {
               updateFPS();
            }
         });
         fpsField_.addKeyListener(new java.awt.event.KeyAdapter() {

            public void keyReleased(java.awt.event.KeyEvent evt) {
               updateFPS();
            }
         });
         JLabel fpsLabel = new JLabel("Animation playback FPS: ");

         panel.add(abortButton_);
         panel.add(pauseButton_);
         panel.add(fpsLabel);
         panel.add(fpsField_);
         panel.add(zPosLabel_);
         panel.add(timeStampLabel_);
         panel.add(nextFrameLabel_);
      }
   }
}
