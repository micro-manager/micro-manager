/*
File:		mdutils.cpp
Copyright:	Mad City Labs Inc., 2019
License:	Distributed under the BSD license.
*/
#include "MCL_MicroDrive.h"
#include "mdutils.h"

unsigned short LimitBitMask(unsigned short pid, int axis, int direction)
{
	int bitShift = 0;
	unsigned short bitMask = 0;
	if (pid == MICRODRIVE1)
	{
		bitShift = 2 + direction;
	}
	else
	{
		bitShift = (2 * (axis - 1)) + direction;
		if (axis > 2)
			bitShift += 2;
	}
	bitMask = 0x1 << bitShift;
	return bitMask;
}

bool IsAxisADefaultTirfModuleAxis(unsigned short pid, unsigned char axisBitmap, int axis)
{
	int numAxes = 0;
	// Count number of axes
	while (axisBitmap != 0)
	{
		numAxes += axisBitmap & 0x01;
		axisBitmap >>= 1;
	}

	bool isDefaultTirfModuleAxis = false;
	switch (pid)
	{
	case MICRODRIVE3:
		if ((numAxes == 1) || (numAxes == 3 && axis == M3AXIS))
			isDefaultTirfModuleAxis = true;
		break;
	case MICRODRIVE4:
		if (axis == M4AXIS)
			isDefaultTirfModuleAxis = true;
		break;
	case MICRODRIVE6:
		if ((numAxes == 5 && axis == M5AXIS || numAxes == 6 && axis == M6AXIS))
			isDefaultTirfModuleAxis = true;
	default:
		isDefaultTirfModuleAxis = false;
		break;
	}
	return isDefaultTirfModuleAxis;
}