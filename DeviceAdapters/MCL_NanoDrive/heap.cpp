/*
File:		heap.cpp
Copyright:	Mad City Labs Inc., 2008
License:	Distributed under the BSD license.
*/
#include <windows.h>

HANDLE gHeap = 0;

bool GlobalHeapInit()
{
	::gHeap = HeapCreate(
					0,
					0x4000,		//16k initial size
					0x20000		//256k maximum heap size
					);

	return (::gHeap != 0);
}

void GlobalHeapDestroy()
{
	HeapDestroy(::gHeap);
}

HANDLE GlobalHeapGetHandle()
{
	return ::gHeap;
}

void* GlobalHeapAllocate(SIZE_T size)
{
	void *ptr = 0;
	ptr = HeapAlloc(::gHeap, HEAP_ZERO_MEMORY, size);

	return ptr;
}

void GlobalHeapFree(void* ptr)
{
	HeapFree(::gHeap, 0, ptr);
}