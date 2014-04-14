///*
// * Master stitched window to display real time stitched images, allow navigating of XY more easily
// */
//package MMCustomization;
//
//import com.imaging100x.twophoton.SettingsDialog;
//import com.imaging100x.twophoton.TwoPhotonControl;
//import ij.IJ;
//import ij.ImagePlus;
//import ij.gui.*;
//import java.awt.*;
//import java.awt.event.*;
//import java.awt.geom.Rectangle2D;
//import java.lang.reflect.InvocationTargetException;
//import java.text.ParseException;
//import java.util.TimerTask;
//import java.util.TreeMap;
//import javax.swing.*;
//import javax.swing.event.ChangeEvent;
//import javax.swing.event.ChangeListener;
//import mmcorej.MMCoreJ;
//import mmcorej.TaggedImage;
//import org.json.JSONArray;
//import org.json.JSONException;
//import org.json.JSONObject;
//import org.micromanager.MMStudioMainFrame;
//import org.micromanager.acquisition.AcquisitionEngine;
//
//import org.micromanager.acquisition.VirtualAcquisitionDisplay;
//import org.micromanager.api.ImageCache;
//import org.micromanager.api.ImageCacheListener;
//
//
//import org.micromanager.internalinterfaces.DisplayControls;
//import org.micromanager.utils.*;
//
//public class ExplorerDisplay implements ImageCacheListener  {
//
//   public static final String WINDOW_TITLE = "Stitched overview";
//   
//   private static final Color TRANSPARENT_BLUE = new Color(0,0,255,60);
//   private static final Color TRANSPARENT_GREEN = new Color(0,255,0,60);
//   //VirtualAcquisitionDisplay on top of which this display is built
//   private VirtualAcquisitionDisplay vad_;
//   private AcquisitionEngine eng_;
//   private JSpinner gridXSpinner_, gridYSpinner_;
//   private JToggleButton newGridButton_;
//   private int tileWidth_, tileHeight_;
//   private Point clickStart_;
//   private Point gridStart_;
//    private boolean invertX_, invertY_, swapXY_;
//    private JSONArray positionList_;
//    private int numRows_ = 0, numCols_ = 0;
//
//    public ExplorerDisplay(final ImageCache stitchedCache, AcquisitionEngine eng, JSONObject summaryMD) {
//        eng_ = eng;
//        try {
//            MMStudioMainFrame gui = MMStudioMainFrame.getInstance();
//            String camera = gui.getCore().getCameraDevice();
//            swapXY_ = gui.getCore().getProperty(camera, MMCoreJ.getG_Keyword_Transpose_SwapXY()).equals("1");
//            invertX_ = gui.getCore().getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorX()).equals("1");
//            invertY_ = gui.getCore().getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorY()).equals("1");
//        } catch (Exception ex) {
//            ReportingUtils.showError(ex.toString());
//        }
//        try {
//           tileWidth_ = MDUtils.getHeight(summaryMD);
//           tileHeight_ = MDUtils.getWidth(summaryMD);
//           positionList_ = summaryMD.getJSONArray("InitialPositionList");
//           //get grid parameters
//           for (int i = 0; i < positionList_.length(); i++) {
//              long colInd = positionList_.getJSONObject(i).getLong("GridColumnIndex");
//              long rowInd = positionList_.getJSONObject(i).getLong("GridRowIndex");
//              if (colInd >= numCols_) {
//                 numCols_ = (int) (colInd + 1);
//              }
//              if (rowInd >= numRows_) {
//                 numRows_ = (int) (rowInd + 1);
//              }
//           }
//        } catch (Exception e) {
//            ReportingUtils.showError("Couldnt get grid info");
//        }
//        vad_ = new VirtualAcquisitionDisplay(stitchedCache, eng, WINDOW_TITLE) {           
//        };
//
//        //Add in custom controls
//        try {
//            JavaUtils.setRestrictedFieldValue(vad_, VirtualAcquisitionDisplay.class, "controls_", controls);
//        } catch (NoSuchFieldException ex) {
//            ReportingUtils.showError("Couldn't create display controls");
//        }
//        vad_.show();
//
//        addNavigationControls();
//
//        
//        //Zoom to 100%
//        vad_.getImagePlus().getWindow().getCanvas().unzoom();
//                    
//        //add mouse listeners for moving grids
//        addMouseListeners();
//
//        stitchedCache.addImageCacheListener(this);
//   }
//
//    
//    private void acquireTile(int row, int col) {
//       
//    }
//    
//    
//   private Point getCanvasSelection(Point p) {
//      final ImageCanvas canvas = vad_.getImagePlus().getCanvas();
//      boolean left = p.x < canvas.getSize().width / numCols_ / 2;
//      boolean right = p.x > canvas.getSize().width - canvas.getSize().width / numCols_ / 2;
//      boolean top = p.y < canvas.getSize().height / numRows_ / 2;
//      boolean bottom = p.y > canvas.getSize().height - canvas.getSize().height / numRows_ / 2;
//      int canvasHeight = canvas.getSize().height, canvasWidth = canvas.getSize().width;
//      int tileHeight = canvasHeight / numRows_;
//      int tileWidth = canvasWidth / numCols_;
//
//      if (left
//              && (!top || ((double) p.y) / ((double) p.x) > ((double) tileHeight_) / ((double) tileWidth_))
//              && (!bottom || ((double) (canvasHeight - p.y)) / ((double) p.x) > ((double) tileHeight_) / ((double) tileWidth_))) {
//         int row = p.y / tileHeight;
//         return new Point(-1, row);
//      } else if (right
//              && (!top
//              || ((double) p.y) / ((double) (canvasWidth - p.x)) > ((double) tileHeight_) / ((double) tileWidth_))
//              && (!bottom
//              || ((double) (canvasHeight)) / (double) ((canvasWidth - p.x))
//              > ((double) tileHeight_) / ((double) tileWidth_))) {
//         int row = p.y / tileHeight;
//         return new Point(numCols_,row);
//      } else if (top) {
//         int col = p.x / tileWidth;
//         return new Point(col, -1);
//      } else if (bottom) {
//         int col = p.x / tileWidth;
//         return new Point(col,numRows_);
//      } else {
//        return new Point(p.x / tileWidth, p.y / tileHeight);
//      }
//   }
//
//    private void addExploreOverlay(int x, int y, int width, int height, Color color) {
//      //need to convert from canvas coordinates to pixel coordinates
//       final ImageCanvas canvas = vad_.getImagePlus().getCanvas();
//      Roi rect = new Roi(x , y, width , height );
//      rect.setFillColor(color);
//      Overlay overlay = new Overlay();
//      overlay.add(rect);
//      //      Arrow arrow = new Arrow(40, 10, 10, 10);
////      overlay.add(arrow);
//      canvas.setOverlay(overlay);
//   }
//
//   private void addNavigationControls() {
//      //Experiement with manipulating image window
//      final ImageCanvas canvas = vad_.getImagePlus().getCanvas();
//      canvas.addMouseMotionListener(new MouseMotionListener() {
//         @Override
//         public void mouseDragged(MouseEvent e) {}
//
//         @Override
//         public void mouseMoved(MouseEvent e) {
//            Point coords = getCanvasSelection(e.getPoint());
//            if (coords.x == -1) {
//               //left
//               addExploreOverlay(0, coords.y * tileHeight_, tileWidth_ / 2, tileHeight_, TRANSPARENT_BLUE);
//            } else if (coords.x == numCols_) {
//               //right
//               addExploreOverlay((int)(tileWidth_*(numCols_ - 0.5)), coords.y * tileHeight_, 
//                       tileWidth_ / 2, tileHeight_, TRANSPARENT_BLUE);
//            } else if (coords.y == -1) {
//               //top              
//               addExploreOverlay(coords.x * tileWidth_, 0, tileWidth_ , tileHeight_ / 2, TRANSPARENT_BLUE );
//            } else if (coords.y == numRows_) {
//               //bottom      
//               addExploreOverlay(coords.x * tileWidth_, (int)(tileHeight_*(numRows_ - 0.5)), tileWidth_ , 
//                       tileHeight_ / 2, TRANSPARENT_BLUE );
//            } else {
//               //highlight a tile
//               addExploreOverlay(coords.x*tileWidth_, coords.y*tileHeight_, tileWidth_, 
//                       tileHeight_, TRANSPARENT_GREEN);
//            }
//         }
//      });
//      canvas.addMouseListener(new MouseListener() {
//         @Override
//         public void mouseClicked(MouseEvent e) {
//            Point coords = getCanvasSelection(e.getPoint());
//             if (coords.x == -1) {
//               //left
//            } else if (coords.x == numCols_) {
//               //right
//            } else if (coords.y == -1) {
//               //top              
//            } else if (coords.y == numRows_) {
//               //bottom      
//            } else {
//               //acquire or reacquire a tile
//               acquireTile(coords.x,coords.y);
//            }
//         }
//
//         @Override
//         public void mousePressed(MouseEvent e) {
//         }
//
//         @Override
//         public void mouseReleased(MouseEvent e) {
//         }
//
//         @Override
//         public void mouseEntered(MouseEvent e) {
//         }
//
//         @Override
//         public void mouseExited(MouseEvent e) {
//            canvas.setOverlay(null);
//         }
//      });
//   }
//
//
//    private void addMouseListeners() {
//        vad_.getImagePlus().getCanvas().addMouseMotionListener(new MouseMotionListener() {
//            @Override
//            public void mouseDragged(MouseEvent e) {
//                Point finalPos = e.getPoint();
//                ImageCanvas canvas = vad_.getImagePlus().getCanvas();
//                int dx = (int) ((finalPos.x - clickStart_.x) / canvas.getMagnification());
//                int dy = (int) ((finalPos.y - clickStart_.y) / canvas.getMagnification());
//                vad_.getImagePlus().getOverlay().get(0).setLocation(
//                        gridStart_.x + dx, gridStart_.y + dy);
//                if (!canvas.getPaintPending()) {
//                    canvas.setPaintPending(true);
//                    canvas.paint(canvas.getGraphics());
//                }
//            }
//
//            @Override
//            public void mouseMoved(MouseEvent e) {
//            }
//        });
//
//        vad_.getImagePlus().getCanvas().addMouseListener(new MouseListener() {
//            @Override
//            public void mouseClicked(MouseEvent e) {
//            }
//
//            @Override
//            public void mousePressed(MouseEvent e) {
//                clickStart_ = e.getPoint();
//                Roi rect = vad_.getImagePlus().getOverlay().get(0);
//                Rectangle2D bounds = rect.getFloatBounds();
//                gridStart_ = new Point((int) bounds.getX(), (int) bounds.getY());
//            }
//
//            @Override
//            public void mouseReleased(MouseEvent e) {
//            }
//
//            @Override
//            public void mouseEntered(MouseEvent e) {
//            }
//
//            @Override
//            public void mouseExited(MouseEvent e) {
//            }
//        });
//    }
//
//   private void makeGridOverlay(int centerX, int centerY) {
//      IJ.setTool(Toolbar.SPARE2);
//      Overlay overlay = vad_.getImagePlus().getOverlay();
//      if (overlay == null || overlay.size() == 0) {
//         overlay = new Overlay();
//      } else {
//         overlay.clear();
//      }
//
//      int gridWidth = (Integer) gridXSpinner_.getValue();
//      int gridHeight = (Integer) gridYSpinner_.getValue();
//      int roiWidth = gridWidth * tileWidth_;
//      int roiHeight = gridHeight * tileHeight_;
//
//      Roi rectangle = new Roi(centerX - roiWidth / 2, centerY - roiHeight / 2, roiWidth, roiHeight);
//      rectangle.setStrokeWidth(20f);
//      overlay.add(rectangle);
//      vad_.getImagePlus().setOverlay(overlay);
//   }
//
//   private void gridSizeChanged() {
//      //resize exisiting grid but keep centered on same area
//      Overlay overlay = vad_.getImagePlus().getOverlay();
//      if (overlay == null || overlay.get(0) == null) {
//         return;
//      }
//      Rectangle2D oldBounds = overlay.get(0).getFloatBounds();
//      int centerX = (int) oldBounds.getCenterX();
//      int centerY = (int) oldBounds.getCenterY();
//      makeGridOverlay(centerX, centerY);
//   }
//
//
//   
//   
//}
