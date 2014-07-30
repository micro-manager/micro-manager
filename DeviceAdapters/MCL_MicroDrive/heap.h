/*
File:		heap.h
Copyright:	Mad City Labs Inc., 2008
License:	Distributed under the BSD license.
*/
#ifndef _HEAP_H_
#define _HEAP_H_

#include <windows.h>

bool GlobalHeapInit();

void GlobalHeapDestroy();

HANDLE GlobalHeapGetHandle();

void* GlobalHeapAllocate(SIZE_T size);

void GlobalHeapFree(void* ptr);

#endif