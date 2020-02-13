import org.micromanager.data.Image;
import org.micromanager.internal.MMStudio;

import ij.IJ;
import ij.ImagePlus;
import ij.macro.ExtensionDescriptor;
import ij.macro.Functions;
import ij.macro.MacroExtension;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;

public class MM2_MacroExtensions implements PlugIn, MacroExtension {
   @Override
	public void run(String arg) {
		if (!IJ.macroRunning()) {
			IJ.error("Cannot install extensions from outside a macro!");
			return;
		}
		Functions.registerExtensions(this);
	}

	private final ExtensionDescriptor[] extensions = { ExtensionDescriptor.newDescriptor("snap", this),
			ExtensionDescriptor.newDescriptor("setExposure", this, ARG_NUMBER),
			ExtensionDescriptor.newDescriptor("moveRelativeXYZ", this, ARG_NUMBER, ARG_NUMBER, ARG_NUMBER),
			ExtensionDescriptor.newDescriptor("moveAbsoluteXYZ", this, ARG_NUMBER, ARG_NUMBER, ARG_NUMBER),
			ExtensionDescriptor.newDescriptor("getStageXYZ", this, ARG_NUMBER + ARG_OUTPUT, ARG_NUMBER + ARG_OUTPUT,
					ARG_NUMBER + ARG_OUTPUT),
			ExtensionDescriptor.newDescriptor("getConfigGroups", this, ARG_STRING + ARG_OUTPUT),
			ExtensionDescriptor.newDescriptor("getGroupPresets", this, ARG_STRING, ARG_STRING + ARG_OUTPUT),
			ExtensionDescriptor.newDescriptor("setConfig", this, ARG_STRING, ARG_STRING),
			ExtensionDescriptor.newDescriptor("getDevices", this, ARG_STRING + ARG_OUTPUT),
			ExtensionDescriptor.newDescriptor("getDeviceProperties", this, ARG_STRING, ARG_STRING + ARG_OUTPUT),
			ExtensionDescriptor.newDescriptor("setDeviceProperty", this, ARG_STRING, ARG_STRING, ARG_STRING), };

	@Override
	public ExtensionDescriptor[] getExtensionFunctions() {
		return extensions;
	}

	@SuppressWarnings("deprecation")
	@Override
	public String handleExtension(String name, Object[] args) {
		if (name.equals("snap")) {
			MMStudio studio = MMStudio.getInstance();
			Image image = studio.live().snap(false).get(0);
			ImageProcessor ip = studio.data().ij().createProcessor(image);
			ImagePlus imp = new ImagePlus("Snap", ip);
			imp.show();
		} else if (name.equals("setExposure")) {
			MMStudio studio = MMStudio.getInstance();
			double exposure = ((Double) args[0]).intValue();
			studio.setExposure(exposure);
			studio.refreshGUI();
		} else if (name.equals("setConfig")) {
			MMStudio studio = MMStudio.getInstance();
			String group = ((String) args[0]);
			String preset = ((String) args[1]);
			try {
				studio.core().setConfig(group, preset);
				studio.refreshGUI();
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if (name.equals("moveRelativeXYZ")) {
			MMStudio studio = MMStudio.getInstance();
			double dx = ((Double) args[0]);
			double dy = ((Double) args[1]);
			double dz = ((Double) args[2]);
			try {
				studio.core().setRelativePosition(dz);
				studio.core().setRelativeXYPosition(dx, dy);
				studio.refreshGUI();
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if (name.equals("moveAbsoluteXYZ")) {
			MMStudio studio = MMStudio.getInstance();
			double dx = ((Double) args[0]);
			double dy = ((Double) args[1]);
			double dz = ((Double) args[2]);
			try {
				studio.core().setPosition(dz);
				studio.core().setXYPosition(dx, dy);
				studio.refreshGUI();
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if (name.equals("getStageXYZ")) {
			MMStudio studio = MMStudio.getInstance();
			try {
				((Double[]) args[0])[0] = studio.core().getXPosition();
				((Double[]) args[1])[0] = studio.core().getYPosition();
				((Double[]) args[2])[0] = studio.core().getPosition();
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if (name.equals("getConfigGroups")) {
			MMStudio studio = MMStudio.getInstance();
			try {
				((String[]) args[0])[0] = String.join(";", studio.core().getAvailableConfigGroups().toArray());
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if (name.equals("getGroupPresets")) {
			MMStudio studio = MMStudio.getInstance();
			String group = ((String) args[0]);
			try {
				((String[]) args[1])[0] = String.join(";", studio.core().getAvailableConfigs(group));
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if (name.equals("getDevices")) {
			MMStudio studio = MMStudio.getInstance();
			try {
				((String[]) args[0])[0] = String.join(";", studio.core().getLoadedDevices().toArray());
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if (name.equals("getDeviceProperties")) {
			MMStudio studio = MMStudio.getInstance();
			String dev = ((String) args[0]);
			try {
				((String[]) args[1])[0] = String.join(";", studio.core().getDevicePropertyNames(dev));
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if (name.equals("setDeviceProperty")) {
			MMStudio studio = MMStudio.getInstance();
			String dev = ((String) args[0]);
			String prop = ((String) args[1]);
			String value = ((String) args[2]);
			try {
				studio.core().setProperty(dev, prop, value);
				studio.refreshGUI();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}
}
