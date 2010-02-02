package org.micromanager.image5d;

import ij.IJ;
import ij.ImageJ;
import ij.plugin.LUT_Editor;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Canvas;
import java.awt.Checkbox;
import java.awt.CheckboxGroup;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Panel;
import java.awt.Rectangle;
import java.awt.ScrollPane;
import java.awt.Scrollbar;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;


/*
 * Created on 15.05.2005
 */

/**
 * ChannelControl contains one (sub-)Panel ("selectorPanel"), which contains a Button, 
 * which opens the color selection menu (grayed out in ONE_CHANNEL_GRAY mode) and a Choice 
 * "displayCoice", that controls the display mode (1 channel, overlay). The button and the
 * Coice are in a sub-Panel, which is in BorderLayout.NORTH. Additionally the selectorPanel
 * contains either a scrollbar (1 ch) or a checkbox group (overlay) in BorderLayout.CENTER. 
 * The selectorPanel is in BorderLayout.CENTER of the main panel.
 * The color selection menu is in BorderLayout.EAST of the main panel.
 */
public class ChannelControl extends Panel implements ItemListener,
		AdjustmentListener, ActionListener {
   private static final long serialVersionUID = 852398209561061019L;
   Image5DWindow win;
	Image5D i5d;
	ImageJ ij;
	
	Choice displayChoice;
	int displayMode;
	
	Panel selectorPanel;
	public ScrollbarWithLabel scrollbarWL;
	Scrollbar scrollbar;
	ChannelSelectorOverlay channelSelectorOverlay;
	
	Button colorButton;
	boolean colorChooserDisplayed;
	ChannelColorChooser cColorChooser;

	int nChannels;
	int currentChannel;

	public static final int ONE_CHANNEL_GRAY = 0;
	public static final int ONE_CHANNEL_COLOR = 1;
	public static final int OVERLAY = 2;
    public static final String[] displayModes = {"gray", "color", "ovl"};

	public static final String BUTTON_ACTIVATE_COLOR_CHOOSER = "Color";
	public static final String BUTTON_DEACTIVATE_COLOR_CHOOSER = "Color";
	
	public ChannelControl(Image5DWindow win) {
		super(new BorderLayout(5, 5));
		this.win = win;
		this.i5d = (Image5D)win.getImagePlus();
		this.ij = IJ.getInstance();
		
		currentChannel = i5d.getCurrentChannel();
		
		if (ij != null)
			addKeyListener(ij);

		// selectorPanel: contains the colorButton, displayChoice and
		// channel selector: scrollbar or channelSelectorOverlay.
		selectorPanel = new Panel(new BorderLayout(0,0));
		add(selectorPanel, BorderLayout.CENTER);
	
		Panel subPanel = new Panel(new GridLayout(2, 1, 1, 1));
		
		// colorButton: makes colorChooser visible/invisible
		colorButton = new Button(BUTTON_ACTIVATE_COLOR_CHOOSER);
		colorButton.setEnabled(true);
		colorChooserDisplayed = false;
		subPanel.add(colorButton);
		colorButton.addActionListener(this);
		if (ij != null)
			colorButton.addKeyListener(ij);
		
		// displayChoice: selects gray, color or overlay
		displayChoice = new Choice();
		displayChoice.add(displayModes[ONE_CHANNEL_GRAY]);
		displayChoice.add(displayModes[ONE_CHANNEL_COLOR]);
		displayChoice.add(displayModes[OVERLAY]);

		displayChoice.select(OVERLAY);
		displayMode = OVERLAY;
		subPanel.add(displayChoice);
		displayChoice.addItemListener(this);
		if (ij != null)
			displayChoice.addKeyListener(ij);
		
		selectorPanel.add(subPanel, BorderLayout.NORTH);

		// channelSelectorOverlay: to select current and currently displayed channels in overlay mode.
		addChannelSelectorOverlay();
		// add ij as KeyListener itself
		
		// scrollbar: select current channel in one channel modes
		addScrollbar();
		if (ij != null)
			scrollbar.addKeyListener(ij);
		
		// initially hidden Panel to select the ColorMap of the currently active channel.
		cColorChooser = new ChannelColorChooser(this);
		// add ij as KeyListener itself
	}

	void addScrollbar() {
		nChannels = i5d.getNChannels();
		if (scrollbarWL == null) {
			scrollbarWL = new ScrollbarWithLabel(Scrollbar.VERTICAL, 1,
					1, 1, nChannels + 1, i5d.getDimensionLabel(2));
			scrollbar = scrollbarWL.getScrollbar();
			scrollbar.addAdjustmentListener(this);
		} else {
			scrollbar.setMaximum(nChannels+1);
		}

		int blockIncrement = nChannels / 10;
		if (blockIncrement < 1)
			blockIncrement = 1;
		scrollbar.setUnitIncrement(1);
		scrollbar.setBlockIncrement(blockIncrement);

		if(channelSelectorOverlay!=null)
			selectorPanel.remove(channelSelectorOverlay);
		selectorPanel.add(scrollbarWL, BorderLayout.CENTER);
		win.pack();
		selectorPanel.repaint();
	}

	void addChannelSelectorOverlay() {
		if (channelSelectorOverlay == null) {
			channelSelectorOverlay = new ChannelSelectorOverlay(this);
		} 
		
		if(scrollbarWL!=null)
			selectorPanel.remove(scrollbarWL);
		selectorPanel.add(channelSelectorOverlay, BorderLayout.CENTER);
		win.pack();
		selectorPanel.repaint();
	}
	
	
	public int getNChannels() {
		return nChannels;
	}

	public void setNChannels(int nChannels) {
		if (nChannels < 1)
			return;
		if (scrollbar != null) {
			int max = scrollbar.getMaximum();
			if (max != (nChannels + 1))
				scrollbar.setMaximum(nChannels + 1);
		}
	}

//	/**
//	 * For changing the currently selected channel as displayed by the controls.
//	 * This method has no effect on the current channel in the Image5D.
//	 * currentChannel ranges from 1 to nChannels.
//	 */
//	public void setChannel(int currentChannel) {
//		// update nChannels and according control elements
//		if (currentChannel > nChannels || currentChannel < 1
//				|| this.channel == currentChannel)
//			return;
//		this.channel = currentChannel;
//		if (scrollbar != null) {
//			scrollbar.setValue(currentChannel);
//		}
//	}

	public int getCurrentChannel() {
		return currentChannel;
	}

	public Dimension getMinimumSize() {
	    int width = selectorPanel.getMinimumSize().width;
	    if (colorChooserDisplayed) {
	        width += cColorChooser.getMinimumSize().width;
	    }
				
		int height = selectorPanel.getMinimumSize().height;
	    if (colorChooserDisplayed) {
	        height = Math.max(height, cColorChooser.getMinimumSize().height);
	    }
	    
		return new Dimension(width, height);
	}

	public Dimension getPreferredSize() {
	    int width = selectorPanel.getPreferredSize().width;
	    if (colorChooserDisplayed) {
	        width += cColorChooser.getPreferredSize().width;
	    }
				
		int height = selectorPanel.getPreferredSize().height;
	    if (colorChooserDisplayed) {
	        height = Math.max(height, cColorChooser.getPreferredSize().height);
	    }
	    
		return new Dimension(width, height);
	}

	public void itemStateChanged(ItemEvent e) {
		if (e.getSource() == displayChoice) {
			int mode = displayChoice.getSelectedIndex();
			if (mode==displayMode) {
				return;
			} 
						
			i5d.setDisplayMode(mode);
			updateChannelSelector();	
		}
	}

	/** Sets the displayMode as seen in the channelControl. Doesn't do anything to the Image5D.
	 */
	public synchronized void setDisplayMode(int mode) {
		if (mode == displayMode) {
			return;
		} 
		
		if (mode == ONE_CHANNEL_GRAY) {
			if (colorChooserDisplayed) {
				remove(cColorChooser);
				colorButton.setLabel(BUTTON_ACTIVATE_COLOR_CHOOSER);
				colorChooserDisplayed = false;
			} 
			colorButton.setEnabled(false);
			addScrollbar();
			displayMode = mode;
			
		} else if (mode == ONE_CHANNEL_COLOR) {
			colorButton.setEnabled(true);
			addScrollbar();
			displayMode = mode;
			
		} else if (mode == OVERLAY) {
			colorButton.setEnabled(true);
			addChannelSelectorOverlay();	
			displayMode = mode;
			
		} else {
		    throw new IllegalArgumentException("Unknown display mode: "+ mode);
		}
		displayChoice.select(mode);
	    
	}
	
	public int getDisplayMode() {
		return displayMode;
	}

	/** Executed if scrollbar has been moved.
	 * 
	 */
	public void adjustmentValueChanged(AdjustmentEvent e) {
		if (scrollbar != null && e.getSource() == scrollbar) {
			currentChannel = scrollbar.getValue();
			channelChanged(currentChannel);
		}
	}
	
	/** Called by ChannelSelectorOverlay if current channel has been changed.
	 * 
	 */
	public void channelChanged(int newChannel) {
	    currentChannel = newChannel;
	    win.channelChanged();
	    cColorChooser.channelChanged();
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 */
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == colorButton) {
			if (colorChooserDisplayed) {
				remove(cColorChooser);
				colorButton.setLabel(BUTTON_ACTIVATE_COLOR_CHOOSER);
				colorChooserDisplayed = false;
				win.pack();
			} else {
				add(cColorChooser, BorderLayout.EAST);
				colorButton.setLabel(BUTTON_DEACTIVATE_COLOR_CHOOSER);
				colorChooserDisplayed = true;
				win.pack();
			}
		}
	}

	/** Updates nChannels, currentChannel and colors of channels from values in Image5D. 
	 */
	public void updateChannelSelector() {	
        currentChannel = i5d.getCurrentChannel();
	    // scrollbar
		int size = i5d.getNChannels();
		int max = scrollbar.getMaximum();
		if (max!=(size+1))
		    scrollbar.setMaximum(size+1);
		scrollbar.setValue(i5d.getCurrentChannel());		
		
		// ChannelSelectorOverlay
		channelSelectorOverlay.updateFromI5D();
        
        // ChannelColorChooser
        cColorChooser.channelChanged();
		win.pack();
	}

	/** Contains a radio button and a check button for each channel to select the current channel
	 * and toggle display of this channel. The background color of the buttons corresponds to 
	 * the color of the channel (highest color in colormap).
	 * Also has a button to select all channels for display and deselect all.
	 * 
	 * @author Joachim Walter 
	 */
	protected class ChannelSelectorOverlay extends Panel implements ActionListener, ItemListener {
      private static final long serialVersionUID = 1539845011848213L;

      ChannelControl cControl;
		
		Panel allNonePanel;
		Button allButton;
		Button noneButton;

		ScrollPane channelPane;
		Panel channelPanel;
		
		Checkbox[] channelDisplayed;
		Checkbox[] channelActive;
		CheckboxGroup channelActiveGroup;
		
		public ChannelSelectorOverlay(ChannelControl cControl) {
			super(new BorderLayout());
			this.cControl = cControl;
			
			// Panel with "All" and "None" Buttons in NORTH of this control.
			allNonePanel = new Panel(new GridLayout(1, 2));
			allButton = new Button("All");
			allButton.addActionListener(this);
			allNonePanel.add(allButton);
			noneButton = new Button("None");
			noneButton.addActionListener(this);
			allNonePanel.add(noneButton);
			add(allNonePanel, BorderLayout.NORTH);
			
			// Pass key hits on to ImageJ
			if (ij != null) {
			    addKeyListener(ij);
				allButton.addKeyListener(ij);
				noneButton.addKeyListener(ij);		
			}
			
			buildChannelSelector();

		}

        protected void buildChannelSelector() {			
			// ScrollPane, which contains selectors for all channels in CENTER of control;
			// ScrollPane doesn't take LayoutManager, so add a panel for this.
			channelPane = new ScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
			channelPanel = new Panel();
			channelPane.add(channelPanel);
			add(channelPane, BorderLayout.CENTER);	
			
			// Pass key hits on to ImageJ
			if (ij != null) {
				channelPane.addKeyListener(ij);	
				channelPanel.addKeyListener(ij);
			}
			
			nChannels = 0;
			updateFromI5D();				
		}
	
        
		/** Updates number of channels, selected and displayed channels from Image5D.
         */
        /*        
         * 	Is called from constructor (via buildChannelSelector) and from updateChannelSelector().
         */
        public void updateFromI5D() {
            // If nChannels changed, build new channelPanel.
            if (cControl.nChannels != i5d.getNChannels()) {
                cControl.nChannels = i5d.getNChannels();

				channelPane.getVAdjustable().setUnitIncrement(20);
				            			
				channelPanel.removeAll();
				channelPanel.setLayout(new GridLayout(nChannels, 1));
	
				channelActiveGroup = new CheckboxGroup();
				channelDisplayed = new Checkbox[nChannels];
				channelActive = new Checkbox[nChannels];
			
				for (int i=0; i<nChannels; ++i) {
					Panel p = new Panel(new BorderLayout());
					channelActive[i] = new Checkbox();
					channelActive[i].setCheckboxGroup(channelActiveGroup);
					channelActive[i].addItemListener(this);
					p.add(channelActive[i], BorderLayout.WEST);		
					
					channelDisplayed[i] = new Checkbox();
					channelDisplayed[i].addItemListener(this);
					p.add(channelDisplayed[i], BorderLayout.CENTER);
					channelPanel.add(p);
					
					// Pass key hits on to ImageJ
					if (ij != null) {
					    channelActive[i].addKeyListener(ij);	
					    channelDisplayed[i].addKeyListener(ij);	
					}
				}
            }
            
            // Update ChannelPanel from Image5D.
            for (int i=0; i<nChannels; ++i) {
				channelActive[i].setBackground(new Color(i5d.getChannelDisplayProperties(i+1).getColorModel().getRGB(255)));
				channelActive[i].setForeground(Color.black);
				channelActive[i].repaint();

                channelDisplayed[i].setLabel(i5d.getChannelCalibration(i+1).getLabel());
                
				channelDisplayed[i].setState(i5d.getChannelDisplayProperties(i+1).isDisplayedInOverlay());
				channelDisplayed[i].setBackground(new Color(i5d.getChannelDisplayProperties(i+1).getColorModel().getRGB(255)));
				channelDisplayed[i].setForeground(Color.black);
				channelDisplayed[i].repaint();
            }
			
			channelActive[(i5d.getCurrentChannel()-1)].setState(true);			
        }
        
		
        public Dimension getMinimumSize() {
            int widthButtons = allNonePanel.getMinimumSize().width;
            int widthChecks = channelPane.getMinimumSize().width; 
            int width = Math.max(widthButtons, widthChecks);
            
            int height = allNonePanel.getMinimumSize().height;
            height += cControl.nChannels*channelDisplayed[0].getMinimumSize().height;
            
            return new Dimension(width, height);
        }
        public Dimension getPreferredSize() {
            int widthButtons = allNonePanel.getPreferredSize().width;
            int widthChecks = channelPane.getPreferredSize().width; 
            int width = Math.max(widthButtons, widthChecks);
            
            int height = allNonePanel.getPreferredSize().height;
            height += cControl.nChannels*channelDisplayed[0].getPreferredSize().height;
            
            return new Dimension(width, height);
        }
        
        
		/* (non-Javadoc)
		 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
		 */
		public void actionPerformed(ActionEvent e) {
		    if (e.getSource() == allButton) {
		        for (int i=0; i<nChannels; ++i) {
		            channelDisplayed[i].setState(true);
		            i5d.setDisplayedInOverlay(i+1, true);
	            }		            
				updateFromI5D();
	            i5d.updateImageAndDraw(); 
		    } else if (e.getSource() == noneButton) {
		        for (int i=0; i<nChannels; ++i) {
		            channelDisplayed[i].setState(false);
		            i5d.setDisplayedInOverlay(i+1, false);
	            }		            
				updateFromI5D();
	            i5d.updateImageAndDraw(); 
		    }
		}

		/* (non-Javadoc)
		 * @see java.awt.event.ItemListener#itemStateChanged(java.awt.event.ItemEvent)
		 */
		public void itemStateChanged(ItemEvent e) {
		    for (int i=0; i<nChannels; ++i) {
		        if (e.getItemSelectable() == channelDisplayed[i]) {
		            if (e.getStateChange() == ItemEvent.SELECTED) {
			            i5d.setDisplayedInOverlay(i+1, true);
		            } else if (e.getStateChange() == ItemEvent.DESELECTED) {
			            i5d.setDisplayedInOverlay(i+1, false);
		            }		            
					updateFromI5D();
		            i5d.updateImageAndDraw();
		            return;
		        }
		        if (e.getItemSelectable() == channelActive[i]) {
		            if (e.getStateChange() == ItemEvent.SELECTED) {
						currentChannel = i+1;
						cControl.channelChanged(currentChannel);
			            return;
		            }
		        }
		    }
		}

		
	}

	/** Panel to select the display color or colormap of the current channel.
	 * 	Contains the ChannelColorCanvas, where the user can select a color. 
	 * 	There is a checkbox "displayGrayBox" to temporarily switch the channel to 
	 * 	grayscale display without losing the display color.
	 * 	Pressing the Button "editLUTButton" opens the builtin LUT_Editor plugin for 
	 * 	defining more complex LUTs.
	 * 
	 * @author Joachim Walter
	 */
	protected class ChannelColorChooser extends Panel implements ActionListener, ItemListener {
      private static final long serialVersionUID = 7876644511007053476L;
      ChannelControl cControl;
		Checkbox displayGrayBox;
		Button editLUTButton;
		ChannelColorCanvas cColorCanvas;		
		
	    public ChannelColorChooser(ChannelControl cControl) {
	        super(new BorderLayout(5,5));
	        this.cControl = cControl;
	        
	        displayGrayBox = new Checkbox("Grayscale");
	        displayGrayBox.setState(i5d.getChannelDisplayProperties(cControl.currentChannel).isDisplayedGray());
	        displayGrayBox.addItemListener(this);
	        add(displayGrayBox, BorderLayout.NORTH);
	        
	        cColorCanvas = new ChannelColorCanvas(this);
	        add(cColorCanvas , BorderLayout.CENTER);
	        
	        editLUTButton = new Button("Edit LUT");
	        editLUTButton.addActionListener(this);
	        add(editLUTButton, BorderLayout.SOUTH);
	        

			if (ij != null) {
				addKeyListener(ij);
				displayGrayBox.addKeyListener(ij);
				cColorCanvas.addKeyListener(ij);
				editLUTButton.addKeyListener(ij);
			}
	    }

	    /** Sets the Color of the current channel to c in the Image5D.
	     * 
	     * @param c: Color
	     */
        public void setColor(Color c) {
            i5d.storeChannelProperties(cControl.currentChannel);
            i5d.getChannelDisplayProperties(cControl.currentChannel).setColorModel(ChannelDisplayProperties.createModelFromColor(c));
            i5d.restoreChannelProperties(cControl.currentChannel);
            
            i5d.updateAndDraw();
            cControl.updateChannelSelector();
            cColorCanvas.repaint();          	        
	    }

//        /** Called by ChannelLUTEditor after LUT change
//         */
//        public void lutWasEdited() {            
//            ColorModel cm = i5d.channelIPs[cControl.activeChannel-1].getColorModel();
//            i5d.cChannelProps[cControl.activeChannel-1].setColorModel(cm);
//            i5d.restoreChannelProperties(cControl.activeChannel);
//            
//            i5d.updateAndDraw();
//            cControl.updateChannelSelector();
//            cColorCanvas.repaint();            
//        }	    
	    
	    /** Called from ChannelControl when active channel changes.
         */
        public void channelChanged() {
            cColorCanvas.repaint();
            displayGrayBox.setState(i5d.getChannelDisplayProperties(cControl.currentChannel).isDisplayedGray());  
            
            cColorCanvas.setEnabled(!displayGrayBox.getState()); 
            editLUTButton.setEnabled(!displayGrayBox.getState()); 
            
        }
	    
	    
        public Dimension getMinimumSize() {
            int width = cColorCanvas.getMinimumSize().width;
            int height = displayGrayBox.getMinimumSize().height + 5 +
            				cColorCanvas.getMinimumSize().height + 5 +
            				editLUTButton.getMinimumSize().height;
            return new Dimension(width, height);
        }
        public Dimension getPreferredSize() {
            int width = cColorCanvas.getPreferredSize().width;
            int height = displayGrayBox.getPreferredSize().height + 5 +
            				cColorCanvas.getPreferredSize().height + 5 +
            				editLUTButton.getPreferredSize().height;
            return new Dimension(width, height);
        }

        // Edit LUT Button
        public void actionPerformed(ActionEvent e) {
            i5d.storeChannelProperties(cControl.currentChannel);    
            new LUT_Editor().run("");     
            ColorModel cm = i5d.channelIPs[cControl.currentChannel-1].getColorModel();
            i5d.getChannelDisplayProperties(cControl.currentChannel).setColorModel(cm);
            i5d.restoreChannelProperties(cControl.currentChannel);
            
            i5d.updateAndDraw();
            cControl.updateChannelSelector();
            cColorCanvas.repaint();   
        }


        // displayGray Checkbox
        public void itemStateChanged(ItemEvent e) {
            if (e.getItemSelectable() == displayGrayBox) {
                i5d.storeChannelProperties(cControl.currentChannel);
                i5d.getChannelDisplayProperties(cControl.currentChannel).setDisplayedGray(displayGrayBox.getState());
                i5d.restoreChannelProperties(cControl.currentChannel);
                i5d.updateImageAndDraw();
                
                cColorCanvas.setEnabled(!displayGrayBox.getState());
                editLUTButton.setEnabled(!displayGrayBox.getState());
            }            
        }

	}

/**
 * Canvas to select the display color of a channel.
 * The Canvas contains a rectangle of colored squares. The hue of the squares changes
 * in vertical direction. The saturation in horizontal direction. "Value" can be changed
 * by changing contrast and brightness of the channel.
 * The Canvas also contains an image of the current LUT. 
 *  
 * @author Joachim Walter
 */
	protected class ChannelColorCanvas extends Canvas implements MouseListener{
   private static final long serialVersionUID = -401646017822187364L;

      ChannelColorChooser cColorChooser;
	    
	    BufferedImage lutImage;
	    BufferedImage colorPickerImage;
	    BufferedImage commonsImage;
	    
        int lutWidth = 15;
        int lutHeight = 129;
	    
        int cpNHues = 24;
        int cpNSats = 8;
        int cpRectWidth = 10;
        int cpRectHeight = 10;
        int cpWidth;
        int cpHeight;             

//        int commonsWidth = 105;
//        int commonsHeight = 15;
        int commonsWidth = 0;
        int commonsHeight = 0;

        int hSpacer = 5;
        int vSpacer = 0;
//        int vSpacer = 5;
        
        int canvasWidth;
        int canvasHeight;
   
        
	    public ChannelColorCanvas(ChannelColorChooser cColorChooser) {
	        super();
	        
	        this.cColorChooser = cColorChooser;
			
	        setForeground(Color.black);
	        setBackground(Color.white);
	        
	        cpWidth = cpRectWidth*cpNSats;
	        cpHeight = cpRectHeight*cpNHues;

	        canvasWidth = lutWidth+2+hSpacer+cpWidth+2;
	        canvasHeight = cpHeight+2+vSpacer+commonsHeight;
	        
	        setSize(canvasWidth, canvasHeight);

	        lutImage = new BufferedImage(lutWidth+2, lutHeight+2, BufferedImage.TYPE_INT_RGB);
	        colorPickerImage = new BufferedImage(cpWidth+2, cpHeight+2, BufferedImage.TYPE_INT_RGB);
//	        commonsImage = new BufferedImage(commonsWidth, commonsHeight, BufferedImage.TYPE_INT_RGB);

	        drawLUT();
	        drawColorPicker();
//	        drawCommons();
	        
	        addMouseListener(this);
	        
	    }
	
	    /** Draws the LUT of the currently displayed channel into a rectangle.
	     */
	    void drawLUT() {
	        Graphics g = lutImage.getGraphics();
	        int[] rgb = new int[256];
	        
	        ColorModel currentLUT = i5d.getChannelDisplayProperties(cColorChooser.cControl.currentChannel).getColorModel();
	        if (!(currentLUT instanceof IndexColorModel)) 
	            throw new IllegalArgumentException("Color Model has to be IndexColorModel.");
	        
	        ((IndexColorModel)currentLUT).getRGBs(rgb);
	        
            g.setColor(Color.black);
	        g.drawRect(0, 0, lutWidth+2, lutHeight+2);
	        
	        // check consistency with lutHeight!
            g.setColor(new Color(rgb[255]));
            g.drawLine(1, 1, lutWidth, 1);
	        for (int i=0; i<128; i++) {
	            g.setColor(new Color(rgb[255-i*2]));
	            g.drawLine(1, i+2, lutWidth, i+2);
	        }
	    }

	    /** Draws the color picker image: a rectangle of small squares all with different hue or saturation.
	     */
	    void drawColorPicker() {       
	        Graphics g = colorPickerImage.getGraphics();
	        
	        float hue=0;
	        float sat=0;
	        g.setColor(Color.black);
	        g.drawRect(0, 0, cpWidth+2, cpHeight+2);
	        for (int h=0; h<cpNHues; h++) {
	            for (int s=0; s<cpNSats; s++) {
	                hue = h / (float) cpNHues;
	                sat = s / (float) (cpNSats-1);
	                g.setColor(Color.getHSBColor(hue, sat, 1f));
	                g.fillRect(cpWidth - (s+1)*cpRectWidth +1, h*cpRectHeight +1 , cpRectWidth, cpRectHeight);
	            }
	        }
	        
	        
	    }
	    
//		Commons: larger squares with commonly used colors.	    
//	    void drawCommons() {       
//	        Graphics g = commonsImage.getGraphics();
//	        g.setColor(Color.red);
//	        g.fillRect(0, 0, commonsHeight, commonsHeight);
//	        
//	        g.setColor(Color.green);
//	        g.fillRect(commonsHeight, 0, commonsHeight, commonsHeight);
//	        
//	        g.setColor(Color.blue);
//	        g.fillRect(2*commonsHeight, 0, commonsHeight, commonsHeight);
//
//	        g.setColor(Color.cyan);
//	        g.fillRect(3*commonsHeight, 0, commonsHeight, commonsHeight);
//	        
//	        g.setColor(Color.magenta);
//	        g.fillRect(4*commonsHeight, 0, commonsHeight, commonsHeight);
//	        
//	        g.setColor(Color.yellow);
//	        g.fillRect(5*commonsHeight, 0, commonsHeight, commonsHeight);
//	        
//	        g.setColor(Color.white);
//	        g.fillRect(6*commonsHeight, 0, commonsHeight, commonsHeight);
//	    }

	    
	    public void paint(Graphics g) {
	        g.drawImage(colorPickerImage, lutWidth+2+hSpacer, 0, cpWidth+2, cpHeight+2, null);
	        drawLUT();
	        g.drawImage(lutImage, 0, 0, lutWidth+2, lutHeight+2, null);
//	        g.drawImage(commonsImage, 0, cpHeight+vSpacer, commonsWidth, commonsHeight, null);
	    }	      
	    
	    
        public Dimension getMinimumSize() {
            return new Dimension(canvasWidth, canvasHeight);
        }
        public Dimension getPreferredSize() {
            return new Dimension(canvasWidth, canvasHeight);  
        }

        /** Change color, if user presses in one of the colored squares.
         */
	    public void mousePressed(MouseEvent e) {

	        int x = e.getX();
	        int y = e.getY();
	        
	        Rectangle colorPickerRect = new Rectangle(lutWidth+2+hSpacer+1, 1, cpWidth, cpHeight);

	        if (colorPickerRect.contains(x, y)) {
	            int xCP = x-(lutWidth+2+hSpacer);
	            int yCP = y;
	            Color c = new Color(colorPickerImage.getRGB(xCP, yCP));
	            
	            cColorChooser.setColor(c);          
	        }
	    }
	    
        public void mouseClicked(MouseEvent e) {         
        }
        public void mouseReleased(MouseEvent e) {            
        }
        public void mouseEntered(MouseEvent e) {   
        }
        public void mouseExited(MouseEvent e) {
        }

	}
		
}
