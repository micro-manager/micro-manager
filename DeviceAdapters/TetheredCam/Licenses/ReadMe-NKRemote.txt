NKRemoteTest.exe is a simple example program to interface with NKRemote.
It can be used to run the camera as a simple webcam, adjust the aperture
and shutter speed or simply take individual pictures.

e.g.
First run NKRemote

To take a single picture:
NKRemoteTest

To take a webcam sequence of 5 shots with 45 secs between each shot:
NKRemoteTest -w 5 -t 45

Files included:
NKRemoteTest.exe - compiled console application
NKRemoteLib.dll - DLL used by NKRemoteTest.exe to interface with NKRemote
NKRemoteLib.h - header file for C++ aplpications using the DLL
NKRemoteLib.lib - lib for C++ apps to link to the DLL
Source - directory containing a VC++ project and source code for NKRemoteTest.exe

This software comes with no warranty whatsoever and is used entirely at your own
risk. It is completely free and you may modify it in any way you wish.

Chris Breeze
24 January 2009
www.breezesys.com
