package org.micromanager.slideexplorer;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import org.micromanager.utils.JavaUtils;



public class Window extends ImageWindow {
	protected static final long serialVersionUID = 1790742904373734003L;
	protected boolean fullscreen_;
	protected Canvas cvs_;
	private Display display_;
	private boolean escapeKeyReady_ = false;
	private Rectangle unmaximizedBounds_;
    private final ZoomControlPanel zcp_;
    private final ControlButtonsPanel cbp_;
	private KeyAdapter universalKeyAdapter_;

	Window(ImagePlus imgp, ImageCanvas cvs, Display display) {
		super(imgp, cvs);
		display_ = display;
		cvs_ = (Canvas) cvs;

		addComponentListener(new ComponentAdapter() {
			public void componentResized(ComponentEvent e) {
				cvs_.fitToWindow();
				cvs_.updateAfterPan();
                positionControls();
			}
		});
        setLayout(null);
        
        zcp_ = new ZoomControlPanel(display_);
        add(zcp_);


        cbp_ = new ControlButtonsPanel(display_);
        add(cbp_);

        positionControls();

		unmaximizedBounds_ = new Rectangle(Window.this.getBounds());
		
		this.addComponentListener(new ComponentAdapter() {
			public void componentResized(ComponentEvent e) {
				if (! fullscreen_)
					unmaximizedBounds_ = new Rectangle(Window.this.getBounds());
			}
			
			public void componentMoved(ComponentEvent e) {
				if (! fullscreen_)
					unmaximizedBounds_ = new Rectangle(Window.this.getBounds());
			}
		});


        addMyKeyListeners();

        this.setLayout(null);
	}

    public void positionControls() {
        Rectangle winBounds = this.getBounds();
        zcp_.setBounds(winBounds.width-150,winBounds.height-37,150,32);
        cbp_.setBounds(0,winBounds.height-37,785, 32);
    }

    public void paint(Graphics g) {


        zcp_.paint(zcp_.getGraphics());
        cbp_.paint(cbp_.getGraphics());


        drawInfo(g);
        Rectangle r = ic.getBounds();
        int extraWidth = MIN_WIDTH - r.width;
        int extraHeight = MIN_HEIGHT - r.height;
        if (extraWidth<=0 && extraHeight<=0 && !Prefs.noBorder && !IJ.isLinux())
            g.drawRect(r.x-1, r.y-1, r.width+1, r.height+1);

    }

    public void paintComponents(Graphics g) {
        zcp_.repaint();
        cbp_.repaint();
        super.paintComponents(g);
    }

    public void drawInfo(Graphics g) {
        
    }

	public void addMyKeyListeners() {
        universalKeyAdapter_ = new KeyAdapter() {
			public void keyPressed(KeyEvent e) {
                switch(e.getKeyCode()) {
                    case KeyEvent.VK_ESCAPE:
                        unfullscreen();
                        break;

                    //case KeyEvent.VK_SPACE:
                    //	fullscreen();
                    //    break;

                    case KeyEvent.VK_Z:
                        display_.showConfig();
                        break;

                    case KeyEvent.VK_R:
                        display_.showRoiManager();
                        break;

                    case KeyEvent.VK_A:
                        display_.acquireMosaics();
                        break;

                    case KeyEvent.VK_Q:
                        display_.clearRois();
                        break;
                }

			}
        };

		addMyKeyListener(this);
        addMyKeyListener(cvs_);

		escapeKeyReady_ = true;
	}

	public void addMyKeyListener(Component component) {
		component.addKeyListener(universalKeyAdapter_);
	}
	
	public void updateImage(ImagePlus imp) {
		if (imp!=this.imp)
			throw new IllegalArgumentException("imp!=this.imp");
		this.imp = imp;
		cvs_.fitToWindow();
		// ic.updateImage(imp);
		//     setLocationAndSize(true);
		//    pack();
		repaint();
	}

	private int getOverlappingArea(Rectangle rect1, Rectangle rect2) {
		int area;
		int left = Math.max(rect1.x, rect2.x);
		int top = Math.max(rect1.y, rect2.y);
		int right = Math.min(rect1.x + rect1.width, rect2.x + rect2.width);
		int bottom = Math.min(rect1.y + rect1.height, rect2.y + rect2.height);
		int width = Math.max(right - left,0); 
		int height = Math.max(bottom - top,0);
		area = width * height;
		return area;

	}

   public void unfullscreen() {
      if (fullscreen_) {
         // Add back title bar, etc.
         setVisible(false);
         dispose();

         setBackground(Color.white);
         zcp_.setBackground(Color.white);
         cbp_.setBackground(Color.white);

         setUndecorated(false);
         setVisible(true);

         setBounds(unmaximizedBounds_);

         fullscreen_ = false;
         cvs_.fitToWindow();
         this.toFront();
         this.requestFocus();
         positionControls();

         cbp_.updateControls();
      }
   }

	
	public void fullscreen() {
      if (!fullscreen_) {
         fullscreen_ = true;

         // Remove title bar, etc.
         setVisible(false);
         dispose();
         setUndecorated(true);

         setBackground(Color.black);
         zcp_.setBackground(Color.black);
         cbp_.setBackground(Color.black);

         GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
         GraphicsDevice[] devices = ge.getScreenDevices();

         int chosenDeviceIndex = -1;
         int maxArea = 0;
         int area;
         Rectangle maximizedBounds = null;

         for (int i = 0; i < devices.length; ++i) {
            GraphicsConfiguration config = devices[i].getDefaultConfiguration();
            Rectangle configBounds = config.getBounds();
            area = getOverlappingArea(configBounds, unmaximizedBounds_);
            if (area > maxArea) {
               chosenDeviceIndex = i;
               maxArea = area;
               maximizedBounds = configBounds;
            }

         }

         if (maximizedBounds != null) {
            setBounds(maximizedBounds);
         }

         if (! JavaUtils.isWindows()) {
            devices[chosenDeviceIndex].setFullScreenWindow(this);
         }

         setVisible(true);
         cbp_.updateControls();

         cvs_.fitToWindow();

         cbp_.updateControls();
         this.requestFocus();
      }

   }

	
	/** Overrides super.windClosing(WindowEvent e) to make sure that if user hits Ctrl-W,
	 * the slideexplorer stops and cleans the cache.
	 */
	public void windowClosing(WindowEvent e) {
		super.windowClosing(e);
		display_.shutdown();
	}

	/** Overrides super.close() to make sure that if user hits Ctrl-W,
	 * the slideexplorer stops and cleans the cache.
	 */
	public boolean close() {
        	display_.reactivateRoiManager();
		display_.shutdown();
		return super.close();
	}

	
	public boolean isFullscreen() {
		return fullscreen_;
	}

    void toggleFullscreen() {
        if (fullscreen_)
            unfullscreen();
        else
            fullscreen();
    }

    void updateControls() {
        cbp_.updateControls();
        zcp_.updateControls();
    }


}
