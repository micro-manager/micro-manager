/*
File:		handle_list_if.h
Copyright:	Mad City Labs Inc., 2008
License:	Distributed under the BSD license.
*/
#ifndef _MCL_HANDLE_LIST_IF_H_
#define _MCL_HANDLE_LIST_IF_H_

class HandleListType;

bool HandleListCreate();

void HandleListDestroy();

int HandleListAddToLockedList(HandleListType* pUsbDevice);

void HandleListLock();

void HandleListUnlock();

int HandleListCount();

bool HandleExistsOnLockedList(HandleListType* device);

bool HandleExistsOnLockedList(int handle);

int HandleListRemoveSingleItem(HandleListType* device);

#endif