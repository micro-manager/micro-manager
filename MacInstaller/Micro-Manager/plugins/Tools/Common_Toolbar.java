import ij.*;
import ij.gui.*;
import ij.plugin.*;
import ij.process.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.Math.*;
import java.awt.image.*;
import java.io.File;

//core code from G. Landini AlignRGB panes

public class Common_Toolbar extends Dialog
implements ActionListener, AdjustmentListener, ItemListener, WindowListener, Runnable {
	private Thread threadProcess = null;

	public Common_Toolbar () {
		super(new Frame(), "Common Tasks");
		if (IJ.versionLessThan("1.21a"))
			return;

		
		doDialog();
	}
public void run() {
		// You will never be here...
	}


	/*
	 * Build the dialog box.
	 */
	private GridBagLayout 	layout;
	private GridBagConstraints 	constraint;
	private Button  bt1;
	private Button  bt2;
	private Button  bt3;
	private Button  bt4;
	private Button  bt5;
	private Button  bt6;
	private Button bt7;
	private Button bt8;
	private Button bt9;

//add more buttons here

	private Button  btClose;
	private int clicks=0;
	private int clicks2=0;

	private void doDialog() {
	// Layout
		int xPos = 3650;
		int yPos=3650;
		layout = new GridBagLayout();
		constraint = new GridBagConstraints();
		bt1= new Button("Bin Image");
		bt2= new Button("RGB Merge");
		bt3= new Button("Subtract Background");
		bt4= new Button("Enhance Contrast");
		bt5= new Button("Measure");
		bt6= new Button("Hot LUT");
		bt7= new Button ("Pale LUT");
		bt8 = new Button("Gamma");
		bt9 = new Button("Add Ramp");

//add more buttons here

		btClose = new Button("Close Toolbar");

	// Panel parameters
		Panel pnMain = new Panel();

		pnMain.setLayout(layout);
	//	pnMain.setPosition(xPos,yPos);

		addComponent(pnMain, 0, 0, 1, 1, 5, bt1);
		addComponent(pnMain, 1, 0, 1, 1, 5, bt2);
		addComponent(pnMain, 2, 0, 1, 1, 5, bt3);
		addComponent(pnMain, 3, 0, 1, 1, 5, bt4);
		addComponent(pnMain, 4, 0, 1, 1, 5, bt5);
		addComponent(pnMain, 5, 0, 1, 1, 5, bt6);
		addComponent(pnMain, 6, 0, 1, 1, 5, bt7);
		addComponent(pnMain, 7, 0, 1, 1, 5, bt8);
		addComponent(pnMain, 8, 0, 1, 1, 5, bt9);
//add more buttons here
 
		addComponent(pnMain, 9, 0, 1, 1, 5, btClose);

	// Add Listeners
		bt1.addActionListener(this);
		bt2.addActionListener(this);
		bt3.addActionListener(this);
		bt4.addActionListener(this);
		bt5.addActionListener(this);
		bt6.addActionListener(this);
		bt7.addActionListener(this);
		bt8.addActionListener(this);
		bt9.addActionListener(this);
		btClose.addActionListener(this);

	// Build panel
		add(pnMain);
		pack();
		setResizable(false);
		//GUI.center(this);	
		
		
		setVisible(true);

		IJ.wait(250); // work around for Sun/WinNT bug
	}

	final private void addComponent(
	final Panel pn,
	final int row, final int col,
	final int width, final int height,
	final int space,
	final Component comp) {
		constraint.gridx = col;
		constraint.gridy = row;
		constraint.gridwidth = width;
		constraint.gridheight = height;
		constraint.anchor = GridBagConstraints.NORTHWEST;
		constraint.insets = new Insets(space, space, space, space);
		constraint.weightx = IJ.isMacintosh()?90:100;
		constraint.fill = constraint.HORIZONTAL;
		layout.setConstraints(comp, constraint);
		pn.add(comp);
	}

	/*
	 * Implements the listeners
	 */

    public synchronized void adjustmentValueChanged(AdjustmentEvent e) {
		
		notify();
    }

public synchronized void itemStateChanged(ItemEvent e) {
		}

	public synchronized  void actionPerformed(ActionEvent e) {
		    String lutDir = System.getProperty("user.dir")+File.separator+"lut"+File.separator;
      
		if (e.getSource() == btClose) {
			dispose();
		
		}
	
	else if (e.getSource() ==bt1)  IJ.run("Binner ");
	else if (e.getSource() == bt2)  IJ.run("RGB Merge...");
	else if (e.getSource() == bt3)  IJ.run("Subtract Background...");
	else if (e.getSource() == bt4) IJ.run("Enhance Contrast");
	else if (e.getSource() == bt5) IJ.run("Measure");
	else if (e.getSource() == bt6) {
				clicks++;
				if (clicks==1) {IJ.run("LUT... ", "open="+"'"+lutDir+"Red Hot.lut"+"'");}
				else if (clicks==2) IJ.run("LUT... ", "open="+"'"+lutDir+"Green Hot.lut"+"'");
				else {IJ.run("LUT... ", "open="+"'"+lutDir+"Blue_Hot.lut"+"'"); clicks=0;}
				}
	else if (e.getSource() == bt7) {
				clicks2++;
				if (clicks2==1) {IJ.run("LUT... ", "open="+"'"+lutDir+"Red Pale.lut"+"'");}
				else if (clicks2==2) IJ.run("LUT... ", "open="+"'"+lutDir+"Green Pale.lut"+"'");
				else {IJ.run("LUT... ", "open="+"'"+lutDir+"Blue Pale.lut"+"'"); clicks2=0;}
				}
	else if (e.getSource() == bt8) IJ.run("Gamma...");
	else if (e.getSource() == bt9) IJ.run("Add Ramp");

	notify();
	}

	public void windowActivated(WindowEvent e) {
	}

	public void windowClosing(WindowEvent e) {
		dispose();
	}

	public void windowClosed(WindowEvent e) {
	}

	public void windowDeactivated(WindowEvent e) {
	}

	public void windowDeiconified(WindowEvent e){
	}

	public void windowIconified(WindowEvent e){
	}

	public void windowOpened(WindowEvent e){
	}
}
