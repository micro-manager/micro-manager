///////////////////////////////////////////////////////////////////////////////
// FILE:          PerformanceTimer.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     utilities
//-----------------------------------------------------------------------------
// DESCRIPTION:   See PerformanceTimer.h
//
//
//
// COPYRIGHT:     University of California, San Francisco, 2009
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
#include "PerformanceTimer.h"

#include <utility>
#include <windows.h>

PerformanceTimer::PerformanceTimer(void)
{
	LARGE_INTEGER ii;
	QueryPerformanceFrequency(&ii);
	this->frequency_ = 1./ii.QuadPart;
	Reset();
}

void PerformanceTimer::Reset(void)
{
	LARGE_INTEGER ii;
	DWORD_PTR oldmask = ::SetThreadAffinityMask(::GetCurrentThread(), 0);
	QueryPerformanceCounter(&ii);
	::SetThreadAffinityMask(::GetCurrentThread(), oldmask);
	this->startTime_ = ii.QuadPart;
}

double PerformanceTimer::elapsed(void)
{
	LARGE_INTEGER ii;
	
	DWORD_PTR oldmask = ::SetThreadAffinityMask(::GetCurrentThread(), 0);
	QueryPerformanceCounter(&ii);
	::SetThreadAffinityMask(::GetCurrentThread(), oldmask);
	return ( ii.QuadPart - startTime_)*frequency_;

}

