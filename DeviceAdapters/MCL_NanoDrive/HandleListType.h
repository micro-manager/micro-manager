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
	HandleListType(int handle, int type);
	~HandleListType();

	void Initialize(int handle, int type);
	
	int getType();
	int getHandle();

	void setType(int type);
	void setHandle(int handle);

	bool operator==(const HandleListType &rhs);

private:
	int handle_;
	int type_;
};