ChuoSeiki QT

Summary:                     Micro-Manager device adapters for QT-series controllers
Author:                      Duong Quang Anh(Chuo Precision Industrial Co. LTD. Japan)
                             Okumura Daisuke(Chuo Precision Industrial Co. LTD. Japan)
License:                     LGPL
Platforms:                   All (uses serial interface)
Devices:                     3-Axis: QT-AMM3, QT-ADM3, QT-ADM3-35
                             2-Axis: QT-AMH2A, QT-AMH2A-35, QT-ADM2, QT-ADM2-35
                             1-axis: QT-ADL1, QT-ADL1-35
Available since version:     1.4.23 20181103 (Nightly Builds)
*****************************************************************************************

The ChuoSeiki QT-series controller connect to PC via an RS232 interface. It does not require any special driver to run. However, if you connect the controller via a device such as a USB-RS232 converter, be sure to install its driver.

The ChuoSeiki QT device adapter can control one or more QT-controllers: you can register multi-device adapters in Hardware Configuration Wizard with different serial ports, and each device adapter controls 1 controller at one serial port. Exception: the 3-axis controllers can be controlled by 2 device adapters (one for AB axis, the other for C axis), which are registered at one same serial port.

To download the QT-series controller's user manual, please follow this link [1], select the correct controller and download the manual under the Product information bar.

Device Adapter Initialization
The "ChuoSeiki QT 2-Axis" requests to register the Serial port and Serial port settings.
The "ChuoSeiki QT 1-Axis" requests to register the Serial port, Serial port settings, and Axis name.

Note* Register the correct serial port that connects to the corresponding QT controller. If using 3-axis controller, the above 2 device adapters should be registered to 1 single serial port (see Note on Axis name).
Serial port settings

Baudrate                9600 bps
DataBits                8 bits
StopBits                1 bit
ParityBits              None bit
AnswerTimeout           500 ms
DelayBetweenCharsMs     0.00 ms
Handshaking             Off	

Default Stage settings

HighSpeed             2000 pps
LowSpeed              500 pps
StepSize              1 um
Acceleration time     100 ms

Please revised stage setting using Group and Preset in Micro-Manager. The setting can be saved in a .cfg file.
Note* LowSpeed must be smaller than HighSpeed. Then, be sure to change LowSpeed first if necessary.

Note* Axis Name Registration
The "ChuoSeiki QT 2-Axis" support the 2-axis and 3-axis controllers. In this device adapter, the 2 stages names are defined as "A" and "B", corresponding to the controller's A and B channels. It uses the functions that control two axes at the same time.

The "ChuoSeiki QT 1-Axis" support all the 1-axis, 2-axis, and 3-axis controllers. The axis name (default is "A") can be defined in the device adapter, as "A" or "B" or "C", corresponding to the controller's A or B or C channels.

To use the device adapter with a 3-axis controller, please register both "ChuoSeiki QT 2-Axis" and "ChuoSeiki QT 1-Axis" with the same Serial port. And remember to set axis name as "C" for the "ChuoSeiki QT 1-Axis". In that case, the "ChuoSeiki QT 2-Axis" will drive the channels A and B, and the "ChuoSeiki QT 1-Axis" will drive the channel C of the controller.