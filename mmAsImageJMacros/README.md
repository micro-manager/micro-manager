# MM2_MacroExtensions

This ImageJ plugin extends the ImageJ macro language with commands to access Micro-Manager2 functionalities. Currently you can set exposure and snap images, change presets in configuration groups, move the stage, and alter installed device properties. 
I tested it against MM2-gamma branch.

```javascript
// start the macro extensions
run("MM2 MacroExtensions");

// moving the stage or getting stage position
Ext.moveRelativeXYZ(10,15,0);
Ext.getStageXYZ(x,y,z);
print ("Position: ",x,y,z);
Ext.moveAbsoluteXYZ(0,0,0);
Ext.getStageXYZ(x,y,z);
print ("Position: ",x,y,z);

// inspecting and using configuration settings
Ext.getConfigGroups(groups);
print ("Existing Config groups: ", groups);

Ext.getGroupPresets("Channel",presets);
print ("Available Presets for Channel group: ", presets);

Ext.setConfig("Channel","FITC");

// inspecting and using devices
Ext.getDevices(devs);
print ("Installed devices: ",devs);
Ext.getDeviceProperties("Camera",props);
print ("Available Properties for Camera device: ",props);
Ext.setDeviceProperty("Camera","Mode","Noise");
Ext.getDeviceProperty("Camera","BitDepth",value);
print (value);

// snapping images
Ext.setExposure(100);
// snaps an image from the camera
Ext.snap(); 
// snaps and runs the image through the on-the-fly processing pipeline
Ext.snapAndProcess(); 

```
