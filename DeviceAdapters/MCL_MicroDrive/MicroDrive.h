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

MICRODRIVE_API bool MCL_InitLibrary(void* heap);
MICRODRIVE_API void MCL_ReleaseLibrary();

MICRODRIVE_API	void	MCL_DLLVersion(short *version, short *revision);

MICRODRIVE_API  int		MCL_InitHandle();
MICRODRIVE_API	int		MCL_GrabHandle(short device);
MICRODRIVE_API	int		MCL_InitHandleOrGetExisting();
MICRODRIVE_API	int		MCL_GrabHandleOrGetExisting(short device);
MICRODRIVE_API  int		MCL_GetHandleBySerial(short serial);
MICRODRIVE_API  int		MCL_GrabAllHandles();
MICRODRIVE_API  int		MCL_GetAllHandles(int *handles, int size);
MICRODRIVE_API  int		MCL_NumberOfCurrentHandles();
MICRODRIVE_API  void	MCL_ReleaseHandle(int handle);
MICRODRIVE_API  void	MCL_ReleaseAllHandles();

MICRODRIVE_API  bool MCL_CorrectDriverVersion();
MICRODRIVE_API  void MCL_PrintDriverVersion();

MICRODRIVE_API	int		MCL_MicroDriveMoveStatus(int *isMoving, int handle);
MICRODRIVE_API	int		MCL_MicroDriveWait(int handle);
MICRODRIVE_API	int		MCL_MicroDriveGetWaitTime(int *wait, int handle);
MICRODRIVE_API	int		MCL_MicroDriveStatus(unsigned char *status, int handle);
MICRODRIVE_API	int		MCL_MicroDriveStop(unsigned char* status, int handle);

MICRODRIVE_API	int		MCL_MicroDriveMoveProfileXYZ_MicroSteps(
							double velocityX,
							int microStepsX,
							double velocityY,
							int microStepsY,
							double velocityZ,
							int microStepsZ,
							int handle
							);
MICRODRIVE_API	int		MCL_MicroDriveMoveProfile_MicroSteps(
							unsigned int axis,
							double velocity,
							int microSteps,
							int handle
							);
MICRODRIVE_API	int		MCL_MicroDriveMoveProfileXYZ(
							double velocityX,
							double distanceX,
							int roundingX, 
							double velocityY,
							double distanceY,
							int roundingY, 
							double velocityZ,
							double distanceZ,
							int roundingZ,
							int handle
							);

MICRODRIVE_API	int		MCL_MicroDriveMoveProfile(
							unsigned int axis,
							double velocity,
							double distance,
							int rounding,
							int handle
							);
MICRODRIVE_API	int		MCL_MicroDriveSingleStep(unsigned int axis, int direction, int handle);
MICRODRIVE_API	int		MCL_MicroDriveResetEncoders(unsigned char* status, int handle);
MICRODRIVE_API	int		MCL_MicroDriveResetXEncoder(unsigned char* status, int handle);
MICRODRIVE_API	int		MCL_MicroDriveResetYEncoder(unsigned char* status, int handle);
MICRODRIVE_API	int		MCL_MicroDriveResetZEncoder(unsigned char* status, int handle);
MICRODRIVE_API  int		MCL_MicroDriveInformation(
							double* encoderResolution,
							double* stepSize,
							double* maxVelocity,
							double* maxVelocityTwoAxis,
							double* maxVelocityThreeAxis,
							double* minVelocity,
							int handle);
MICRODRIVE_API	int		MCL_MicroDriveReadEncoders(double* x, double* y, double *z, int handle);
MICRODRIVE_API	int		MCL_MicroDriveCurrentMicroStepPosition(int *microStepsX, int *microStepsY, int *microStepsZ, int handle);

MICRODRIVE_API int		MCL_MD1MoveProfile_MicroSteps(double velocity, int microSteps, int handle);
MICRODRIVE_API int		MCL_MD1MoveProfile(double velocity, double distance, int rounding, int handle);
MICRODRIVE_API int		MCL_MD1SingleStep(int direction, int handle);
MICRODRIVE_API int		MCL_MD1ResetEncoder(unsigned char* status, int handle);
MICRODRIVE_API int		MCL_MD1ReadEncoder(double* position, int handle);
MICRODRIVE_API int		MCL_MD1CurrentMicroStepPosition(int *microSteps, int handle);
MICRODRIVE_API int		MCL_MD1Information(
							double* encoderResolution,
							double* stepSize,
							double* maxVelocity,
							double* minVelocity,
							int handle);

MICRODRIVE_API	int 	MCL_GetFirmwareVersion(short *version, short *profile, int handle);
MICRODRIVE_API	int		MCL_GetSerialNumber(int handle);
MICRODRIVE_API	void	MCL_PrintDeviceInfo(int handle); 
MICRODRIVE_API	bool	MCL_DeviceAttached(int milliseconds, int handle);
MICRODRIVE_API  int     MCL_GetProductID(unsigned short *PID, int handle);
MICRODRIVE_API  int     MCL_GetAxisInfo(unsigned char *axis_bitmap, int handle);
MICRODRIVE_API  int     MCL_MicroStepsPerStep(double *mps, int handle);
MICRODRIVE_API  int     MCL_GetFullStepSize(double *stepSize, int handle);
MICRODRIVE_API	int		MCL_GetCalibrationInfo(double *x, double *y, double *z, int handle);

#ifdef __cplusplus
	}
#endif

#endif