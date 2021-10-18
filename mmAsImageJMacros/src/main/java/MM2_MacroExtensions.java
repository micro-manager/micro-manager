import org.micromanager.data.Image;
import org.micromanager.Studio;



import ij.IJ;
import ij.ImagePlus;
import ij.macro.ExtensionDescriptor;
import ij.macro.Functions;
import ij.macro.MacroExtension;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import org.micromanager.internal.MMStudio;

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

	@Override
	public String handleExtension(String name, Object[] args) {
		switch (name) {
			case "snap": {
				Studio studio = MMStudio.getInstance();
				Image image = studio.live().snap(false).get(0);
				ImageProcessor ip = studio.data().ij().createProcessor(image);
				ImagePlus imp = new ImagePlus("Snap", ip);
				imp.show();
				break;
			}
			case "snapAndProcess": {
				Studio studio = MMStudio.getInstance();
				Datastore store = studio.data().createRAMDatastore();
				Pipeline pipeLine = studio.data().copyApplicationPipeline(store, true);
				List<Image> imgList;
				try {
				    imgList = studio.acquisitions().snap();                
				    Image img = imgList.get(0);
				    pipeLine.insertImage(img);
				    Coords coords = studio.data().createCoords("t=0,p=0,c=0,z=0");
				    Image processedImg = store.getImage(coords);
				    Double px = processedImg.getMetadata().getPixelSizeUm();
				    ImageProcessor ip = studio.data().ij().createProcessor(processedImg);
				    ImagePlus imp = new ImagePlus("Image", ip);
				    Calibration cal = imp.getCalibration();
				    cal.pixelWidth = px;
				    cal.pixelHeight = px;
				    cal.pixelDepth = 1;
				    cal.setUnit("micron");
				    imp.show();
				} catch (Exception e) {
				    e.printStackTrace();
				}
				break;
		    	}
			case "setExposure": {
				MMStudio studio = MMStudio.getInstance();
				double exposure = ((Double) args[0]).intValue();
				studio.app().setExposure(exposure);
				studio.app().refreshGUI();
				break;
			}
			case "setConfig": {
				MMStudio studio = MMStudio.getInstance();
				String group = ((String) args[0]);
				String preset = ((String) args[1]);
				try {
					studio.core().setConfig(group, preset);
					studio.app().refreshGUI();
				} catch (Exception e) {
					e.printStackTrace();
				}
				break;
			}
			case "moveRelativeXYZ": {
				MMStudio studio = MMStudio.getInstance();
				double dx = ((Double) args[0]);
				double dy = ((Double) args[1]);
				double dz = ((Double) args[2]);
				try {
					studio.core().setRelativePosition(dz);
					studio.core().setRelativeXYPosition(dx, dy);
					studio.app().refreshGUI();
				} catch (Exception e) {
					e.printStackTrace();
				}
				break;
			}
			case "moveAbsoluteXYZ": {
				MMStudio studio = MMStudio.getInstance();
				double dx = ((Double) args[0]);
				double dy = ((Double) args[1]);
				double dz = ((Double) args[2]);
				try {
					studio.core().setPosition(dz);
					studio.core().setXYPosition(dx, dy);
					studio.app().refreshGUI();
				} catch (Exception e) {
					e.printStackTrace();
				}
				break;
			}
			case "getStageXYZ": {
				MMStudio studio = MMStudio.getInstance();
				try {
					((Double[]) args[0])[0] = studio.core().getXPosition();
					((Double[]) args[1])[0] = studio.core().getYPosition();
					((Double[]) args[2])[0] = studio.core().getPosition();
				} catch (Exception e) {
					e.printStackTrace();
				}
				break;
			}
			case "getConfigGroups": {
				MMStudio studio = MMStudio.getInstance();
				try {
					((String[]) args[0])[0] = String.join(";", studio.core().getAvailableConfigGroups().toArray());
				} catch (Exception e) {
					e.printStackTrace();
				}
				break;
			}
			case "getGroupPresets": {
				MMStudio studio = MMStudio.getInstance();
				String group = ((String) args[0]);
				try {
					((String[]) args[1])[0] = String.join(";", studio.core().getAvailableConfigs(group));
				} catch (Exception e) {
					e.printStackTrace();
				}
				break;
			}
			case "getDevices": {
				MMStudio studio = MMStudio.getInstance();
				try {
					((String[]) args[0])[0] = String.join(";", studio.core().getLoadedDevices().toArray());
				} catch (Exception e) {
					e.printStackTrace();
				}
				break;
			}
			case "getDeviceProperties": {
				MMStudio studio = MMStudio.getInstance();
				String dev = ((String) args[0]);
				try {
					((String[]) args[1])[0] = String.join(";", studio.core().getDevicePropertyNames(dev));
				} catch (Exception e) {
					e.printStackTrace();
				}
				break;
			}
			case "setDeviceProperty": {
				MMStudio studio = MMStudio.getInstance();
				String dev = ((String) args[0]);
				String prop = ((String) args[1]);
				String value = ((String) args[2]);
				try {
					studio.core().setProperty(dev, prop, value);
					studio.app().refreshGUI();
				} catch (Exception e) {
					e.printStackTrace();
				}
				break;
			}
		}
		return null;
	}
}
