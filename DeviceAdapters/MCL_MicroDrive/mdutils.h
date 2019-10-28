/*
File:		mdutils.h
Copyright:	Mad City Labs Inc., 2019
License:	Distributed under the BSD license.
*/
#pragma once

#define FORWARD 1
#define REVERSE 0

unsigned short LimitBitMask(unsigned short pid, int axis, int direction);

bool IsAxisADefaultTirfModuleAxis(unsigned short pid, unsigned char axisBitmap, int axis);