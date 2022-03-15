/*
File:		handle_list_if.cpp
Copyright:	Mad City Labs Inc., 2019
License:	Distributed under the BSD license.
*/
#include "handle_list_if.h"
#include "HandleListType.h"

#include <vector>
using namespace std;

#include <windows.h>

HANDLE gHandleListMutex = 0;
vector<HandleListType> *gHandleList = NULL;

//Called once in dllmain process attach.
bool HandleListCreate()
{	
	//Create a mutex variable to control access to the HandleList
	::gHandleListMutex = CreateMutex(NULL, FALSE, NULL);
	if(::gHandleListMutex == 0)
		return false;
	
	::gHandleList = new vector<HandleListType>();
	if(::gHandleList == 0)
	{
		ReleaseMutex(::gHandleListMutex);
		CloseHandle(::gHandleListMutex);
		return false;
	}

	//Release the lock on the mutex we created
	ReleaseMutex(::gHandleListMutex);

	return true;
}

//Called once in dllmain process detach.
void HandleListDestroy()
{
	if (::gHandleList != NULL)
	{
		delete ::gHandleList;
	}
	CloseHandle(::gHandleListMutex);
}

void HandleListAddToLockedList(HandleListType hlt)
{
	if (::gHandleList == NULL)
		return;
	::gHandleList->push_back(hlt);
}

void HandleListLock()
{
	WaitForSingleObject(::gHandleListMutex, INFINITE);
}

void HandleListUnlock()
{
	ReleaseMutex(::gHandleListMutex);
}

bool HandleExistsOnLockedList(HandleListType device)
{
	if (::gHandleList == NULL)
		return false;

	for (vector<HandleListType>::iterator it = ::gHandleList->begin(); it != ::gHandleList->end(); ++it)
	{
		if ((*it).IsControlling(device.GetHandle(), device.GetAxis1(), device.GetAxis2())) {
			return true;
		}
	}
	return false;
}

int HandleListCount()
{
	int count = 0;

	for (vector<HandleListType>::iterator it = ::gHandleList->begin(); it != ::gHandleList->end(); ++it)
	{
		count++;
	}
	return count;
}


bool HandleExistsOnLockedList(int handle)
{
	if (::gHandleList == NULL)
		return false;

	for (vector<HandleListType>::iterator it = ::gHandleList->begin(); it != ::gHandleList->end(); ++it)
	{
		if ((*it).GetHandle() == handle) {
			return true;
		}
	}
	return false;
}

void HandleListRemoveSingleItem(HandleListType device)
{
	if (::gHandleList == NULL)
		return;

	int ii = 0;
	for (vector<HandleListType>::iterator it = ::gHandleList->begin(); it != ::gHandleList->end(); ++it)
	{
		if ((*it) == device) {
			::gHandleList->erase(::gHandleList->begin() + ii);
			break;
		}
		ii++;
	}
}