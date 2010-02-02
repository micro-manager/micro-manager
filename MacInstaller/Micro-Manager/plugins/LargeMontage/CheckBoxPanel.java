/**
* Copyright (c) 2005-2007 Vilppu Tuominen (vtuo@iki.fi)
* University of Tampere, Institute of Medical Technology
*
* http://iki.fi/vtuo/software/largemontage/
*
* This program is free software; you can redistribute it and/or modify it
* under the terms of the GNU General Public License as published by the
* Free Software Foundation; either version 2 of the License, or (at your
* option) any later version.
*
* This program is distributed in the hope that it will be useful, but
* WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
* General Public License for more details.
*
* You should have received a copy of the GNU General Public License along
* with this program; if not, write to the Free Software Foundation, Inc.,
* 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
*
*/

import java.awt.Checkbox;
import java.awt.ComponentOrientation;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Panel;


/**
 * A simple Panel with checkboxes that is used within the main GUI.
 * 
 * NOTE: this class is created with the Visual Editor, hence the
 * rather messy code.
 */
public class CheckBoxPanel extends Panel {

	private static final long serialVersionUID = 1L;

	private Checkbox y_overlap = null;
	private Checkbox x_shift = null;
	private Checkbox y_shift = null;
	private Checkbox snake = null;
	private Checkbox special = null;
	private Checkbox sws = null;
	private Checkbox verbose = null;

	/**
	 * This is the default constructor
	 */
	public CheckBoxPanel() {
		super();
		initialize();
	}

	/**
	 * This method initializes this
	 * 
	 * @return void
	 */
	private void initialize() {
		GridBagConstraints gridBagConstraints6 = new GridBagConstraints();		
		gridBagConstraints6.gridx = 0;
		gridBagConstraints6.anchor = GridBagConstraints.WEST;
		gridBagConstraints6.gridy = 3;
		GridBagConstraints gridBagConstraints5 = new GridBagConstraints();
		gridBagConstraints5.gridx = 1;
		gridBagConstraints5.anchor = GridBagConstraints.WEST;
		gridBagConstraints5.gridy = 2;
		GridBagConstraints gridBagConstraints4 = new GridBagConstraints();
		gridBagConstraints4.gridx = 1;
		gridBagConstraints4.anchor = GridBagConstraints.WEST;
		gridBagConstraints4.gridy = 1;
		GridBagConstraints gridBagConstraints3 = new GridBagConstraints();
		gridBagConstraints3.gridy = 0;
		gridBagConstraints3.anchor = GridBagConstraints.WEST;
		gridBagConstraints3.gridx = 1;
		GridBagConstraints gridBagConstraints2 = new GridBagConstraints();
		gridBagConstraints2.gridx = 0;
		gridBagConstraints2.anchor = GridBagConstraints.WEST;
		gridBagConstraints2.gridy = 2;
		GridBagConstraints gridBagConstraints1 = new GridBagConstraints();
		gridBagConstraints1.gridx = 0;
		gridBagConstraints1.anchor = GridBagConstraints.WEST;
		gridBagConstraints1.gridy = 1;
		GridBagConstraints gridBagConstraints = new GridBagConstraints();
		gridBagConstraints.gridy = 0;
		gridBagConstraints.anchor = GridBagConstraints.WEST;
		gridBagConstraints.gridx = 0;
		this.setLayout(new GridBagLayout());
		this.setSize(321, 100);
		this.add(getY_overlap(), gridBagConstraints);
		this.add(getX_shift(), gridBagConstraints1);
		this.add(getY_shift(), gridBagConstraints2);
		this.add(getSnake(), gridBagConstraints3);
		this.add(getSpecial(), gridBagConstraints4);
		this.add(getSws(), gridBagConstraints5);
		this.add(getVerbose(), gridBagConstraints6);
	}

	/**
	 * This method initializes y_overlap	
	 * 	
	 * @return java.awt.Checkbox	
	 */
	private Checkbox getY_overlap() {
		if (y_overlap == null) {
			y_overlap = new Checkbox();
			y_overlap.setLabel("Y overlap registration    ");
			y_overlap.setName("y_overlap");
			y_overlap.setComponentOrientation(ComponentOrientation.UNKNOWN);
		}
		return y_overlap;
	}

	/**
	 * This method initializes x_shift	
	 * 	
	 * @return java.awt.Checkbox	
	 */
	private Checkbox getX_shift() {
		if (x_shift == null) {
			x_shift = new Checkbox();
			x_shift.setLabel("X shift registration");
			x_shift.setName("x_shift");
		}
		return x_shift;
	}

	/**
	 * This method initializes y_shift	
	 * 	
	 * @return java.awt.Checkbox	
	 */
	private Checkbox getY_shift() {
		if (y_shift == null) {
			y_shift = new Checkbox();
			y_shift.setLabel("Y shift registration");
			y_shift.setName("y_shift");
		}
		return y_shift;
	}

	/**
	 * This method initializes snake	
	 * 	
	 * @return java.awt.Checkbox	
	 */
	private Checkbox getSnake() {
		if (snake == null) {
			snake = new Checkbox();
			snake.setLabel("Snake-like row ordering");
			snake.setName("snake");
		}
		return snake;
	}

	/**
	 * This method initializes special	
	 * 	
	 * @return java.awt.Checkbox	
	 */
	private Checkbox getSpecial() {
		if (special == null) {
			special = new Checkbox();
			special.setLabel("Special source numbering");
			special.setName("special");
		}
		return special;
	}

	/**
	 * This method initializes sws	
	 * 	
	 * @return java.awt.Checkbox	
	 */
	private Checkbox getSws() {
		if (sws == null) {
			sws = new Checkbox();
			sws.setLabel("Predefined SWS file");
			sws.setName("sws");
		}
		return sws;
	}

	/**
	 * This method initializes verbose	
	 * 	
	 * @return java.awt.Checkbox	
	 */
	private Checkbox getVerbose() {
		if (verbose == null) {
			verbose = new Checkbox();
			verbose.setLabel("Verbose output");
			verbose.setName("verbose");
		}
		return verbose;
	}
	
	public boolean yOverlapChecked() {
		return y_overlap.getState();
	}
	
	public boolean xShiftChecked() {
		return x_shift.getState();
	}
	
	public boolean yShiftChecked() {
		return y_shift.getState();
	}
	
	public boolean snakeChecked() {
		return snake.getState();
	}
	
	public boolean specialChecked() {
		return special.getState();
	}
	
	public boolean swsChecked() {
		return sws.getState();
	}
	
	public boolean verboseChecked() {
		return verbose.getState();
	}	

}  //  @jve:decl-index=0:visual-constraint="17,5"
