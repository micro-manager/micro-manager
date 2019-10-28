/*
File:		handle_list_if.h
Copyright:	Mad City Labs Inc., 2019
License:	Distributed under the BSD license.
*/
#pragma once

class HandleListType;

bool HandleListCreate();

void HandleListDestroy();

void HandleListAddToLockedList(HandleListType pUsbDevice);

void HandleListLock();

void HandleListUnlock();

bool HandleExistsOnLockedList(HandleListType device);

bool HandleExistsOnLockedList(int handle);

void HandleListRemoveSingleItem(HandleListType device);
