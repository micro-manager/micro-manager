package de.embl.rieslab.emu.ui.swinglisteners;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Enumeration;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import de.embl.rieslab.emu.ui.ConfigurablePanel;
import de.embl.rieslab.emu.ui.swinglisteners.actions.Action;
import de.embl.rieslab.emu.ui.swinglisteners.actions.UnparametrizedAction;
import de.embl.rieslab.emu.ui.uiproperties.SingleStateUIProperty;
import de.embl.rieslab.emu.ui.uiproperties.TwoStateUIProperty;
import de.embl.rieslab.emu.ui.uiproperties.UIPropertyType;
import de.embl.rieslab.emu.utils.EmuUtils;
import de.embl.rieslab.emu.utils.exceptions.IncorrectUIPropertyTypeException;

/**
 * This class holds static standard methods to link Swing components with UIProperties or actions. The methods trigger a UIProperty change or an action when
 * the user interacts with the JComponents.
 * 
 * @author Joran Deschamps
 *
 */
public class SwingUIListeners {

	/**
	 * Adds a Swing action listener to a JComboBox, causing the UIProperty {@code propertyKey} from {@code cp} to be updated
	 * with the JCombobox current value.
	 * 
	 * @param cp ConfigurablePanel that owns the property.
	 * @param propertyKey UIProperty to modify when the JComboBox changes.
	 * @param cbx JCombobox holding the different UIproperty states.
	 */
	public static void addActionListenerOnStringValue(final ConfigurablePanel cp, final String propertyKey, final JComboBox<String> cbx) {
		if(cp == null) {
			throw new NullPointerException("The ConfigurablePanel cannot be null.");
		}
		if(propertyKey == null) {
			throw new NullPointerException("The UIProperty's label cannot be null.");
		}
		if(cbx == null) {
			throw new NullPointerException("The JCombobox cannot be null.");
		}
		
		cbx.addActionListener(new ActionListener(){
	    	public void actionPerformed(ActionEvent e){
	    		String val = String.valueOf(cbx.getSelectedItem());
	    		cp.setUIPropertyValue(propertyKey,val);
	    	}
        });
	}
	
	/**
	 * Adds a Swing action listener to a JTextField value, causing the UIProperty {@code propertyKey} from {@code cp} to be updated
	 * with the JTextField value upon pressing the enter key.
	 * 
	 * @param cp ConfigurablePanel that owns the property.
	 * @param propertyKey UIProperty to modify when the JTextField changes.
	 * @param txtf JTextField the user interacts with.
	 */
	public static void addActionListenerOnStringValue(final ConfigurablePanel cp, final String propertyKey, final JTextField txtf) {
		if(cp == null) {
			throw new NullPointerException("The ConfigurablePanel cannot be null.");
		}
		if(propertyKey == null) {
			throw new NullPointerException("The UIProperty's label cannot be null.");
		}
		if(txtf == null) {
			throw new NullPointerException("The JTextField cannot be null.");
		}
		
		txtf.addActionListener(new ActionListener(){
	    	public void actionPerformed(ActionEvent e){
	    		cp.setUIPropertyValue(propertyKey,txtf.getText());
	    	}
        });
	}

	/**
	 * Adds a Swing action listener to a JComboBox, causing the UIProperty {@code propertyKey} from {@code cp} to be updated
	 * with the JCombobox currently selected index.
	 * 
	 * @param cp ConfigurablePanel that owns the property.
	 * @param propertyKey UIProperty to modify when the JComboBox changes.
	 * @param cbx JCombobox.
	 */
	public static void addActionListenerOnSelectedIndex(final ConfigurablePanel cp, final String propertyKey, final JComboBox<?> cbx) {
		if(cp == null) {
			throw new NullPointerException("The ConfigurablePanel cannot be null.");
		}
		if(propertyKey == null) {
			throw new NullPointerException("The UIProperty's label cannot be null.");
		}
		if(cbx == null) {
			throw new NullPointerException("The JCombobox cannot be null.");
		}
		
		cbx.addActionListener(new ActionListener(){
	    	public void actionPerformed(ActionEvent e){
	    		String val = String.valueOf(cbx.getSelectedIndex());
	    		cp.setUIPropertyValue(propertyKey,val);
	    	}
        });
	}
	
	/**
	 * Adds a Swing action listener to a ButtonGroup, causing the UIProperty {@code propertyKey} from {@code cp} to be updated
	 * with the ButtonGroup currently selected button index.
	 * 
	 * @param cp ConfigurablePanel that owns the property.
	 * @param propertyKey UIProperty to modify when the ButtonGroup selected button changes.
	 * @param group ButtonGroup containing the different buttons.
	 */
	public static void addActionListenerOnSelectedIndex(final ConfigurablePanel cp, final String propertyKey, final ButtonGroup group) {
		if(cp == null) {
			throw new NullPointerException("The ConfigurablePanel cannot be null.");
		}
		if(propertyKey == null) {
			throw new NullPointerException("The UIProperty's label cannot be null.");
		}
		if(group == null) {
			throw new NullPointerException("The ButtonGroup cannot be null.");
		}
		
		Enumeration<AbstractButton> enm = group.getElements();
		int counter = 0;
		while(enm.hasMoreElements()) {
			final int pos = counter;
			
			AbstractButton btn = enm.nextElement();		
			btn.addActionListener(new ActionListener(){
		    	public void actionPerformed(ActionEvent e){
		    		cp.setUIPropertyValue(propertyKey,String.valueOf(pos));
		    	}
	        });
			
			counter++;
		}
	}
	
	/**
	 * Adds a Swing action listener to a ButtonGroup, causing the UIProperty {@code propertyKey} from {@code cp} to be updated
	 * with the entry of {@code values} whose index equals the ButtonGroup currently selected button index.
	 * 
	 * @param cp ConfigurablePanel that owns the property.
	 * @param propertyKey UIProperty to modify when the ButtonGroup selected button changes.
	 * @param group ButtonGroup containing the different buttons.
	 * @param values String array containing the values to set the UIProperty to.
	 */
	public static void addActionListenerOnSelectedIndex(final ConfigurablePanel cp, final String propertyKey, final ButtonGroup group, String[] values) {
		if(cp == null) {
			throw new NullPointerException("The ConfigurablePanel cannot be null.");
		}
		if(propertyKey == null) {
			throw new NullPointerException("The UIProperty's label cannot be null.");
		}
		if(group == null) {
			throw new NullPointerException("The ButtonGroup cannot be null.");
		}
		if(values == null) {
			throw new NullPointerException("The values array cannot be null.");
		}
		for(String s: values) {
			if(s==null) {
				throw new NullPointerException("Null values are not allowed.");
			}
		}
		
		Enumeration<AbstractButton> enm = group.getElements();
		int counter = 0;
		while(enm.hasMoreElements()) {
			final int pos = counter;
			
			AbstractButton btn = enm.nextElement();		
			btn.addActionListener(new ActionListener(){
		    	public void actionPerformed(ActionEvent e){
		    		cp.setUIPropertyValue(propertyKey,values[pos]);
		    	}
	        });
			
			counter++;
		}
	}
	
	/**
	 * Adds a Swing action listener to a JCombobox, causing the UIProperty {@code propertyKey} from {@code cp} to be updated
	 * with the entry of {@code values} whose index equals the JCombobox currently selected index.
	 * 
	 * @param cp ConfigurablePanel that owns the property.
	 * @param propertyKey UIProperty to modify when the JComboBox changes.
	 * @param cbx JComboBox.
	 * @param values String array containing the values to set the UIProperty to.
	 */
	public static void addActionListenerOnSelectedIndex(final ConfigurablePanel cp, final String propertyKey, final JComboBox<?> cbx, String[] values) {
		if(cp == null) {
			throw new NullPointerException("The ConfigurablePanel cannot be null.");
		}
		if(propertyKey == null) {
			throw new NullPointerException("The UIProperty's label cannot be null.");
		}
		if(cbx == null) {
			throw new NullPointerException("The JCombobox cannot be null.");
		}
		if(values == null) {
			throw new NullPointerException("The values array cannot be null.");
		}
		for(String s: values) {
			if(s==null) {
				throw new NullPointerException("Null values are not allowed.");
			}
		}
		
		cbx.addActionListener(new ActionListener(){
	    	public void actionPerformed(ActionEvent e){
	    		int ind = cbx.getSelectedIndex();
	    		if(ind < values.length && ind >= 0) {
	    			String val = values[ind];
	    			cp.setUIPropertyValue(propertyKey,val);
	    		}
	    	}
        });
	}

	/**
	 * Adds a Swing action listener to a JTextField value, causing the UIProperty {@code propertyKey} from {@code cp} to be updated
	 * with the JTextField (integer) value within an allowed range defined by {@code min} and {@code max}. Non-integer values are ignored.
	 * 
	 * @param cp ConfigurablePanel that owns the UIProperty {@code propertyKey}
	 * @param propertyKey Label of the UIProperty to update.
	 * @param txtf JTextField.
	 * @param min Minimum accepted value. 
	 * @param max Maximum accepted value.
	 */
	public static void addActionListenerOnIntegerValue(final ConfigurablePanel cp, final String propertyKey, final JTextField txtf, int min, int max) {
		if(cp == null) {
			throw new NullPointerException("The ConfigurablePanel cannot be null.");
		}
		if(propertyKey == null) {
			throw new NullPointerException("The UIProperty's label cannot be null.");
		}
		if(txtf == null) {
			throw new NullPointerException("The JTextField cannot be null.");
		}
		
		txtf.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {	
				String s = txtf.getText();
				if (EmuUtils.isInteger(s)) {
					int val = Integer.parseInt(s);
					if(val >= min && val <= max) {
						cp.setUIPropertyValue(propertyKey, s);
					}
				}
	         }
	    });
		
		txtf.addFocusListener(new FocusListener() {
			@Override
			public void focusGained(FocusEvent ex) {}

			@Override
			public void focusLost(FocusEvent ex) {
				String s = txtf.getText();
				if (EmuUtils.isInteger(s)) {
					int val = Integer.parseInt(s);
					if(val >= min && val <= max) {
						cp.setUIPropertyValue(propertyKey, s);
					}
				}
			}
		});
	}

	/**
	 * Adds a Swing action listener to a JTextField value, causing the UIProperty {@code propertyKey} from {@code cp} to be updated
	 * with the JTextField (double) value within an allowed range defined by {@code min} and {@code max}. Non-double values are ignored.
	 * 
	 * @param cp ConfigurablePanel that owns the UIProperty {@code propertyKey}
	 * @param propertyKey Label of the UIProperty to update.
	 * @param txtf JTextField.
	 * @param min Minimum accepted value. 
	 * @param max Maximum accepted value.
	 */
	public static void addActionListenerOnDoubleValue(final ConfigurablePanel cp, final String propertyKey, final JTextField txtf, double min, double max) {
		if(cp == null) {
			throw new NullPointerException("The ConfigurablePanel cannot be null.");
		}
		if(propertyKey == null) {
			throw new NullPointerException("The UIProperty's label cannot be null.");
		}
		if(txtf == null) {
			throw new NullPointerException("The JTextField cannot be null.");
		}
		
		txtf.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {	
				String s = txtf.getText().replaceAll(",",".");
				if (EmuUtils.isNumeric(s)) {
					double val = Double.parseDouble(s);
					if(Double.compare(val,min) >= 0 && Double.compare(val,max) <= 0) {
						cp.setUIPropertyValue(propertyKey, s);
					}
				}
	         }
	    });
		
		txtf.addFocusListener(new FocusListener() {
			@Override
			public void focusGained(FocusEvent ex) {}

			@Override
			public void focusLost(FocusEvent ex) {
				String s = txtf.getText().replaceAll(",",".");
				if (EmuUtils.isNumeric(s)) {
					double val = Double.parseDouble(s);
					if(Double.compare(val,min) >= 0 && Double.compare(val,max) <= 0) {
						cp.setUIPropertyValue(propertyKey, s);
					}
				}
			}
		});
	}

	/**
	 * Adds a Swing action listener to a JTextField value, causing the UIProperty {@code propertyKey} from {@code cp} to be updated
	 * with the JTextField (integer) value. Non-integer values are ignored.
	 * 
	 * @param cp ConfigurablePanel that owns the UIProperty {@code propertyKey}
	 * @param propertyKey Label of the UIProperty to update.
	 * @param txtf JTextField.
	 */
	public static void addActionListenerOnIntegerValue(final ConfigurablePanel cp, final String propertyKey, final JTextField txtf) {
		if(cp == null) {
			throw new NullPointerException("The ConfigurablePanel cannot be null.");
		}
		if(propertyKey == null) {
			throw new NullPointerException("The UIProperty's label cannot be null.");
		}
		if(txtf == null) {
			throw new NullPointerException("The JTextField cannot be null.");
		}
		
		txtf.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				String s = txtf.getText();
				if (EmuUtils.isInteger(s)) {
					cp.setUIPropertyValue(propertyKey, s);
				}
			}
		});

		txtf.addFocusListener(new FocusListener() {
			@Override
			public void focusGained(FocusEvent ex) {
			}

			@Override
			public void focusLost(FocusEvent ex) {
				String s = txtf.getText();
				if (EmuUtils.isInteger(s)) {
					cp.setUIPropertyValue(propertyKey, s);
				}
			}
		});
	}

	/**
	 * Adds a Swing action listener to a JTextField value, causing the UIProperty
	 * {@code propertyKey} from {@code cp} to be updated with the JTextField
	 * (double) value. Non-double values are ignored.
	 * 
	 * @param cp          ConfigurablePanel that owns the UIProperty {@code propertyKey}
	 * @param propertyKey Label of the UIProperty to update.
	 * @param txtf        JTextField.
	 */
	public static void addActionListenerOnDoubleValue(final ConfigurablePanel cp, final String propertyKey, final JTextField txtf) {
		if(cp == null) {
			throw new NullPointerException("The ConfigurablePanel cannot be null.");
		}
		if(propertyKey == null) {
			throw new NullPointerException("The UIProperty's label cannot be null.");
		}
		if(txtf == null) {
			throw new NullPointerException("The JTextField cannot be null.");
		}
		
		txtf.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				String s = txtf.getText().replaceAll(",",".");
				if (EmuUtils.isNumeric(s)) {
					cp.setUIPropertyValue(propertyKey, s);
				}
			}
		});

		txtf.addFocusListener(new FocusListener() {
			@Override
			public void focusGained(FocusEvent ex) {
			}

			@Override
			public void focusLost(FocusEvent ex) {
				String s = txtf.getText().replaceAll(",",".");
				if (EmuUtils.isNumeric(s)) {
					cp.setUIPropertyValue(propertyKey, s);
				}
			}
		});
	}

	/**
	 * Adds a Swing action listener to a JTextField with an input of type Double, triggering an Action (lambda expression) when the enter key is pressed.
	 * Non-double expressions will be ignored.
	 * 
	 * @param action Action taking a Double parameter.
	 * @param txtf JTextField triggering the Action.
	 */
	public static void addActionListenerToDoubleAction(final Action<Double> action, final JTextField txtf) {

		if(action == null) {
			throw new NullPointerException("The Action cannot be null.");
		}
		if(txtf == null) {
			throw new NullPointerException("The JTextField cannot be null.");
		}
		
		txtf.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				String s = txtf.getText().replaceAll(",",".");
				if (EmuUtils.isNumeric(s)) {
					action.performAction(Double.valueOf(s));
				}
			}
		});

		txtf.addFocusListener(new FocusListener() {
			@Override
			public void focusGained(FocusEvent ex) {
			}

			@Override
			public void focusLost(FocusEvent ex) {
				String s = txtf.getText().replaceAll(",",".");
				if (EmuUtils.isNumeric(s)) {
					action.performAction(Double.valueOf(s));
				}
			}
		});
	}
	
	/**
	 * Adds a Swing action listener to a JTextField with an input of type Double between {@code min} and {@code max}, 
	 * triggering an Action (lambda expression) when the enter key is pressed. Non-double expressions will be ignored.
	 * 
	 * @param action Action taking a Double parameter.
	 * @param txtf JTextField triggering the Action.
	 * @param min Minimum value of the Double input.
	 * @param max Maximum value of the Double input.
	 */
	public static void addActionListenerToDoubleAction(final Action<Double> action, final JTextField txtf, double min, double max) {
		if(action == null) {
			throw new NullPointerException("The Action cannot be null.");
		}
		if(txtf == null) {
			throw new NullPointerException("The JTextField cannot be null.");
		}
		
		txtf.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				String s = txtf.getText().replaceAll(",",".");
				if (EmuUtils.isNumeric(s)) {
					double val = Double.valueOf(s);
					if(Double.compare(val, min) >= 0 && Double.compare(val, max) <= 0) {
						action.performAction(Double.valueOf(s));
					}
				}
			}
		});

		txtf.addFocusListener(new FocusListener() {
			@Override
			public void focusGained(FocusEvent ex) {
			}

			@Override
			public void focusLost(FocusEvent ex) {
				String s = txtf.getText().replaceAll(",",".");
				if (EmuUtils.isNumeric(s)) {
					double val = Double.valueOf(s);
					if(Double.compare(val, min) >= 0 && Double.compare(val, max) <= 0) {
						action.performAction(Double.valueOf(s));
					}
				}
			}
		});
	}
	/**
	 * Adds a Swing action listener to a JTextField, triggering an Action (lambda expression) when the enter 
	 * key is pressed.
	 * 
	 * @param action Action taking a String parameter.
	 * @param txtf JTextField triggering the Action.
	 */
	public static void addActionListenerToStringAction(final Action<String> action, final JTextField txtf) {
		if(action == null) {
			throw new NullPointerException("The Action cannot be null.");
		}
		if(txtf == null) {
			throw new NullPointerException("The JTextField cannot be null.");
		}
		
		txtf.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				action.performAction(txtf.getText());
			}
		});

		txtf.addFocusListener(new FocusListener() {
			@Override
			public void focusGained(FocusEvent ex) {
			}

			@Override
			public void focusLost(FocusEvent ex) {
				action.performAction(txtf.getText());
			}
		});
	}
	
	/**
	 * Adds a Swing action listener to a JTextField with an input of type Integer, triggering an Action (lambda expression) 
	 * when the enter key is pressed. Non-integer expressions will be ignored.
	 * 
	 * @param action Action taking an Integer parameter.
	 * @param txtf JTextField triggering the Action.
	 */
	public static void addActionListenerToIntegerAction(final Action<Integer> action, final JTextField txtf) {
		if(action == null) {
			throw new NullPointerException("The Action cannot be null.");
		}
		if(txtf == null) {
			throw new NullPointerException("The JTextField cannot be null.");
		}
		
		txtf.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				String s = txtf.getText();
				if (EmuUtils.isInteger(s)) {
					action.performAction(Integer.valueOf(s));
				}
			}
		});

		txtf.addFocusListener(new FocusListener() {
			@Override
			public void focusGained(FocusEvent ex) {
			}

			@Override
			public void focusLost(FocusEvent ex) {
				String s = txtf.getText();
				if (EmuUtils.isInteger(s)) {
					action.performAction(Integer.valueOf(s));
				}
			}
		});
	}

	/**
	 * Adds a Swing action listener to a JSlider, causing the UIProperty {@code propertyKey} from {@code cp} to be updated
	 * with the JSlider (integer) value. 
	 * 
	 * @param cp ConfigurablePanel that owns the UIProperty {@code propertyKey}
	 * @param propertyKey Label of the UIProperty to update.
	 * @param sld JSlider.
	 */
	public static void addActionListenerOnIntegerValue(final ConfigurablePanel cp, final String propertyKey, final JSlider sld) {
		if(cp == null) {
			throw new NullPointerException("The ConfigurablePanel cannot be null.");
		}
		if(propertyKey == null) {
			throw new NullPointerException("The UIProperty's label cannot be null.");
		}
		if(sld == null) {
			throw new NullPointerException("The JSlider cannot be null.");
		}
		
		sld.addMouseListener(new MouseAdapter() {
			public void mouseReleased(MouseEvent e) {
				int val = sld.getValue();
				cp.setUIPropertyValue(propertyKey, String.valueOf(val));
			}
		});
	}
	
	/**
	 * Adds a Swing action listener to a JTextField, causing the UIProperty {@code propertyKey} from {@code cp} and the JSlider {@code sld}
	 * to be updated with the JTextField (integer) value. Non-integer values will be ignored. 
	 * 
	 * @param cp ConfigurablePanel that owns the UIProperty {@code propertyKey}
	 * @param propertyKey Label of the UIProperty to update.
	 * @param txtf JTextField triggering the update when the enter key is pressed.
	 * @param sld JSlider updated alongside the UIProperty.
	 */
	public static void addActionListenerOnIntegerValue(final ConfigurablePanel cp, final String propertyKey, final JTextField txtf, final JSlider sld) {
		if(cp == null) {
			throw new NullPointerException("The ConfigurablePanel cannot be null.");
		}
		if(propertyKey == null) {
			throw new NullPointerException("The UIProperty's label cannot be null.");
		}
		if(txtf == null) {
			throw new NullPointerException("The JTextField cannot be null.");
		}
		if(sld == null) {
			throw new NullPointerException("The JSlider cannot be null.");
		}
		
		txtf.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {	
				String s = txtf.getText();
				if (EmuUtils.isInteger(s)) {
					int val = Integer.parseInt(s);
					if(val >= sld.getMinimum() && val <= sld.getMaximum()) {
						sld.setValue(val);
						cp.setUIPropertyValue(propertyKey, s);
					}
				}
	         }
	    });
		
		txtf.addFocusListener(new FocusListener() {
			@Override
			public void focusGained(FocusEvent ex) {}

			@Override
			public void focusLost(FocusEvent ex) {
				String s = txtf.getText();
				if (EmuUtils.isInteger(s)) {
					int val = Integer.parseInt(s);
					if(val >= sld.getMinimum() && val <= sld.getMaximum()) {
						sld.setValue(val);
						cp.setUIPropertyValue(propertyKey, s);
					}
				}
			}
		});
	}
	
	/**
	 * Adds a Swing action listener to a JSlider, causing the UIProperty {@code propertyKey} from {@code cp} and the JTextField {@code txtf}
	 * to be updated with the JSlider (integer) value. 
	 * 
	 * @param cp ConfigurablePanel that owns the UIProperty {@code propertyKey}
	 * @param propertyKey Label of the UIProperty to update.
	 * @param sld JSlider triggering the update.
	 * @param txtf JTextField updated alongside the UIProperty.
	 */
	public static void addActionListenerOnIntegerValue(final ConfigurablePanel cp, final String propertyKey, final JSlider sld, final JTextField txtf) {
		if(cp == null) {
			throw new NullPointerException("The ConfigurablePanel cannot be null.");
		}
		if(propertyKey == null) {
			throw new NullPointerException("The UIProperty's label cannot be null.");
		}
		if(txtf == null) {
			throw new NullPointerException("The JTextField cannot be null.");
		}
		if(sld == null) {
			throw new NullPointerException("The JSlider cannot be null.");
		}
		
		sld.addMouseListener(new MouseAdapter() {
			public void mouseReleased(MouseEvent e) {
				String strval = String.valueOf(sld.getValue());
				txtf.setText(strval);
				cp.setUIPropertyValue(propertyKey, strval);
			}
		});
	}

	/**
	 * Adds a Swing action listener to a JSlider, causing the UIProperty {@code propertyKey} from {@code cp} and the JLabel {@code lbl}
	 * to be updated with the JSlider (integer) value. 
	 * 
	 * @param cp ConfigurablePanel that owns the UIProperty {@code propertyKey}
	 * @param propertyKey Label of the UIProperty to update.
	 * @param sld JSlider triggering the update.
	 * @param lbl JLabel updated alongside the UIProperty.
	 */
	public static void addActionListenerOnIntegerValue(final ConfigurablePanel cp, final String propertyKey, final JSlider sld, final JLabel lbl) {
		if(cp == null) {
			throw new NullPointerException("The ConfigurablePanel cannot be null.");
		}
		if(propertyKey == null) {
			throw new NullPointerException("The UIProperty's label cannot be null.");
		}
		if(lbl == null) {
			throw new NullPointerException("The JLabel cannot be null.");
		}
		if(sld == null) {
			throw new NullPointerException("The JSlider cannot be null.");
		}
		
		sld.addMouseListener(new MouseAdapter() {
			public void mouseReleased(MouseEvent e) {
				String val = String.valueOf(sld.getValue());
				lbl.setText(val);
				cp.setUIPropertyValue(propertyKey, val);
			}
		});
	}
	/**
	 * Adds a Swing action listener to a JSlider, causing the UIProperty {@code propertyKey} from {@code cp} and the JLabel {@code lbl}
	 * to be updated with the JSlider (integer) value. 
	 * 
	 * @param cp ConfigurablePanel that owns the UIProperty {@code propertyKey}
	 * @param propertyKey Label of the UIProperty to update.
	 * @param sld JSlider triggering the update.
	 * @param lbl JLabel updated alongside the UIProperty.
	 * @param prefix Prefix added in the JLabel text before the JSlider value.
	 * @param suffix Suffix added in the JLabel text after the JSlider value.
	 */
	public static void addActionListenerOnIntegerValue(final ConfigurablePanel cp, final String propertyKey, final JSlider sld, final JLabel lbl, final String prefix, final String suffix) {
		if(cp == null) {
			throw new NullPointerException("The ConfigurablePanel cannot be null.");
		}
		if(propertyKey == null) {
			throw new NullPointerException("The UIProperty's label cannot be null.");
		}
		if(lbl == null) {
			throw new NullPointerException("The JLabel cannot be null.");
		}
		if(sld == null) {
			throw new NullPointerException("The JSlider cannot be null.");
		}
		if(prefix == null) {
			throw new NullPointerException("The prefix cannot be null.");
		}
		if(suffix == null) {
			throw new NullPointerException("The suffix cannot be null.");
		}
		
		sld.addMouseListener(new MouseAdapter() {
			public void mouseReleased(MouseEvent e) {
				String val = String.valueOf(sld.getValue());
				lbl.setText(prefix+val+suffix);
				cp.setUIPropertyValue(propertyKey, val);
			}
		});
	}

	/**
	 * Adds a Swing action listener to a JToggleButton causing the TwoStateUIProperty {@code propertyKey} from {@code cp} 
	 * to be switched to its on or off state depending on {@code tglb} state. 
	 * 
	 * @param cp ConfigurablePanel that owns the TwoStateUIProperty {@code propertyKey}.
	 * @param propertyKey Label of the TwoStateUIProperty.
	 * @param tglb JToggleButton triggering the TwoStateUIProperty change.
	 * @throws IncorrectUIPropertyTypeException Exception thrown if {@code propertyKey} does not correspond to a TwoStateUIProperty.
	 */
	public static void addActionListenerToTwoState(final ConfigurablePanel cp, final String propertyKey, final JToggleButton tglb) throws IncorrectUIPropertyTypeException {
		if(cp == null) {
			throw new NullPointerException("The ConfigurablePanel cannot be null.");
		}
		if(propertyKey == null) {
			throw new NullPointerException("The UIProperty's label cannot be null.");
		}
		if(tglb == null) {
			throw new NullPointerException("The JToggleButton cannot be null.");
		}
		if(!cp.getUIPropertyType(propertyKey).equals(UIPropertyType.TWOSTATE)) {
			throw new IncorrectUIPropertyTypeException(UIPropertyType.TWOSTATE.getTypeValue(), cp.getUIPropertyType(propertyKey).getTypeValue());
		}
		
		tglb.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				AbstractButton abstractButton = (AbstractButton) actionEvent.getSource();
				boolean selected = abstractButton.getModel().isSelected();
				if (selected) {
					cp.setUIPropertyValue(propertyKey, TwoStateUIProperty.getOnStateLabel());
				} else {
					cp.setUIPropertyValue(propertyKey, TwoStateUIProperty.getOffStateLabel());
				}
			}
		});
	}

	/**
	 *  Adds a Swing action listener to a JToggleButton causing the (boolean) Action to be triggered upon {@code tglb} change.
	 * 
	 * @param action Action triggered by a change in {@code tglb} state.
	 * @param tglb JToggleButton triggering the Action.
	 */
	public static void addActionListenerToBooleanAction(final Action<Boolean> action, final JToggleButton tglb) {
		if(action == null) {
			throw new NullPointerException("The Action cannot be null.");
		}
		if(tglb == null) {
			throw new NullPointerException("The JToggleButton cannot be null.");
		}
		
		tglb.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				AbstractButton abstractButton = (AbstractButton) actionEvent.getSource();
				boolean selected = abstractButton.getModel().isSelected();
				action.performAction(selected);
			}
		});
	}
	
	/**
	 * Adds a Swing mouse listener to a JSlider causing the (Integer) Action to be triggered upon value change.
	 * 
	 * @param action Action triggered by a change of {@code sldr}.
	 * @param sldr Jslider triggering the Action.
	 */
	public static void addActionListenerToIntegerAction(final Action<Integer> action, final JSlider sldr) {
		if(action == null) {
			throw new NullPointerException("The Action cannot be null.");
		}
		if(sldr == null) {
			throw new NullPointerException("The JSlider cannot be null.");
		}
		
		sldr.addMouseListener(new MouseAdapter() {
			public void mouseReleased(MouseEvent e) {
				action.performAction(sldr.getValue());
			}
		});
	}
	
	/**
	 * Adds a Swing action listener to an AbstractButton causing the UnparametrizedAction to be triggered upon clicking on the button.
	 * 
	 * @param action Action triggered by {@code btn} being pressed.
	 * @param btn Button triggering the action.
	 */
	public static void addActionListenerToUnparametrizedAction(final UnparametrizedAction action, final AbstractButton btn) {
		if(action == null) {
			throw new NullPointerException("The Action cannot be null.");
		}
		if(btn == null) {
			throw new NullPointerException("The AbstractButton cannot be null.");
		}
		
		btn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				action.performAction();
			}
		});
	}

	/**
	 * Adds a Swing action listener to an AbstractButton causing the SingleStateUIProperty to be set to its single-state upon pressing the button.
	 * 
	 * @param cp ConfigurablePanel that owns the SingleStateUIProperty {@code propertyKey}.
	 * @param propertyKey Label of the SingleStateUIProperty.
	 * @param btn AbstractButton triggering the call.
	 * @throws IncorrectUIPropertyTypeException Thrown if {@code propertyKey} does not correspond to a SingleStateUIProperty.
	 */
	public static void addActionListenerToSingleState(final ConfigurablePanel cp, final String propertyKey, final AbstractButton btn) throws IncorrectUIPropertyTypeException {
		if(cp == null) {
			throw new NullPointerException("The ConfigurablePanel cannot be null.");
		}
		if(propertyKey == null) {
			throw new NullPointerException("The UIProperty's label cannot be null.");
		}
		if(btn == null) {
			throw new NullPointerException("The AbstractButton cannot be null.");
		}
		if(!cp.getUIPropertyType(propertyKey).equals(UIPropertyType.SINGLESTATE)) {
			throw new IncorrectUIPropertyTypeException(UIPropertyType.SINGLESTATE.getTypeValue(), cp.getUIPropertyType(propertyKey).getTypeValue());
		}
		
		btn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				cp.setUIPropertyValue(propertyKey, SingleStateUIProperty.getStateLabel());
			}
		});
	}

	/**
	 * Adds a Swing change listener to a JSpinner, causing the UIProperty {@code propertyKey} to be updated upon {@code spnr} state change.
	 * 
	 * @param cp ConfigurablePanel that owns the UIProperty {@code propertyKey}.
	 * @param propertyKey Label of the UIProperty.
	 * @param spnr JSpinner with SpinnerNumberModel value triggering the UIProperty change.
	 */
	public static void addChangeListenerOnNumericalValue(final ConfigurablePanel cp, final String propertyKey, final JSpinner spnr) {
		if(cp == null) {
			throw new NullPointerException("The ConfigurablePanel cannot be null.");
		}
		if(propertyKey == null) {
			throw new NullPointerException("The UIProperty's label cannot be null.");
		}
		if(spnr == null) {
			throw new NullPointerException("The JSpinner cannot be null.");
		}
		
		if(!(spnr.getModel() instanceof SpinnerNumberModel)) {
			throw new IllegalArgumentException("The JSpinner should have a SpinnerNumberModel.");
		}
		
		spnr.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				Object val = spnr.getValue();
				if(val instanceof Integer) {
					cp.setUIPropertyValue(propertyKey, String.valueOf((int) spnr.getValue()));
				} else {
					cp.setUIPropertyValue(propertyKey, String.valueOf((double) spnr.getValue()));
				}
			}
		});
	}

}
