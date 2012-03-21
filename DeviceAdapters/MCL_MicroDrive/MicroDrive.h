/*
File:		MicroDrive.h
Copyright:	Mad City Labs Inc., 2008
License:	Distributed under the BSD license.
*/
#ifndef _MICRODRIVE_H_
#define _MICRODRIVE_H_

#define		MCL_SUCCESS				 0
#define     MCL_GENERAL_ERROR		-1
#define		MCL_DEV_ERROR			-2
#define		MCL_DEV_NOT_ATTACHED	-3
#define		MCL_USAGE_ERROR			-4
#define		MCL_DEV_NOT_READY		-5
#define		MCL_ARGUMENT_ERROR		-6
#define		MCL_INVALID_AXIS		-7
#define		MCL_INVALID_HANDLE		-8

#define		MCL_INVALID_DRIVER		-9
#define     INVALID_VELOCITY        -10

#ifdef __cplusplus
	extern"C" {
#else
	typedef unsigned char bool;
#endif

#define MICRODRIVE_API

MICRODRIVE_API	bool	MCL_InitLibrary();
MICRODRIVE_API	void	MCL_ReleaseLibrary();

MICRODRIVE_API  int		MCL_InitHandle();
MICRODRIVE_API  void	MCL_ReleaseHandle(int handle);

MICRODRIVE_API  int		MCL_MicroDriveWait(int handle);
MICRODRIVE_API	int		MCL_MicroDriveMoveProfileXY(
							double velocityX,
							double distanceX,
							int roundingX, 
							double velocityY,
							double distanceY,
							int roundingY, 
							int handle
							);
MICRODRIVE_API	int		MCL_MicroDriveMoveProfile(
							unsigned int axis,
							double velocity,
							double distance,
							int rounding,
							int handle
							);
MICRODRIVE_API	int		MCL_MicroDriveStatus(unsigned char *status, int handle);
MICRODRIVE_API	int		MCL_MicroDriveStop(unsigned char* status, int handle);
MICRODRIVE_API	int		MCL_MicroDriveReadEncoders(double* x, double* y, int handle);
MICRODRIVE_API	int		MCL_MicroDriveResetEncoders(unsigned char* status, int handle);
MICRODRIVE_API	int		MCL_MicroDriveInformation(
							double* encoderResolution,
							double* stepSize,
							double* maxVelocity,
							double* minVelocity,
							int handle);
MICRODRIVE_API	bool	MCL_DeviceAttached(int milliseconds, int handle);
MICRODRIVE_API  bool	MCL_CorrectDriverVersion();

#ifdef __cplusplus
	}
#endif

#endif
