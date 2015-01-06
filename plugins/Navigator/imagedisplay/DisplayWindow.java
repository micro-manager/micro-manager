package imagedisplay;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.StackWindow;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowEvent;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.Timer;
import mmcloneclasses.internalinterfaces.DisplayControls;
import org.micromanager.MMStudio;
import org.micromanager.utils.CanvasPaintPending;
import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.ReportingUtils;


/**
 * This class is the Frame that handles image viewing: it contains the 
 * canvas and controls for determining which channel, Z-slice, etc. is shown. 
 * HACK: we have overridden getComponents() on this function to "fix" bugs
 * in other bits of code; see that function's comment.
 */
public class DisplayWindow extends StackWindow {
   public static int CANVAS_PIXEL_BORDER = 4;
   private static int RESIZE_WINDOW_DELAY = 100;

   private boolean closed_ = false;
   private final EventBus bus_;
   private ImagePlus plus_;
   private JPanel canvasPanel_;
   private Timer windowResizeTimer_;
   private DisplayControls subImageControls_;
   private JToggleButton arrowButton_;
   
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
//      ic = new CanvasPlus(plus_);
      ic = new ImageCanvas(plus_);
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
      nip.add(arrowPanel, BorderLayout.LINE_END);
      
        
      
      //create sub image controls
      subImageControls_ = new SubImageControls(disp, bus, disp.getAcquisition());
      // Wrap the canvas in a subpanel so that we can get events when it
      // resizes.
      canvasPanel_ = new JPanel();
      canvasPanel_.setBorder(BorderFactory.createEmptyBorder(CANVAS_PIXEL_BORDER,
              CANVAS_PIXEL_BORDER,CANVAS_PIXEL_BORDER,CANVAS_PIXEL_BORDER));
      canvasPanel_.setLayout(new BorderLayout());
      MMStudio.getInstance().addMMBackgroundListener(canvasPanel_);
      canvasPanel_.setBackground(MMStudio.getInstance().getBackgroundColor());
      canvasPanel_.add(ic, BorderLayout.CENTER);
      JPanel leftPanel = new JPanel(new BorderLayout());
      leftPanel.add(canvasPanel_, BorderLayout.CENTER);
      leftPanel.add(subImageControls_, BorderLayout.PAGE_END);
      this.add(leftPanel, BorderLayout.CENTER);      
      
      JPanel rightPanel = new JPanel(new BorderLayout());
      rightPanel.add(nip, BorderLayout.CENTER);
      this.add(rightPanel, BorderLayout.LINE_END);

      positionWindow();
      this.setSize(new Dimension(displayPrefs_.getInt(WINDOWSIZEX, 600), displayPrefs_.getInt(WINDOWSIZEY, 600)));
      cmcPanel.initialize(disp);
      doLayout(); 

     
        // Propagate window resizing to the canvas
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
     
   public void resizeCanvas() {
      synchronized (canvasPanel_) {
         Dimension size = canvasPanel_.getSize();
         ((DisplayPlus) VirtualAcquisitionDisplay.getDisplay(plus_)).changeStack(
                 new ZoomableVirtualStack((ZoomableVirtualStack) plus_.getStack(),
                 size.width - 2 * CANVAS_PIXEL_BORDER, size.height - 2 * CANVAS_PIXEL_BORDER));
      }
   }
   
   
   /**
    * HACK: Override painting of the ImageWindow, because we need it to *not*
    * draw the canvas border, but still need it to draw the info text at the
    * top of the window. It can't draw the border properly anyway, since the
    * canvas is now contained in a JPanel and the canvas's size is such that
    * if any other entity draws the border, the canvas will "shadow" the 
    * border and make it largely invisible.
    */
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
   
   /**
    * sets the position of the window, based on the position of the last
    * closed window.
    * 
    */
   private void positionWindow() {
      Point location = getLocation();

      // Make sure the window is fully on the screen
      Rectangle rect = GUIUtils.getMaxWindowSizeForPoint(location.x, location.y);
      if (location.x + getWidth() > rect.x + rect.width) {
         // We're running off the right side of the screen, so pull back to
         // the left.
         location.x -= (location.x + getWidth() - rect.x - rect.width);
      }
      if (location.y + getHeight() > rect.y + rect.height) {
         // We're running off the bottom of the screen, so pull back to the
         // top.
         location.y -= (location.y + getHeight() - rect.y - rect.height);
      }

      setLocation(location);
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
      //ovverride this to prevent infinte loop of window resizing...
   }
}
