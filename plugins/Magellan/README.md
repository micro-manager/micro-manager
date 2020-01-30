# Micro-Magellan
Control software for high-throughput optical microscopy

[User Guide](https://micro-manager.org/wiki/MicroMagellan)

[Journal article](http://krummellab.com/images/Publications/pinkard-pub-2016-02.pdf)

## Installation and setup
The current Micro-Magellan master branch is developed against the Micro-Manager 2.0 APIs. 

1. Download and install the current version of Micro-Manager 2.0 from [here](https://micro-manager.org/wiki/Version_2.0). At the time of this writing (2018/9/8) the current version is beta3.

2. Download the latest version of Magellan.jar from [here](https://github.com/henrypinkard/Micro-Magellan/releases). Copy it into the "mmplugins" folder of the root directory of the micromanager installation

3. Download the [DT1.2-.jar](https://github.com/henrypinkard/Micro-Magellan/releases) and copy it into the "plugins" folder of the micromanager installation

4. Launch Micro-manager and configure your hardware setup if you haven't already done so using Micro-Manager's hardware configuration wizard. More information can be found [here](https://micro-manager.org/wiki/Micro-Manager_Configuration_Guide).

5. Select "Plugins-MicroMagellan" to launch Magellan. A setup window should launch to guide you through the remaining two steps for using Magellan (Configuring the focus direction of your Z drive and calibrating your XY stage relative to the camera for tiled imaging).

