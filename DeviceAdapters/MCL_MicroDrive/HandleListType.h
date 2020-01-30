/*
File:		HandleListType.h
Copyright:	Mad City Labs Inc., 2019
License:	Distributed under the BSD license.
*/
#pragma once

#define XYSTAGE_TYPE 1
#define STAGE_TYPE  2
#define IGNORED_AXIS 0

class HandleListType
{
public:
	HandleListType(int handle, int type, int axis1, int axis2);
	~HandleListType();

	bool IsControlling(int handle, int a1, int a2);

	int getType();
	int getHandle();
	int getAxis1();
	int getAxis2();

	void setType(int type);
	void setHandle(int handle);
	void setAxis1(int axis);
	void setAxis2(int axis);

	bool operator==(const HandleListType &rhs);

private:
	int handle_;
	int type_;
	int axis1_;
	int axis2_;
};
