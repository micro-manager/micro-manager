/*
File:		HandleListType.cpp
Copyright:	Mad City Labs Inc., 2008
License:	Distributed under the BSD license.
*/
#include "HandleListType.h"
#include <stdio.h>
#include <string>

HandleListType::HandleListType(int handle, int type)
{
	handle_ = handle;
	type_ = type;
}

HandleListType::~HandleListType()
{
}

void HandleListType::Initialize(int handle, int type)
{
	handle_ = handle;
	type_ = type;
}

int HandleListType::getType()
{
	return type_;
}

int HandleListType::getHandle()
{
	return handle_;
}

void HandleListType::setType(int type)
{
	type_ = type;
}

void HandleListType::setHandle(int handle)
{
	handle_ = handle;
}

bool HandleListType::operator==(const HandleListType &rhs)
{
	if (handle_ == rhs.handle_ && type_ == rhs.type_)
		return true;

	return false;
}