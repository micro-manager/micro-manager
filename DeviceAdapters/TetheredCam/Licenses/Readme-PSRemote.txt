PSRemoteTest.exe is a simple example program to interface with PSRemote.
It can be used to run the camera as a simple webcam, zoom the lens or
simply take individual pictures.

e.g.
First run PSRemote.

To take a single picture:
PSRemoteTest

To take a webcam sequence of 5 shots with 45 secs between each shot:
PSRemoteTest -w 5 -i 45

For usage information:
PSRemoteTest -h

Files included:
PSRemoteTest.exe - compiled console application
PSRemoteLib.dll - DLL used by PSRemoteTest.exe to interface with PSRemote
PSRemoteLib.h - header file for C++ aplpications using the DLL
PSRemoteLib.lib - lib for C++ apps to link to the DLL
Source - directory containing a VC++ project and source code for PSRemoteTest.exe


This software comes with no warranty whatsoever and is used entirely at your own
risk. It is completely free and you may modify it in any way you wish.

Chris Breeze
12 January 2007
www.breezesys.com
