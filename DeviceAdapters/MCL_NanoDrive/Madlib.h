/*
File:		Madlib.h
Copyright:	Mad City Labs Inc., 2008
License:	Distributed under the BSD license.
*/
#ifndef _MADLIB_H_
#define _MADLIB_H_

#define		MCL_SUCCESS				 0
#define     MCL_GENERAL_ERROR		-1
#define		MCL_DEV_ERROR			-2
#define		MCL_DEV_NOT_ATTACHED	-3
#define		MCL_USAGE_ERROR			-4
#define		MCL_DEV_NOT_READY		-5
#define		MCL_ARGUMENT_ERROR		-6
#define		MCL_INVALID_AXIS		-7
#define		MCL_INVALID_HANDLE		-8
#define		MCL_INVALID_DRIVER      -9
#define		MCL_SEQ_NOT_VALID		-10

#pragma pack(push, 1)
struct ProductInformation {
	unsigned char  axis_bitmap; //bitmap of available axis
	short ADC_resolution;		//# of bits of resolution
	short DAC_resolution;		//# of bits of resolution
	short Product_id;
	short FirmwareVersion;
	short FirmwareProfile;
};
#pragma pack(pop)

#ifdef __cplusplus
	extern"C"{
#else
	typedef unsigned char bool;
#endif

#define MADLIB_API

MADLIB_API	bool	MCL_InitLibrary(void *heap);
MADLIB_API  void	MCL_ReleaseLibrary();

MADLIB_API  int		MCL_GrabAllHandles();
MADLIB_API  int		MCL_GetAllHandles(int *handles, int size);
MADLIB_API  void	MCL_ReleaseHandle(int handle);
MADLIB_API	double	MCL_SingleReadN(unsigned int axis, int handle);
MADLIB_API	int		MCL_SingleWriteN(double position, unsigned int axis, int handle);
MADLIB_API	double	MCL_GetCalibration(unsigned int axis, int handle);
MADLIB_API	int		MCL_GetSerialNumber(int handle);
MADLIB_API	int		MCL_GetProductInfo(struct ProductInformation *pi, int handle);
MADLIB_API	bool	MCL_DeviceAttached(int milliseconds, int handle);
MADLIB_API  int     MCL_GetCommandedPosition(double *xCom, double *yCom, double *zCom, int handle);

MADLIB_API	int		MCL_SequenceLoad(int axis, double* sequence, int seqSize, int handle);
MADLIB_API	int		MCL_SequenceClear(int handle);
MADLIB_API	int		MCL_SequenceStart(int handle);
MADLIB_API	int		MCL_SequenceStop(int handle);
MADLIB_API	int		MCL_SequenceGetMax(int* max, int handle);

MADLIB_API  bool    MCL_CorrectDriverVersion();

#ifdef __cplusplus
	}
#endif

#endif