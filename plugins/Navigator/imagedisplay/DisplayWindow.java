package imagedisplay;

import acq.Acquisition;
import acq.ExploreAcquisition;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import ij.ImagePlus;
import ij.gui.StackWindow;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowEvent;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.*;
import mmcloneclasses.internalinterfaces.DisplayControls;
import org.micromanager.MMStudio;
import org.micromanager.utils.CanvasPaintPending;
import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.ReportingUtils;

/**
 * This class is the Frame that handles image viewing: it contains the canvas
 * and controls for determining which channel, Z-slice, etc. is shown. HACK: we
 * have overridden getComponents() on this function to "fix" bugs in other bits
 * of code; see that function's comment.
 */
public class DisplayWindow extends StackWindow {

   public static int CANVAS_PIXEL_BORDER = 4;
   private static int RESIZE_WINDOW_DELAY = 100;
   private static int FIXED_ACQ_ZOOM_PIXEL_CUTOFF = 200;
   
   private boolean closed_ = false;
   private final EventBus bus_;
   private ImagePlus plus_;
   private JPanel canvasPanel_;
   private Timer windowResizeTimer_;
   private DisplayControls subImageControls_;
   private JToggleButton arrowButton_;
   private Acquisition acq_;
   private DisplayPlus disp_;
   // store window location in Java Preferences
   private static final int DEFAULTPOSX = 300;
   private static final int DEFAULTPOSY = 100;
   private static Preferences displayPrefs_;
   private static final String WINDOWPOSX = "WindowPosX";
   private static final String WINDOWPOSY = "WindowPosY";
   private static final String WINDOWSIZEX = "WindowSizeX";
   private static final String WINDOWSIZEY = "WindowSizeY";

   // This class is used to signal that a window is closing.
   public static class RequestToCloseEvent {

      public DisplayWindow window_;

      public RequestToCloseEvent(DisplayWindow window) {
         if (displayPrefs_ != null) {
            displayPrefs_.putInt(WINDOWPOSX, window.getLocation().x);
            displayPrefs_.putInt(WINDOWPOSY, window.getLocation().y);
         }
         window_ = window;
      }
   };

   public DisplayWindow(final ImagePlus plus, final EventBus bus, DisplayPlus disp) {
      super(plus);
      acq_ = disp.getAcquisition();
      disp_ = disp;
      windowResizeTimer_ = new Timer(RESIZE_WINDOW_DELAY, new ActionListener() {

         @Override
         public void actionPerformed(ActionEvent ae) {
            resizeCanvas();
         }
      });
      windowResizeTimer_.setRepeats(false);

      plus_ = plus;
      bus_ = bus;
      initializePrefs();
      int posX = DEFAULTPOSX, posY = DEFAULTPOSY;
      if (displayPrefs_ != null) {
         posX = displayPrefs_.getInt(WINDOWPOSX, DEFAULTPOSX);
         posY = displayPrefs_.getInt(WINDOWPOSY, DEFAULTPOSY);
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
      // Re-create the ImageJ canvas. We need it to manually draw its own
      // border (by comparison, in standard ImageJ the ImageWindow draws the
      // border), because we're changing the layout heirarchy so the 
      // ImageWindow doesn't draw it in the right place. Thus, we have to 
      // override its paint() method.
      remove(ic);
      // Ensure that all references to this canvas are removed.
      CanvasPaintPending.removeAllPaintPending(ic);
      ic = new NoZoomCanvas(plus_);
      // HACK: set the minimum size. If we don't do this, then the canvas
      // doesn't shrink properly when the window size is reduced. Why?!
      ic.setMinimumSize(new Dimension(16, 16));



      //create contrast, metadata, and other controls
      ContrastMetadataCommentsPanel cmcPanel = new ContrastMetadataCommentsPanel(disp);
      disp.setCMCPanel(cmcPanel);

      //create non image panel
      final JPanel nip = new JPanel(new BorderLayout());
      final JPanel controlsAndContrastPanel = new JPanel(new BorderLayout());
      controlsAndContrastPanel.add(new DisplayPlusControls(disp, bus, disp.getAcquisition()), BorderLayout.PAGE_START);
      controlsAndContrastPanel.add(cmcPanel, BorderLayout.CENTER);
      nip.add(controlsAndContrastPanel, BorderLayout.CENTER);


      arrowButton_ = new JToggleButton("\u25ba");
      arrowButton_.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(ActionEvent e) {
            if (arrowButton_.isSelected()) {
               arrowButton_.setText("\u25c4");
               nip.remove(controlsAndContrastPanel);
            } else {
               arrowButton_.setText("\u25ba");
               nip.add(controlsAndContrastPanel, BorderLayout.CENTER);
            }
            DisplayWindow.this.invalidate();
            DisplayWindow.this.validate();
            DisplayWindow.this.repaint();
         }
      });
      //for some reason this needs to be on a panel, not a jpanel, or it disappears
      Panel arrowPanel = new Panel(new BorderLayout());

      arrowButton_.setFont(arrowButton_.getFont().deriveFont(20f));
      arrowPanel.add(arrowButton_, BorderLayout.CENTER);
      arrowPanel.setPreferredSize(new Dimension(30, 30));
      nip.add(arrowPanel, BorderLayout.LINE_START);



      //create sub image controls
      subImageControls_ = new SubImageControls(disp, bus, disp.getAcquisition());
      // Wrap the canvas in a subpanel so that we can get events when it
      // resizes.
      canvasPanel_ = new JPanel();
      canvasPanel_.setBorder(BorderFactory.createEmptyBorder(CANVAS_PIXEL_BORDER,
              CANVAS_PIXEL_BORDER, CANVAS_PIXEL_BORDER, CANVAS_PIXEL_BORDER));
      MMStudio.getInstance().addMMBackgroundListener(canvasPanel_);
      canvasPanel_.setBackground(MMStudio.getInstance().getBackgroundColor());

      //use this layout with no constraints to center canvas in the canvas panel
      canvasPanel_.setLayout(new GridBagLayout());
      canvasPanel_.add(ic);
      disp.windowAndCanvasReady();


      JPanel leftPanel = new JPanel(new BorderLayout());
      leftPanel.add(canvasPanel_, BorderLayout.CENTER);
      leftPanel.add(subImageControls_, BorderLayout.PAGE_END);
      this.add(leftPanel, BorderLayout.CENTER);

      JPanel rightPanel = new JPanel(new BorderLayout());
      rightPanel.add(nip, BorderLayout.CENTER);
      this.add(rightPanel, BorderLayout.LINE_END);


      this.setSize(new Dimension(displayPrefs_.getInt(WINDOWSIZEX, 700), displayPrefs_.getInt(WINDOWSIZEY, 700)));
      cmcPanel.initialize(disp);
      doLayout();


      if (acq_ instanceof ExploreAcquisition) {
         resizeCanvas();
      } else {
         //set initial zoom so that full acq area fits within window    
         double widthRatio = disp.getFullResWidth() / (double) canvasPanel_.getSize().width;
         double heightRatio = disp.getFullResHeight() / (double) canvasPanel_.getSize().height;
         int viewResIndex = (int) Math.ceil(Math.log(Math.max(widthRatio, heightRatio) / Math.log(2)));
         //set max res index so that max dimension can be shrunk below 128 pixels
         int maxResIndex = (int) Math.ceil(Math.log((Math.max(disp.getFullResWidth(),disp.getFullResHeight()) 
                 / FIXED_ACQ_ZOOM_PIXEL_CUTOFF)) / Math.log(2));
         ((ZoomableVirtualStack) disp.getHyperImage().getStack()).initializeUpToRes(viewResIndex, maxResIndex);
         resizeCanvas();
         fitWindowToCanvas(false);
      }

      // Activate dynamic resizing
      canvasPanel_.addComponentListener(new ComponentAdapter() {

         @Override
         public void componentResized(ComponentEvent e) {
            if (windowResizeTimer_.isRunning()) {
               windowResizeTimer_.restart();
            } else {
               windowResizeTimer_.start();
            }
            //store window size
            displayPrefs_.putInt(WINDOWSIZEX, DisplayWindow.this.getSize().width);
            displayPrefs_.putInt(WINDOWSIZEY, DisplayWindow.this.getSize().height);
         }
      });
   }

   public DisplayControls getSubImageControls() {
      return subImageControls_;
   }

   public Dimension getCanvasPanelSize() {
      return canvasPanel_.getSize();
   }

   public void fitWindowToCanvas(boolean allowExpansion) {
      Dimension cpSize = canvasPanel_.getSize();
      Dimension canvasSize = disp_.getHyperImage().getCanvas().getSize();
      Dimension windowSize = this.getSize();
      Dimension newWindowSize = new Dimension();
      if (allowExpansion || cpSize.width > canvasSize.width) {
         newWindowSize.width = windowSize.width + (canvasSize.width - cpSize.width);
      } else {
         newWindowSize.width = windowSize.width;
      }
      if (allowExpansion || cpSize.height > canvasSize.height) {
         newWindowSize.height = windowSize.height + (canvasSize.height - cpSize.height);
      } else {
         newWindowSize.height = windowSize.height;
      }
      System.out.println(cpSize);
      System.out.println(canvasSize);
      System.out.println(windowSize);
      System.out.println(newWindowSize + "\n");
      this.setSize(newWindowSize);
   }

   public boolean resizeCanvas() {
      synchronized (canvasPanel_) {
         Dimension panelSize = canvasPanel_.getSize();
         if (acq_ instanceof ExploreAcquisition) {
            disp_.changeStack(new ZoomableVirtualStack((ZoomableVirtualStack) plus_.getStack(),
                    panelSize.width - 2 * CANVAS_PIXEL_BORDER, panelSize.height - 2 * CANVAS_PIXEL_BORDER));
         } else {
            //make canvas the proper aspect ratio                    
            int fullWidth = disp_.getFullResWidth();
            int fullHeight = disp_.getFullResHeight();
            //get the area available for viewing in canvas panel
            int displayHeight = panelSize.height - 2 * CANVAS_PIXEL_BORDER;
            int displayWidth = panelSize.width - 2 * CANVAS_PIXEL_BORDER;

            int dsFactor = ((ZoomableVirtualStack) disp_.getHyperImage().getStack()).getDownsampleFactor();
            int canvasWidth, canvasHeight;
            //canvas height and canvas width will be <= display image at at resolutions
            //except the most zoomed out
            canvasWidth = Math.min(displayWidth, fullWidth / dsFactor);
            canvasHeight = Math.min(displayHeight, fullHeight / dsFactor);

            ZoomableVirtualStack newStack = new ZoomableVirtualStack((ZoomableVirtualStack) plus_.getStack(), canvasWidth, canvasHeight);
            //only chnage if display size has actually changed
            if (!((ZoomableVirtualStack) plus_.getStack()).equalDisplaySize(newStack)) {
               disp_.changeStack(newStack);
            } else {
               return false;
            }
         }
         DisplayWindow.this.validate();
      }
      return true;
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

   @Subscribe
   public void onLayoutChange(ScrollerPanel.LayoutChangedEvent event) {
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
      closed_ = true;
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
