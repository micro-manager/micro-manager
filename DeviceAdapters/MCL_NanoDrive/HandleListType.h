/*
File:		HandleListType.h
Copyright:	Mad City Labs Inc., 2019
License:	Distributed under the BSD license.
*/
#pragma once

#define XY_TYPE 1
#define Z_TYPE  2

class HandleListType
{
public:
	HandleListType(int handle, int type, int axis1, int axis2);
	~HandleListType();

	bool IsControlling(int handle, int a1, int a2);

	int GetType();
	int GetHandle();
	int GetAxis1();
	int GetAxis2();

	void SetType(int type);
	void SetHandle(int handle);
	void SetAxis1(int axis);
	void SetAxis2(int axis);

	bool operator==(const HandleListType &rhs);

private:
	int handle_;
	int type_;
	int axis1_;
	int axis2_;
};