This is not an official Google product (experimental or otherwise), it is just code that happens to be owned by Google.

CNC Microscope is an automated microscope whose stage is built from a
Shapeoko 1 CNC router platform
(http://www.shapeoko.com/wiki/index.php/Assembly_overview_(SO1))
driven by a RepRap RAMPS (http://www.reprap.org/wiki/Ramps) and whose
illuminator is an Adafruit NeoPixel Shield
(http://www.adafruit.com/products/1430).  The microscope is assembled
using the Shapeoko, the illuminator, and an objective/optical
tube/camera assembly, all mounted to an extruded aluminum frame using
3D-printed parts.  Device adapters for the Micro-Manager software
(https://www.micro-manager.org/) make the Shapeoko and illuminator act
as XY/Z microscope stages and shutter, respectively.

Subdirectories:
cad: FreeCAD files for the 3D-printed parts
micromanager: Device adapters for the RAMPS and Adafruit NeoPixel Shield
arduino: Firmware for Arduino UNO to drive the Adafruit NeoPixel Shield

License:
Apache 2(BSD?)

Bill of Materials:

Shapeoko 1.  No longer for sale, but an X-Carve
https://www.inventables.com/technologies/x-carve should be a
reasonable replacement.

Arduino UNO.  Easily obtained from Amazon, or elsewhere.

Adafruit NeoPixel Shield.  http://www.adafruit.com/products/1430

ThorCam camera.  http://www.thorlabs.com/thorproduct.cfm?partnumber=DCC1545M

Edmunds optical tube.  http://www.edmundoptics.com/microscopy/relay-lenses-couplers/single-tube-din-microscopes-for-ocular-c-mount-camera-use/1548/

Misumi extrusion, brackets.  We ordered a pre-cut Misumi AEX BA with dimensions 410mm per side. http://us.misumi-ec.com/vona2/detail/110302285350/?Inch=0

Assembly:

Refer to the CAD diagram of the entire system for an overview.  In
short, assembly follows these steps:

1) Assemble the Shapeoko according to the Shapeoko assembly
instructions.  Omit the step of mounting the spindle holder in the
Z-axis.

2) Assemble the aluminum extrusion frame and mount it to the Shapeoko.

3) 3D print the parts, and mount them to the frame.

4) Assemble the optical tube, camera, and objective and install them
within the 3D-printed microscope to Z axis bracket.

5) Assemble the Arduino UNO, Neopixel Shield, and diffuser and install
them to the aluminum extrusion top bars.

6) Flash the ArduinoNeoPixel firmware to the UNO.

7) Build and install the Micro-Manager device adapters.

8) Run Micro-Manager, configure the device adapters.




