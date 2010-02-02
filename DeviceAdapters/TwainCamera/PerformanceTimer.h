///////////////////////////////////////////////////////////////////////////////
// FILE:          PerformanceTimer.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     utilities
//-----------------------------------------------------------------------------
// DESCRIPTION:   This class implements a high-resolution timer
// AUTHOR:        Karl Hoover
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


#pragma once

class PerformanceTimer
{
public:
	PerformanceTimer(void);		// create and initialize the timer
	double elapsed(void);		// return elapsed time in seconds
	~PerformanceTimer(void){};
	void Reset(void);				// re-initialize the timer.
private:
	long long startTime_;
	double frequency_;
};
