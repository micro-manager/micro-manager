package de.embl.rieslab.emu.swinglisteners;

import static org.junit.Assert.assertEquals;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.JToggleButton;

import org.junit.Test;

import de.embl.rieslab.emu.utils.EmuUtils;

/**
 * These are just a bunch of tests to remember whether changing the state of
 * a Swing JComponent fires the action listeners used in SwingUIListeners. This
 * is important to know in the UIPlugin in order to avoid repeated calls to the
 * MMProperty from ConfigurablePanel.propertyHashChanged(String, String).
 * 
 * @author Joran Deschamps
 *
 */
public class SwingComponentTests {

	/*
	 * Tests that calling jtogglebutton.setSelected(boolean) doesn't trigger the action listener.
	 */
	@Test
	public void testJToggleButton() {
		final JToggleButton tgl = new JToggleButton();
		final int[] vals = {10, 20};
		final AtomicInteger  i  = new AtomicInteger(vals[1]);
		
		tgl.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				AbstractButton abstractButton = (AbstractButton) actionEvent.getSource();
				boolean selected = abstractButton.getModel().isSelected();
				if (selected) {
					i.set(vals[0]);
				} else {
					i.set(vals[1]);				
				}
			}
		});
		
		assertEquals(vals[1], i.intValue());
		
		// now make the button selected
		tgl.setSelected(true);
		
		// check that the value didn't change.
		assertEquals(vals[1], i.intValue());
		
		// make the button unselected
		tgl.setSelected(false);
		assertEquals(vals[1], i.intValue());
		
		// now fake a user interaction
		tgl.doClick();
		
		// the value changed
		assertEquals(vals[0], i.intValue());
	}
	
	/*
	 * ItemListener are triggered by jtogglebutton.setSelected(boolean) 
	 */
	@Test
	public void testJToggleButtonItemState() {
		final JToggleButton tgl = new JToggleButton();
		final int[] vals = {10, 20};
		final AtomicInteger  i  = new AtomicInteger(vals[1]);
		
		tgl.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent ev){
				if (ev.getStateChange() == ItemEvent.SELECTED) {
					i.set(vals[0]);
				} else {
					i.set(vals[1]);				
				}
			}
		});
		
		assertEquals(vals[1], i.intValue());
		
		// now make the button selected
		tgl.setSelected(true);
		
		// check that the value didn't change.
		assertEquals(vals[0], i.intValue());
	}
	
	/*
	 * Verifies that calling jcombobox.setSelectedIndex(int) fires the actionListener.
	 */
	@Test
	public void testJCombobox() {
		final String[] s = {"sa", "sdsa", "2dsf"};
		final JComboBox<String> jcombobox = new JComboBox<String>(s);
		
		final AtomicInteger  i  = new AtomicInteger(0);
		
		jcombobox.addActionListener(new ActionListener(){
	    	public void actionPerformed(ActionEvent e){
	    		i.set(jcombobox.getSelectedIndex());
	    	}
        });
	
		assertEquals(0, i.intValue());
		
		// call setSelectedIndex
		jcombobox.setSelectedIndex(2); // calls the action listener
		
		// check that the value changed
		assertEquals(2, i.intValue());
		
		// same with setSelectedItem
		jcombobox.setSelectedItem(s[1]);
		assertEquals(1, i.intValue());
	}
	
	/*
	 * Tests that calling jslider.setValue(int) does not trigger (as expected) the mouse listener.
	 */
	@Test
	public void testJSlider() {
		final JSlider sld = new JSlider(); 
		final AtomicInteger  i  = new AtomicInteger(0);
		
		sld.addMouseListener(new MouseAdapter() {
			public void mouseReleased(MouseEvent e) {
				i.set(sld.getValue());
			}
		});
		
		assertEquals(0, i.intValue());
		
		// set slider value
		int n = 40;
		sld.setValue(n);
		
		// check that the value of the atomic integer has not changed
		assertEquals(0, i.intValue());
	
	}

	/*
	 * Checks that setting the text of jtextfield does not trigger the action listener
	 * and the focus listener.
	 */
	@Test
	public void testJTextField() {
		final JTextField txt = new JTextField("");
		final AtomicInteger  i  = new AtomicInteger(0);
		
		txt.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				String s = txt.getText();
				if (EmuUtils.isInteger(s)) {
					i.set(Integer.valueOf(s));
				}
			}
		});

		txt.addFocusListener(new FocusListener() {
			@Override
			public void focusGained(FocusEvent ex) {
			}

			@Override
			public void focusLost(FocusEvent ex) {
				String s = txt.getText();
				if (EmuUtils.isInteger(s)) {
					i.set(Integer.valueOf(s));
				}
			}
		});

		assertEquals(0, i.intValue());
		
		// set JTextField text
		int n = 40;
		txt.setText(String.valueOf(n));
		
		// check that the value of the atomic integer has not changed
		assertEquals(0, i.intValue());
	}
	
	// jspinner
}
