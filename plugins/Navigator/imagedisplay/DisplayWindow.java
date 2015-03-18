package imagedisplay;

import acq.Acquisition;
import acq.ExploreAcquisition;
import acq.FixedAreaAcquisition;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import ij.ImagePlus;
import ij.gui.StackWindow;
import java.awt.*;
import java.awt.event.*;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.*;
import mmcloneclasses.graph.ContrastPanel;
import net.miginfocom.swing.MigLayout;
import org.micromanager.MMStudio;
import org.micromanager.utils.CanvasPaintPending;
import org.micromanager.utils.ReportingUtils;

/**
 * This class is the Frame that handles image viewing: it contains the canvas
 * and controls for determining which channel, Z-slice, etc. is shown. HACK: we
 * have overridden getComponents() on this function to "fix" bugs in other bits
 * of code; see that function's comment.
 */
public class DisplayWindow extends StackWindow {

   public static int CANVAS_PIXEL_BORDER = 4;
   private static int RESIZE_WINDOW_DELAY = 50;
   private static int MINIMUM_CANVAS_DIMENSION = 200;
   private static int MINIMUM_SAVED_WINDOW_DIMENSION = 700;
   private boolean closed_ = false;
   private final EventBus bus_;
   private ImagePlus plus_;
   private JPanel canvasPanel_;
   private Timer windowResizeTimer_;
   private SubImageControls subImageControls_;
   private JToggleButton arrowButton_;
   private Acquisition acq_;
   private DisplayPlus disp_;
   private JPanel nonImagePanel_, controlsAndContrastPanel_;
   private volatile boolean saveWindowResize_ = false;
   private ContrastMetadataCommentsPanel cmcPanel_;
   private DisplayPlusControls dpControls_;
   
   // store window location in Java Preferences
   private static final int DEFAULTPOSX = 300;
   private static final int DEFAULTPOSY = 100;
   private static Preferences displayPrefs_;
   private static final String EXPLOREWINDOWPOSX = "ExploreWindowPosX";
   private static final String EXPLOREWINDOWPOSY = "ExploreWindowPosY";
   private static final String FIXEDWINDOWPOSX = "FixedWindowPosX";
   private static final String FIXEDWINDOWPOSY = "FixedWindowPosY";
   private static final String WINDOWSIZEX_EXPLORE = "ExploreWindowSizeX";
   private static final String WINDOWSIZEY_EXPLORE = "ExploreWindowSizeY";
   private static final String WINDOWSIZEX_FIXED = "FixedWindowSizeX";
   private static final String WINDOWSIZEY_FIXED = "FixedWindowSizeY";

   // This class is used to signal that a window is closing.
   public class RequestToCloseEvent {

      public DisplayWindow window_;

      public RequestToCloseEvent(DisplayWindow window) {
         if (displayPrefs_ != null) {
            if (acq_ instanceof ExploreAcquisition) {
               displayPrefs_.putInt(EXPLOREWINDOWPOSX, window.getLocation().x);
               displayPrefs_.putInt(EXPLOREWINDOWPOSY, window.getLocation().y);
            } else {
               displayPrefs_.putInt(EXPLOREWINDOWPOSX, window.getLocation().x);
               displayPrefs_.putInt(EXPLOREWINDOWPOSY, window.getLocation().y);
            }
         }
         window_ = window;
      }
   };

   public DisplayWindow(final ImagePlus plus, final EventBus bus, DisplayPlus disp) {
      super(plus);
      acq_ = disp.getAcquisition();
      disp_ = disp;

      //Fix focus traversal stack trace error caused by removing componenets (i think...) and
      //disbale focus
      this.setFocusable(false);
      this.setFocusTraversalPolicy(new SortingFocusTraversalPolicy(new Comparator<Component>() {

         @Override
         public int compare(Component t, Component t1) {
            return 0;
         }
      }));

      plus_ = plus;
      bus_ = bus;
      initializePrefs();
      int posX = DEFAULTPOSX, posY = DEFAULTPOSY;
      if (displayPrefs_ != null) {
         if (acq_ instanceof ExploreAcquisition) {
            posX = displayPrefs_.getInt(EXPLOREWINDOWPOSX, DEFAULTPOSX);
            posY = displayPrefs_.getInt(EXPLOREWINDOWPOSY, DEFAULTPOSY);
         } else {
            posX = displayPrefs_.getInt(FIXEDWINDOWPOSX, DEFAULTPOSX);
            posY = displayPrefs_.getInt(FIXEDWINDOWPOSY, DEFAULTPOSY);         
         }
      }
      setLocation(posX, posY);

      MMStudio.getInstance().addMMBackgroundListener(this);
      setBackground(MMStudio.getInstance().getBackgroundColor());
      bus_.register(this);

      // HACK: hide ImageJ's native scrollbars; we provide our own.
      if (cSelector != null) {
         remove(cSelector);
      }
      if (tSelector != null) {
         remove(tSelector);
      }
      if (zSelector != null) {
         remove(zSelector);
      }
      this.setLayout(new BorderLayout());
      // Replace IJ canvas with our own that doesn't have zoom
      remove(ic);
      // Ensure that all references to this canvas are removed.
      CanvasPaintPending.removeAllPaintPending(ic);
      ic = new NoZoomCanvas(plus_);
      ic.setMinimumSize(new Dimension(200, 200));


      //create contrast, metadata, and other controls
      cmcPanel_ = new ContrastMetadataCommentsPanel(disp);
      disp.setCMCPanel(cmcPanel_);
      dpControls_ = new DisplayPlusControls(disp, bus, disp.getAcquisition());

      //create non image panel
      nonImagePanel_ = new JPanel(new BorderLayout());
      controlsAndContrastPanel_ = new JPanel(new BorderLayout());
      controlsAndContrastPanel_.add(dpControls_, BorderLayout.PAGE_START);
      controlsAndContrastPanel_.add(cmcPanel_, BorderLayout.CENTER);

      //show stuff on explore acquisitions, collapse for fixed area cqs
      arrowButton_ = new JToggleButton(disp_.getAcquisition() instanceof ExploreAcquisition ? "\u25c4" : "\u25ba");
      if (disp_.getAcquisition() instanceof ExploreAcquisition) {
         arrowButton_.setSelected(false);
         nonImagePanel_.add(controlsAndContrastPanel_, BorderLayout.CENTER);
      } else {
         arrowButton_.setSelected(true);
      }
      arrowButton_.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(ActionEvent e) {
            arrowButtonAction();
         }
      });
      //for some reason this needs to be on a panel, not a jpanel, or it disappears
      Panel arrowPanel = new Panel(new BorderLayout());

      arrowButton_.setFont(arrowButton_.getFont().deriveFont(20f));
      //so it doesn't show up as "..."
      arrowButton_.setBorder(null);
      arrowButton_.setMargin(new Insets(0,0,0,0));
      arrowPanel.add(arrowButton_, BorderLayout.CENTER);
      arrowPanel.setPreferredSize(new Dimension(30, 30));
      nonImagePanel_.add(arrowPanel, BorderLayout.LINE_START);



      //create sub image controls
      subImageControls_ = new SubImageControls(disp, bus, disp.getAcquisition());
      // Wrap the canvas in a subpanel so that we can get events when it
      // resizes.
      canvasPanel_ = new JPanel();
      canvasPanel_.setBorder(BorderFactory.createEmptyBorder(CANVAS_PIXEL_BORDER,
              CANVAS_PIXEL_BORDER, CANVAS_PIXEL_BORDER, CANVAS_PIXEL_BORDER));
      MMStudio.getInstance().addMMBackgroundListener(canvasPanel_);
      canvasPanel_.setBackground(MMStudio.getInstance().getBackgroundColor());

//      use this layout with no constraints to center canvas in the canvas panel
      canvasPanel_.setLayout(new GridBagLayout());
      GridBagConstraints con = new GridBagConstraints();
      con.fill = GridBagConstraints.BOTH;
      con.weightx = 1.0;
      con.weighty = 1.0;
      canvasPanel_.add(ic, con);

      disp.windowAndCanvasReady();


      JPanel leftPanel = new JPanel(new BorderLayout());
      leftPanel.add(canvasPanel_, BorderLayout.CENTER);
      leftPanel.add(subImageControls_, BorderLayout.PAGE_END);
      this.add(leftPanel, BorderLayout.CENTER);

      JPanel rightPanel = new JPanel(new BorderLayout());
      rightPanel.add(nonImagePanel_, BorderLayout.CENTER);
      this.add(rightPanel, BorderLayout.LINE_END);

      //Set window size based on saved prefs for each type of acquisition
      if (disp.getAcquisition() instanceof ExploreAcquisition) {
         this.setSize(new Dimension(displayPrefs_.getInt(WINDOWSIZEX_EXPLORE, 1000),
                 displayPrefs_.getInt(WINDOWSIZEY_EXPLORE, 1000)));
      } else {
         this.setSize(new Dimension(displayPrefs_.getInt(WINDOWSIZEX_FIXED, 1000),
                 displayPrefs_.getInt(WINDOWSIZEY_FIXED, 1000)));
      }
      cmcPanel_.initialize(disp);
      doLayout();

      SwingUtilities.invokeLater(new Runnable() {

         @Override
         public void run() {
            initWindow();
            windowResizeTimer_ = new Timer(RESIZE_WINDOW_DELAY, new ActionListener() {
               @Override
               public void actionPerformed(ActionEvent ae) {
                  windowResizeTimerAction();
               }
            });
            windowResizeTimer_.setRepeats(false);

            // Activate dynamic resizing
            DisplayWindow.this.addComponentListener(new ComponentAdapter() {

               @Override
               public void componentResized(ComponentEvent e) {
                  windowResizedAction();
               }
            });
         }
      });

   }

   /**
    * Expand canvas to window size for explore acquisitions
    * shrink window to correct aspect ratio for fixed area acqs
    * this is called before dynamic resize activated, so don't need to 
    * worry about accidentally storing fixed area window size
    */
   private void initWindow() {
      if (acq_ instanceof ExploreAcquisition) {
         fitCanvasToWindow();
      } else {
         //set initial zoom so that full acq area fits within window, which has already been set
         //to stored prefferred size
         double widthRatio = disp_.getFullResWidth() / (double) canvasPanel_.getSize().width;
         double heightRatio = disp_.getFullResHeight() / (double) canvasPanel_.getSize().height;
         int viewResIndex = (int) Math.ceil(Math.log(Math.max(widthRatio, heightRatio)) / Math.log(2));
         //set max res index so that min dimension doesn't shrink below 64-128 pixels
         int maxResIndex = (int) Math.ceil(Math.log((Math.min(disp_.getFullResWidth(), disp_.getFullResHeight())
                 / MINIMUM_CANVAS_DIMENSION)) / Math.log(2));
         ((ZoomableVirtualStack) disp_.getHyperImage().getStack()).initializeUpToRes(viewResIndex, maxResIndex);
         //resize to the biggest it can be in current window with correct aspect ration                
         int dsFactor = ((ZoomableVirtualStack) disp_.getHyperImage().getStack()).getDownsampleFactor();
         ZoomableVirtualStack newStack = new ZoomableVirtualStack((ZoomableVirtualStack) plus_.getStack(),
                 disp_.getFullResWidth() / dsFactor, disp_.getFullResHeight() / dsFactor);
         disp_.changeStack(newStack);
         this.validate();
         //fit window around correctly sized canvas, but don't save the window size
         saveWindowResize_ = false;
         shrinkWindowToFitCanvas();
      }
   }

   /**
    * When shrinking with arrow: -remove controls and shrink window by the size
    * of controls
    *
    * When expanding with arrow: -If there is enough room on screen, expand
    * window so same area stays visible (moving window to the left if needed to
    * show everything) -If there's not enough width to keep whole image area
    * shown, expand to full screen width and clip part of image as needed
    */
   private void arrowButtonAction() {
      saveWindowResize_ = false;
      if (arrowButton_.isSelected()) {
         arrowButton_.setText("\u25ba");
         int controlsWidth = controlsAndContrastPanel_.getPreferredSize().width;
         nonImagePanel_.remove(controlsAndContrastPanel_);
         //shrink window by width of controls
         DisplayWindow.this.setSize(new Dimension(DisplayWindow.this.getWidth() - controlsWidth,
                 DisplayWindow.this.getHeight()));
      } else {
         arrowButton_.setText("\u25c4");
         nonImagePanel_.add(controlsAndContrastPanel_, BorderLayout.CENTER);
         //expnd window to accomodate non image panel, provided there is space for it
         int screenWidth = Toolkit.getDefaultToolkit().getScreenSize().width;
         if (disp_.getHyperImage().getCanvas().getWidth() + nonImagePanel_.getPreferredSize().width < screenWidth) {
            DisplayWindow.this.setSize(new Dimension(nonImagePanel_.getPreferredSize().width
                    + disp_.getHyperImage().getCanvas().getWidth(), DisplayWindow.this.getHeight()));
            //move left so whole thing fits on screen if needed
            if (this.getWidth() + this.getLocationOnScreen().x > screenWidth) {
               this.setLocation(screenWidth - this.getWidth(), this.getLocation().y);
            }
         } else {
            //not enough space for it on screen, so expand to full size
            DisplayWindow.this.setSize(new Dimension(screenWidth, DisplayWindow.this.getHeight()));
         }
      }
      DisplayWindow.this.invalidate();
      DisplayWindow.this.validate();
   }

   /**
    * Called whenever window resize occurs, either from user or programmatically
    */
   private void windowResizedAction() {
      //wait for a pause before acting
      if (windowResizeTimer_.isRunning()) {
         windowResizeTimer_.restart();
      } else {
         windowResizeTimer_.start();
      }
   }

   /**
    * Explore acquisitions: resize canvas to use all available space 
    * Fixed area acquisitions: 
    * -resize canvas if shrinking
    * -if expanding, zoom as much as needed to fill entire panel
    */
   private void windowResizeTimerAction() {
      if (acq_ instanceof FixedAreaAcquisition) {
         Dimension availableSize = new Dimension(canvasPanel_.getSize().width - 2*CANVAS_PIXEL_BORDER,
                 canvasPanel_.getSize().height - 2*CANVAS_PIXEL_BORDER);
         int dsFactor = ((ZoomableVirtualStack) disp_.getHyperImage().getStack()).getDownsampleFactor();         
         int dsHeight = disp_.getFullResHeight() / dsFactor;
         int dsWidth = disp_.getFullResWidth() / dsFactor;
         //calculate how many zoom levels needed to fully fill
         int zoomsNeeded = (int) Math.ceil(Math.log(Math.max(availableSize.width / (double) dsWidth,
                    availableSize.height / (double) dsHeight)) / Math.log(2));
         if (zoomsNeeded > 0) { //if zoom out
            disp_.zoom(-zoomsNeeded);
         } else {
            resizeCanvas(false); 
         }

         //store fixed acq size, but only if this call comes from user resizing the window
         if (saveWindowResize_) {
            displayPrefs_.putInt(WINDOWSIZEX_FIXED, Math.max(MINIMUM_SAVED_WINDOW_DIMENSION, DisplayWindow.this.getSize().width));
            displayPrefs_.putInt(WINDOWSIZEY_FIXED, Math.max(MINIMUM_SAVED_WINDOW_DIMENSION, DisplayWindow.this.getSize().height));
         }
         saveWindowResize_ = true;
      } else {
         //Explore acq, resize to use all available space
         if (fitCanvasToWindow()) {
            //store explore acquisition size if resize successful    
            displayPrefs_.putInt(WINDOWSIZEX_EXPLORE, Math.max(MINIMUM_SAVED_WINDOW_DIMENSION, DisplayWindow.this.getSize().width));
            displayPrefs_.putInt(WINDOWSIZEY_EXPLORE, Math.max(MINIMUM_SAVED_WINDOW_DIMENSION, DisplayWindow.this.getSize().height));
         }
      }
   }

   /**
    * Used only for fixed area acquisition 
    * 1) At startup to fit window around canvas with correct aspect ratio 
    * 2) When zooming out, to shrink window (but not when zooming in to expand--this 
    * is done by dragging the size of the window to be bigger)
    */
   private void shrinkWindowToFitCanvas() {
      Dimension cpSize = canvasPanel_.getSize();
      Dimension windowSize = this.getSize();
      Dimension newWindowSize = new Dimension();

      int dsFactor = ((ZoomableVirtualStack) disp_.getHyperImage().getStack()).getDownsampleFactor();
      int dsHeight = disp_.getFullResHeight() / dsFactor;
      int dsWidth = disp_.getFullResWidth() / dsFactor;

      if (cpSize.width > dsWidth) {
         newWindowSize.width = windowSize.width + (dsWidth - cpSize.width + 2 * CANVAS_PIXEL_BORDER);
      } else {
         newWindowSize.width = windowSize.width;
      }
      if (cpSize.height > dsHeight) {
         newWindowSize.height = windowSize.height + (dsHeight - cpSize.height + 2 * CANVAS_PIXEL_BORDER);
      } else {
         newWindowSize.height = windowSize.height;
      }
      this.setSize(newWindowSize);
   }

   /**
    * Used by explore acquisitions to resize canvas to grow or shrink with
    * window changes
    */
   private boolean fitCanvasToWindow() {
      synchronized (canvasPanel_) {
         if (!canvasPanel_.isValid()) {
            return false; // cant resize to size of panel if its not layed out yet
         }
         Dimension panelSize = canvasPanel_.getSize();
         if (panelSize.width < CANVAS_PIXEL_BORDER*2 || panelSize.height < CANVAS_PIXEL_BORDER*2 ) {
            return false; //dont set it to invisible sizes
         }
         //expand canvas to take up full size of viewing window
         disp_.changeStack(new ZoomableVirtualStack((ZoomableVirtualStack) plus_.getStack(),
                 panelSize.width - 2 * CANVAS_PIXEL_BORDER, panelSize.height - 2 * CANVAS_PIXEL_BORDER));
         this.validate();
         return true;
      }
   }

   /**
    * Used for fixed area acquisitions
    * Called when zooming or user resize of window
    * Shrink canvas as needed when zooming out
    *
    * @return true if redraw was included
    */
   public boolean resizeCanvas(boolean shrinkWindow) {
      synchronized (canvasPanel_) {
         Dimension panelSize = canvasPanel_.getSize();
         //get the area available for viewing in canvas panel
         int displayHeight = panelSize.height - 2 * CANVAS_PIXEL_BORDER;
         int displayWidth = panelSize.width - 2 * CANVAS_PIXEL_BORDER;
         //Use these to calulate the display size of current image
         int dsFactor = ((ZoomableVirtualStack) disp_.getHyperImage().getStack()).getDownsampleFactor();
         int fullWidth = disp_.getFullResWidth();
         int fullHeight = disp_.getFullResHeight();
         //shrink canvas to eliminate empty space, but do not expand to accomodate more canvas
         int newCanvasWidth = Math.min(displayWidth, fullWidth / dsFactor);
         int newCanvasHeight = Math.min(displayHeight, fullHeight / dsFactor);

         ZoomableVirtualStack newStack = new ZoomableVirtualStack((ZoomableVirtualStack) plus_.getStack(), newCanvasWidth, newCanvasHeight);
         //Replace stack if number of pixels in stack has changed
         if (!((ZoomableVirtualStack) plus_.getStack()).equalDisplaySize(newStack)) {
            disp_.changeStack(newStack);
         } else {
            return false;
         }
         this.validate();
         //now that canvas is shrunk, shrink window
         if (shrinkWindow) {
            shrinkWindowToFitCanvas();
         }
         return true;
      }
   }

   public void paint(Graphics g) {
      drawInfo(g);
   }

   /**
    * Keep class specific preferences to store window location
    */
   private void initializePrefs() {
      if (displayPrefs_ == null) {
         try {
            displayPrefs_ = Preferences.userNodeForPackage(getClass());
         } catch (Exception e) {
            ReportingUtils.logError(e);
         }
      }
   }

   public SubImageControls getSubImageControls() {
      return subImageControls_;
   }

   @Override
   public boolean close() {
      windowClosing(null);
      return closed_;
   }

   @Override
   public void windowClosing(WindowEvent e) {
      if (!closed_) {
         bus_.post(new RequestToCloseEvent(this));
      }
   }

   public ContrastPanel getContrastPanel() {
      return cmcPanel_.getContrastPanel();
   }
   
   @Subscribe
   public void onLayoutChange(ScrollerPanel.LayoutChangedEvent event) {
//      System.out.println();
   }

   // Force this window to go away.
   public void forceClosed() {
      try {
         super.close();
      } catch (NullPointerException ex) {
         ReportingUtils.showError(ex, "Null pointer error in ImageJ code while closing window");
      }
      MMStudio.getInstance().removeMMBackgroundListener(this);
      MMStudio.getInstance().removeMMBackgroundListener(canvasPanel_);
      bus_.unregister(this); 
      cmcPanel_.prepareForClose();        
      dpControls_.prepareForClose();
      
      closed_ = true;
   }
   
   @Override
   public boolean validDimensions() {
      //override this so that code that replaces imagej stack to resize the canvas works
      return true;
   }

   @Override
   public void windowClosed(WindowEvent E) {
      try {
         super.windowClosed(E);
      } catch (NullPointerException ex) {
         ReportingUtils.showError(ex, "Null pointer error in ImageJ code while closing window");
      }
   }

   @Override
   public void windowActivated(WindowEvent e) {
      if (!isClosed()) {
         super.windowActivated(e);
      }
   }

   @Override
   public void pack() {
      //override this because it erroneously makes windows too big from an imagej call
      //in a private method
   }
}
