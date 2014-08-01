/*
 * Master stitched window to display real time stitched images, allow navigating of XY more easily
 */
package MMCustomization;

import com.imaging100x.twophoton.SettingsDialog;
import com.imaging100x.twophoton.TwoPhotonControl;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.util.TimerTask;
import java.util.TreeMap;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import mmcorej.MMCoreJ;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.MMStudio;
import org.micromanager.acquisition.AcquisitionEngine;

import org.micromanager.imagedisplay.VirtualAcquisitionDisplay;
import org.micromanager.api.ImageCache;
import org.micromanager.api.ImageCacheListener;


import org.micromanager.internalinterfaces.DisplayControls;
import org.micromanager.utils.*;

public class ExplorerDisplay implements ImageCacheListener  {

   public static final String WINDOW_TITLE = "Stitched overview";
   
   private static final Color TRANSPARENT_BLUE = new Color(0,0,255,60);
   private static final Color TRANSPARENT_GREEN = new Color(0,255,0,60);
   //VirtualAcquisitionDisplay on top of which this display is built
   private VirtualAcquisitionDisplay vad_;
   private AcquisitionEngine eng_;
   private JSpinner gridXSpinner_, gridYSpinner_;
   private JToggleButton newGridButton_;
   private int tileWidth_, tileHeight_;
   private Point clickStart_;
   private Point gridStart_;
    private boolean invertX_, invertY_, swapXY_;
    private JSONArray positionList_;
    private int numRows_ = 0, numCols_ = 0;

    public ExplorerDisplay(final ImageCache stitchedCache, AcquisitionEngine eng, JSONObject summaryMD) {
        eng_ = eng;
        try {
            MMStudio gui = MMStudio.getInstance();
            String camera = gui.getCore().getCameraDevice();
            swapXY_ = gui.getCore().getProperty(camera, MMCoreJ.getG_Keyword_Transpose_SwapXY()).equals("1");
            invertX_ = gui.getCore().getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorX()).equals("1");
            invertY_ = gui.getCore().getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorY()).equals("1");
        } catch (Exception ex) {
            ReportingUtils.showError(ex.toString());
        }
        try {
           tileWidth_ = MDUtils.getHeight(summaryMD);
           tileHeight_ = MDUtils.getWidth(summaryMD);
           positionList_ = summaryMD.getJSONArray("InitialPositionList");
           //get grid parameters
           for (int i = 0; i < positionList_.length(); i++) {
              long colInd = positionList_.getJSONObject(i).getLong("GridColumnIndex");
              long rowInd = positionList_.getJSONObject(i).getLong("GridRowIndex");
              if (colInd >= numCols_) {
                 numCols_ = (int) (colInd + 1);
              }
              if (rowInd >= numRows_) {
                 numRows_ = (int) (rowInd + 1);
              }
           }
        } catch (Exception e) {
            ReportingUtils.showError("Couldnt get grid info");
        }
        vad_ = new VirtualAcquisitionDisplay(stitchedCache, eng, WINDOW_TITLE) {
            public void showImage(final JSONObject tags, boolean waitForDisplay)
                    throws InterruptedException, InvocationTargetException {
                //Since this is multichannel camera, only show when last channel arrives
                try {
                    if (MDUtils.getChannelIndex(tags) == super.getNumChannels() - 1) {
                        super.showImage(tags, waitForDisplay);
                    } else {
                        ImagePlus ip = super.getHyperImage();
                        if (ip != null) {
                            //canvas never gets painted so need to set painpending false
                            
                            ip.getCanvas().setPaintPending(false);
                        }
                    }
                } catch (JSONException ex) {
                }
            }
        };
        Controls controls = new ExplorerDisplay.Controls();

        //Add in custom controls
        try {
            JavaUtils.setRestrictedFieldValue(vad_, VirtualAcquisitionDisplay.class, "controls_", controls);
        } catch (NoSuchFieldException ex) {
            ReportingUtils.showError("Couldn't create display controls");
        }
        vad_.show();

        addNavigationControls();

        
        //Zoom to 100%
        vad_.getImagePlus().getWindow().getCanvas().unzoom();
                    
        //add mouse listeners for moving grids
        addMouseListeners();

        stitchedCache.addImageCacheListener(this);
   }

    
    private void acquireTile(int row, int col) {
       
    }
    
    
   private Point getCanvasSelection(Point p) {
      final ImageCanvas canvas = vad_.getImagePlus().getCanvas();
      boolean left = p.x < canvas.getSize().width / numCols_ / 2;
      boolean right = p.x > canvas.getSize().width - canvas.getSize().width / numCols_ / 2;
      boolean top = p.y < canvas.getSize().height / numRows_ / 2;
      boolean bottom = p.y > canvas.getSize().height - canvas.getSize().height / numRows_ / 2;
      int canvasHeight = canvas.getSize().height, canvasWidth = canvas.getSize().width;
      int tileHeight = canvasHeight / numRows_;
      int tileWidth = canvasWidth / numCols_;

      if (left
              && (!top || ((double) p.y) / ((double) p.x) > ((double) tileHeight_) / ((double) tileWidth_))
              && (!bottom || ((double) (canvasHeight - p.y)) / ((double) p.x) > ((double) tileHeight_) / ((double) tileWidth_))) {
         int row = p.y / tileHeight;
         return new Point(-1, row);
      } else if (right
              && (!top
              || ((double) p.y) / ((double) (canvasWidth - p.x)) > ((double) tileHeight_) / ((double) tileWidth_))
              && (!bottom
              || ((double) (canvasHeight)) / (double) ((canvasWidth - p.x))
              > ((double) tileHeight_) / ((double) tileWidth_))) {
         int row = p.y / tileHeight;
         return new Point(numCols_,row);
      } else if (top) {
         int col = p.x / tileWidth;
         return new Point(col, -1);
      } else if (bottom) {
         int col = p.x / tileWidth;
         return new Point(col,numRows_);
      } else {
        return new Point(p.x / tileWidth, p.y / tileHeight);
      }
   }

    private void addExploreOverlay(int x, int y, int width, int height, Color color) {
      //need to convert from canvas coordinates to pixel coordinates
       final ImageCanvas canvas = vad_.getImagePlus().getCanvas();
      Roi rect = new Roi(x , y, width , height );
      rect.setFillColor(color);
      Overlay overlay = new Overlay();
      overlay.add(rect);
      //      Arrow arrow = new Arrow(40, 10, 10, 10);
//      overlay.add(arrow);
      canvas.setOverlay(overlay);
   }

   private void addNavigationControls() {
      //Experiement with manipulating image window
      final ImageCanvas canvas = vad_.getImagePlus().getCanvas();
      canvas.addMouseMotionListener(new MouseMotionListener() {
         @Override
         public void mouseDragged(MouseEvent e) {}

         @Override
         public void mouseMoved(MouseEvent e) {
            Point coords = getCanvasSelection(e.getPoint());
            if (coords.x == -1) {
               //left
               addExploreOverlay(0, coords.y * tileHeight_, tileWidth_ / 2, tileHeight_, TRANSPARENT_BLUE);
            } else if (coords.x == numCols_) {
               //right
               addExploreOverlay((int)(tileWidth_*(numCols_ - 0.5)), coords.y * tileHeight_, 
                       tileWidth_ / 2, tileHeight_, TRANSPARENT_BLUE);
            } else if (coords.y == -1) {
               //top              
               addExploreOverlay(coords.x * tileWidth_, 0, tileWidth_ , tileHeight_ / 2, TRANSPARENT_BLUE );
            } else if (coords.y == numRows_) {
               //bottom      
               addExploreOverlay(coords.x * tileWidth_, (int)(tileHeight_*(numRows_ - 0.5)), tileWidth_ , 
                       tileHeight_ / 2, TRANSPARENT_BLUE );
            } else {
               //highlight a tile
               addExploreOverlay(coords.x*tileWidth_, coords.y*tileHeight_, tileWidth_, 
                       tileHeight_, TRANSPARENT_GREEN);
            }
         }
      });
      canvas.addMouseListener(new MouseListener() {
         @Override
         public void mouseClicked(MouseEvent e) {
            Point coords = getCanvasSelection(e.getPoint());
             if (coords.x == -1) {
               //left
            } else if (coords.x == numCols_) {
               //right
            } else if (coords.y == -1) {
               //top              
            } else if (coords.y == numRows_) {
               //bottom      
            } else {
               //acquire or reacquire a tile
               acquireTile(coords.x,coords.y);
            }
         }

         @Override
         public void mousePressed(MouseEvent e) {
         }

         @Override
         public void mouseReleased(MouseEvent e) {
         }

         @Override
         public void mouseEntered(MouseEvent e) {
         }

         @Override
         public void mouseExited(MouseEvent e) {
            canvas.setOverlay(null);
         }
      });
   }


    private void addMouseListeners() {
        vad_.getImagePlus().getCanvas().addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
                Point finalPos = e.getPoint();
                ImageCanvas canvas = vad_.getImagePlus().getCanvas();
                int dx = (int) ((finalPos.x - clickStart_.x) / canvas.getMagnification());
                int dy = (int) ((finalPos.y - clickStart_.y) / canvas.getMagnification());
                vad_.getImagePlus().getOverlay().get(0).setLocation(
                        gridStart_.x + dx, gridStart_.y + dy);
                if (!canvas.getPaintPending()) {
                    canvas.setPaintPending(true);
                    canvas.paint(canvas.getGraphics());
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
            }
        });

        vad_.getImagePlus().getCanvas().addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
            }

            @Override
            public void mousePressed(MouseEvent e) {
                clickStart_ = e.getPoint();
                Roi rect = vad_.getImagePlus().getOverlay().get(0);
                Rectangle2D bounds = rect.getFloatBounds();
                gridStart_ = new Point((int) bounds.getX(), (int) bounds.getY());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
            }

            @Override
            public void mouseEntered(MouseEvent e) {
            }

            @Override
            public void mouseExited(MouseEvent e) {
            }
        });
    }

   private void createGrid() {
      try {
         //Assume that image is stitched propoerly and pixels are calibrated properly
         double pixelSize = MMStudio.getInstance().getCore().getPixelSizeUm();
         String xyStage = MMStudio.getInstance().getCore().getXYStageDevice();

         
         //get coordinates of center of exisitng grid
         TreeMap<Integer,Double> xCoords = new TreeMap<Integer,Double>(), 
                 yCoords = new TreeMap<Integer,Double>();
         JSONArray PositionList = vad_.getSummaryMetadata().getJSONArray("InitialPositionList");
         for (int i = 0; i < PositionList.length(); i++) {
            int colInd = (int) PositionList.getJSONObject(i).getLong("GridColumnIndex");
            int rowInd = (int) PositionList.getJSONObject(i).getLong("GridRowIndex");            
            xCoords.put(colInd, PositionList.getJSONObject(i)
                    .getJSONObject("DeviceCoordinatesUm").getJSONArray(xyStage).getDouble(0));
            yCoords.put(rowInd, PositionList.getJSONObject(i).getJSONObject("DeviceCoordinatesUm").getJSONArray(xyStage).getDouble(1));
         }
         
         double currentCenterX, currentCenterY;
         if (xCoords.keySet().size() % 2 == 0) {
            //even number
            currentCenterX = (xCoords.get(xCoords.size() / 2) + xCoords.get(xCoords.size() / 2 - 1)) / 2.0;
         } else {
            currentCenterX = xCoords.get(xCoords.size() / 2);
         }
         if (yCoords.keySet().size() % 2 == 0) {
            //even number
            currentCenterY = (yCoords.get(yCoords.size() / 2) + yCoords.get(yCoords.size() / 2 - 1)) / 2.0;
         } else {
            currentCenterY = yCoords.get(yCoords.size() / 2);
         }

         //get displacements of center of rectangle from center of stitched image
         double rectCenterXDisp = (vad_.getImagePlus().getOverlay().get(0).getFloatBounds().getCenterX()
                 - vad_.getImagePlus().getWidth() / 2) * pixelSize;
         double rectCenterYDisp = (vad_.getImagePlus().getOverlay().get(0).getFloatBounds().getCenterY()
                 - vad_.getImagePlus().getHeight() / 2) * pixelSize;

         //TODO: add adjustments for angle of stage relative to image

         
//         int xOverlap = SettingsDialog.xOverlap_, yOverlap = SettingsDialog.yOverlap_;
//         if (swapXY_) {
//            TwoPhotonControl.createGrid(currentCenterY + rectCenterXDisp, currentCenterX + rectCenterYDisp,
//                    (Integer) gridXSpinner_.getValue(), (Integer) gridYSpinner_.getValue(), 
//                    xOverlap, yOverlap);
//         } else {
//            TwoPhotonControl.createGrid(currentCenterX + rectCenterXDisp, currentCenterY + rectCenterYDisp,
//                    (Integer) gridXSpinner_.getValue(), (Integer) gridYSpinner_.getValue(),
//                    xOverlap, yOverlap);
//         }
         
      } catch (Exception e) {
         ReportingUtils.showError("Couldnt create grid");
      }
   }

   private void makeGridOverlay(int centerX, int centerY) {
      IJ.setTool(Toolbar.SPARE2);
      Overlay overlay = vad_.getImagePlus().getOverlay();
      if (overlay == null || overlay.size() == 0) {
         overlay = new Overlay();
      } else {
         overlay.clear();
      }

      int gridWidth = (Integer) gridXSpinner_.getValue();
      int gridHeight = (Integer) gridYSpinner_.getValue();
      int roiWidth = gridWidth * tileWidth_;
      int roiHeight = gridHeight * tileHeight_;

      Roi rectangle = new Roi(centerX - roiWidth / 2, centerY - roiHeight / 2, roiWidth, roiHeight);
      rectangle.setStrokeWidth(20f);
      overlay.add(rectangle);
      vad_.getImagePlus().setOverlay(overlay);
   }

   private void gridSizeChanged() {
      //resize exisiting grid but keep centered on same area
      Overlay overlay = vad_.getImagePlus().getOverlay();
      if (overlay == null || overlay.get(0) == null) {
         return;
      }
      Rectangle2D oldBounds = overlay.get(0).getFloatBounds();
      int centerX = (int) oldBounds.getCenterX();
      int centerY = (int) oldBounds.getCenterY();
      makeGridOverlay(centerX, centerY);
   }

   @Override
   public void imageReceived(TaggedImage taggedImage) {
      try {
         //duplicate so image storage doesnt see incorrect tags
         JSONObject newTags = new JSONObject(taggedImage.tags.toString());
         MDUtils.setPositionIndex(newTags, 0);
         taggedImage = new TaggedImage(taggedImage.pix,newTags);
      } catch (JSONException ex) {
         ReportingUtils.showError("Couldn't manipulate image tags for display");
      }
      
      vad_.imageReceived(taggedImage);
   }

   @Override
   public void imagingFinished(String path) {
      vad_.imagingFinished(path);
   }
   
   
   class Controls extends DisplayControls {

      private JButton pauseButton_, abortButton_;
      private JTextField fpsField_;
      private int startS_ = -1, startMin_ = -1, startHour_ = -1;
      
      private JLabel zPosLabel_, timeStampLabel_;

      public Controls() {
         initComponents();

         
      }
      
           
      public void acquiringImagesUpdate(boolean state) {
         abortButton_.setEnabled(state);
         pauseButton_.setEnabled(state);
      }
      
      private void updateFPS() {
         try {
            double fps = NumberUtils.displayStringToDouble(fpsField_.getText());
            vad_.setPlaybackFPS(fps);
         } catch (ParseException ex) {
         }
      }

      @Override
      public void imagesOnDiskUpdate(boolean bln) {
         abortButton_.setEnabled(bln);
         pauseButton_.setEnabled(bln);
      }


      @Override
      public void setStatusLabel(String string) {
      }
      
      private void updateLabels(JSONObject tags) {
         //Z position label
         String zPosition = "";
         try {
            zPosition = NumberUtils.doubleToDisplayString(MDUtils.getZPositionUm(tags));
         } catch (Exception e) {
            // Do nothing...
         }

         zPosLabel_.setText("Z Position: " + zPosition + " um        ");
         
         //time label
         try {
            int ms = (int) tags.getDouble("ElapsedTime-ms") ;
            int s = ms / 1000;
            int min = s / 60;
            int h = min / 60;
            
            String time = twoDigitFormat(h) + ":" + twoDigitFormat(min % 60) +
                    ":" + twoDigitFormat(s % 60) + "." + threeDigitFormat(ms % 1000);
            timeStampLabel_.setText("Elapsed time: " + time + "      ");          
         } catch (JSONException ex) {
            ReportingUtils.logError("MetaData did not contain ElapsedTime-ms field");
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
         try {
            if (vad_.acquisitionIsRunning() && vad_.getNextWakeTime() > 0) {
               final long nextImageTime = vad_.getNextWakeTime();
               if (System.nanoTime() / 1000000 < nextImageTime) {
                  final java.util.Timer timer = new java.util.Timer("Next frame display");
                  TimerTask task = new TimerTask() {

                     public void run() {
                        double timeRemainingS = (nextImageTime - System.nanoTime() / 1000000) / 1000;
                        if (timeRemainingS > 0 && vad_.acquisitionIsRunning()) {
                           setStatusLabel("Next frame: " + NumberUtils.doubleToDisplayString(1 + timeRemainingS) + " s");
                        } else {
                           timer.cancel();
                           setStatusLabel("");
                        }
                     }
                  };
                  timer.schedule(task, 2000, 100);
               }
            }

         } catch (Exception ex) {
            ReportingUtils.logError(ex);
         }
      }

      private void initComponents() {

         setPreferredSize(new java.awt.Dimension(420, 65));

         this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

         JPanel buttonPanel = new JPanel();
         buttonPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
         JPanel textPanel = new JPanel();
         SpringLayout textLayout = new SpringLayout();
         textPanel.setLayout(textLayout);
         this.add(buttonPanel);
         this.add(textPanel);


         //button area
         abortButton_ = new JButton();
         abortButton_.setBackground(new java.awt.Color(255, 255, 255));
         abortButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/icons/cancel.png"))); // NOI18N
         abortButton_.setToolTipText("Abort acquisition");
         abortButton_.setFocusable(false);
         abortButton_.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
         abortButton_.setMaximumSize(new java.awt.Dimension(30, 28));
         abortButton_.setMinimumSize(new java.awt.Dimension(30, 28));
         abortButton_.setPreferredSize(new java.awt.Dimension(30, 28));
         abortButton_.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
         abortButton_.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
               try {
                  JavaUtils.invokeRestrictedMethod(vad_, VirtualAcquisitionDisplay.class, "abort");
               } catch (Exception ex) {
                 ReportingUtils.showError("Couldn't abort. Try pressing stop on Multi-Dimensional acquisition Window");
               }
            }
         });
         buttonPanel.add(abortButton_);


         pauseButton_ = new JButton();
         pauseButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/icons/control_pause.png"))); // NOI18N
         pauseButton_.setToolTipText("Pause acquisition");
         pauseButton_.setFocusable(false);
         pauseButton_.setMargin(new java.awt.Insets(0, 0, 0, 0));
         pauseButton_.setMaximumSize(new java.awt.Dimension(30, 28));
         pauseButton_.setMinimumSize(new java.awt.Dimension(30, 28));
         pauseButton_.setPreferredSize(new java.awt.Dimension(30, 28));
         pauseButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
               try {
                  JavaUtils.invokeRestrictedMethod(vad_, VirtualAcquisitionDisplay.class, "pause");
               } catch (Exception ex) {
                 ReportingUtils.showError("Couldn't pause");
               }
               if (eng_.isPaused()) {             
                  pauseButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/icons/resultset_next.png"))); // NOI18N
               } else {
                  pauseButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/icons/control_pause.png"))); // NOI18N
               }
            }
         });
         buttonPanel.add(pauseButton_);
         
        
         gridXSpinner_ = new JSpinner();
         gridXSpinner_.setModel(new SpinnerNumberModel(2, 1, 1000, 1));
         gridXSpinner_.setPreferredSize(new Dimension(35,24));
         gridYSpinner_ = new JSpinner();
         gridYSpinner_.setModel(new SpinnerNumberModel(2, 1, 1000, 1));
         gridYSpinner_.setPreferredSize(new Dimension(35,24));
         gridXSpinner_.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
               gridSizeChanged();
            }
         });
         gridYSpinner_.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
               gridSizeChanged();
            }
         });
         final JLabel gridLabel = new JLabel(" grid");
         final JLabel byLabel = new JLabel("by");
         gridLabel.setEnabled(false);
         byLabel.setEnabled(false);
         gridXSpinner_.setEnabled(false);
         gridYSpinner_.setEnabled(false);

         final JButton createGridButton = new JButton("Create");
         createGridButton.setEnabled(false);
         createGridButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               createGrid();
            }
         });
         
         newGridButton_ = new JToggleButton("New grid");
         buttonPanel.add(new JLabel("    "));
         buttonPanel.add(newGridButton_);
         newGridButton_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               if (newGridButton_.isSelected()) {                     
                  makeGridOverlay(vad_.getImagePlus().getWidth() / 2, vad_.getImagePlus().getHeight() / 2  );
                  newGridButton_.setText("Cancel");
                  gridLabel.setEnabled(true);
                  byLabel.setEnabled(true);
                  gridXSpinner_.setEnabled(true);
                  gridYSpinner_.setEnabled(true);
                  createGridButton.setEnabled(true);
               } else {
                  vad_.getImagePlus().getOverlay().clear();
                  vad_.getImagePlus().getCanvas().repaint();
                  newGridButton_.setText("New grid");
                  gridLabel.setEnabled(false);
                  byLabel.setEnabled(false);
                  gridXSpinner_.setEnabled(false);
                  gridYSpinner_.setEnabled(false);
                  createGridButton.setEnabled(false);
               }
            }
         });
          
         buttonPanel.add(gridXSpinner_);
         buttonPanel.add(byLabel);
         buttonPanel.add(gridYSpinner_);
         buttonPanel.add(gridLabel);
         buttonPanel.add(createGridButton);
         



         //text area
         zPosLabel_ = new JLabel("Z position:                    "); 
         textPanel.add(zPosLabel_);
         
         timeStampLabel_ = new JLabel("Elapsed time:                               ");
         textPanel.add(timeStampLabel_);
          
         fpsField_ = new JTextField();
         fpsField_.setText("7");
         fpsField_.setToolTipText("Set the speed at which the acquisition is played back.");
         fpsField_.setPreferredSize(new Dimension(25,18));
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
         textPanel.add(fpsLabel);
         textPanel.add(fpsField_);

         textLayout.putConstraint(SpringLayout.WEST, textPanel, 0, SpringLayout.WEST, zPosLabel_);
         textLayout.putConstraint(SpringLayout.EAST, zPosLabel_, 0, SpringLayout.WEST,timeStampLabel_);
         textLayout.putConstraint(SpringLayout.EAST, timeStampLabel_, 0, SpringLayout.WEST,fpsLabel);
         textLayout.putConstraint(SpringLayout.EAST, fpsLabel, 0, SpringLayout.WEST,fpsField_);
         textLayout.putConstraint(SpringLayout.EAST, fpsField_, 0, SpringLayout.EAST,textPanel);
         
         textLayout.putConstraint(SpringLayout.NORTH, fpsField_, 0, SpringLayout.NORTH, textPanel);
         textLayout.putConstraint(SpringLayout.NORTH, zPosLabel_, 3, SpringLayout.NORTH, textPanel);
         textLayout.putConstraint(SpringLayout.NORTH, timeStampLabel_, 3, SpringLayout.NORTH, textPanel);
         textLayout.putConstraint(SpringLayout.NORTH, fpsLabel, 3, SpringLayout.NORTH, textPanel);

      }
   }
}
