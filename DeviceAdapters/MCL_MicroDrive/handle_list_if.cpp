/*
File:		handle_list_if.cpp
Copyright:	Mad City Labs Inc., 2008
License:	Distributed under the BSD license.
*/
#include "heap.h"
#include "device_list.h"
#include "handle_list_if.h"
#include "HandleListType.h"

#include <windows.h>

HANDLE gHandleListMutex = 0;
DeviceList<HandleListType> *gHandleList = 0;

//Called once in dllmain process attach.
bool HandleListCreate()
{	
	//Create a mutex variable to control access to the HandleList
	::gHandleListMutex = CreateMutex(NULL, FALSE, NULL);
	if(::gHandleListMutex == 0)
		return false;
	
	::gHandleList =	(DeviceList<HandleListType>*)GlobalHeapAllocate(sizeof(DeviceList<HandleListType>));
	if(::gHandleList == 0)
	{
		ReleaseMutex(::gHandleListMutex);
		CloseHandle(::gHandleListMutex);
		return false;
	}

	::gHandleList->Init(GlobalHeapGetHandle());

	//Release the lock on the mutex we created
	ReleaseMutex(::gHandleListMutex);

	return true;
}

//Called once in dllmain process detach.
void HandleListDestroy()
{
	gHandleList->RemoveAll();

	GlobalHeapFree(::gHandleList);

	CloseHandle(::gHandleListMutex);
}

int HandleListAddToLockedList(HandleListType* pUsbDevice)
{
	if(!pUsbDevice)
		return -1;

	return ::gHandleList->AddToList(pUsbDevice);
}

void HandleListLock()
{
	WaitForSingleObject(::gHandleListMutex, INFINITE);
}

void HandleListUnlock()
{
	ReleaseMutex(::gHandleListMutex);
}

int HandleListCount()
{
	WaitForSingleObject(::gHandleListMutex, INFINITE);

	int numDevices = ::gHandleList->GetDeviceCount();

	ReleaseMutex(::gHandleListMutex);

	return numDevices;
}

bool HandleExistsOnLockedList(HandleListType* device)
{
	if (!device)
		return false;

	List<HandleListType>* curItem = ::gHandleList->GetHead();
	while(curItem)
	{
		if (*curItem->pd == *device){
			return true;
		}
		curItem = curItem->next;
	}
	return false;
}

bool HandleExistsOnLockedList(int handle)
{
	List<HandleListType>* curItem = ::gHandleList->GetHead();
	while(curItem)
	{
		if (curItem->pd->getHandle() == handle){
			return true;
		}

		curItem = curItem->next;
	}
	return false;
}

int HandleListRemoveSingleItem(HandleListType* device)
{
	if (!device)
		return -1;

	return ::gHandleList->DeleteSingleItem(device);
}