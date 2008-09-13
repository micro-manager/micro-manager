/*
File:		HandleListType.h
Copyright:	Mad City Labs Inc., 2008
License:	Distributed under the BSD license.
*/
#ifndef _HANDLE_LIST_TYPE_
#define _HANDLE_LIST_TYPE_

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

#endif