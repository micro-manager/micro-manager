ChuoSeiki MD5000

Summary:                   Micro-Manager device adapters for MD5000-series controllers
Author:	                   Duong Quang Anh (Chuo Precision Industrial Co. LTD. Japan)
License:                   LGPL
Platforms:                 All (uses virtual serial via USB interface)
Devices:                   2-Axis: MD5230D
                           1-axis: MD5123D
Available since version:   1.4.23 20181103 (Nightly Builds)
****************************************************************************************


The MD5000 series controllers use a virtual serial port via the USB interface. You have to install the MD5000 USB driver before using. You can also install the MD51_52OPTOOL to set some parameters that can not be changed in Micro-Manager (such as customized acceleration time, motor current, sensor settings, home search settings, etc.).

Please click here [1] to download the MD5000 USB driver and here [2] for the MD51_52OPTOOL software.

To download the MD5000 user manual, please follow this link [3]

The ChuoSeiki MD5000 device adapter can control one or more MD-controllers: you can register multi-device adapters in Hardware Configuration Wizard with different serial ports (USB-virtual), and each device adapter controls 1 controller at one serial port.

The Serial port settings are as following:

Serial port settings
	Baudrate		115200	bps
	DataBits		8	bits
	StopBits		1	bit
	ParityBits		None	bit
	AnswerTimeout		500	ms
	DelayBetweenCharsMs	0.00	ms
	Handshaking		OFF	

Device Adapter Initilization
The "ChuoSeiki XY Controller||XY stages" asks to register the Serial port and Serial port setting.
The "ChuoSeiki Z Controller||Z stages" asks to register the Serial port, Serial port setting, and Axis name.

Default Stage Settings		
	Speed			1000	pps
	StepSize		1	um
	Acceleration pattern	"1"	(500 ms)
Please revised stage setting using Group and Preset in Micro-Manager. The setting can be saved in a .cfg file.

Note* Axis Name Registration

The "ChuoSeiki XY Controller||XY stages" support only the 2-axis controller (MD5230D). In this device adapter, the 2 stages names are defined as "X" and "Y", respectively. It uses some functions that work only on MD5230D controller.

The "ChuoSeiki Z Controller||Z stage" support both the 1-axis controller (MD5130D) and 2-axis controller (MD5230D). The axis name can be defined in the device adapter. For MD5130D, the axis name must be defined as "X". For MD5230D, the axis name can be defined as "X" or "Y", depends on which channel is connected to the Z-stage.

Note* Acceleration Pattern

In Device adapter, the Acceleration pattern (include acceleration and deceleration times) can be changed from "1", "2", "3" and "4". They correspond to the 4 acceleration times that are registered in Controller speed settings 1,2,3 and 4, respectively. To use a customized acceleration time, please change and register using MD51_52OPTOOL software.

Default Acceleration pattern:

	Pattern	Acceleration time	Deceleration time
	"1"	0 (ms)			0 (ms)
	"2"	500 (ms)		0 (ms)
	"3"	500 (ms)		0 (ms)
	"4"	500 (ms)		0 (ms)

Note* Step Resolution
The Step resolution (step zise) can be initialized to the controller using MD51_52OPTOOL. (Default step resolution registered in MD51_52OPTOOL is 20um).

For example, in case of your step resolution is 2 um:
- If you registered 2 um in the MD51_52OPTOOL, then please set step resolution = 1 in the device adapter.
- If you registered 1 um in the MD51_52OPTOOL, then please set step resolution = 2 in the device adapter.
