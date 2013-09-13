///////////////////////////////////////////////////////////////////////////////
// FILE:          devicelist.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Implementation of the Piezosystem device adapter.
//                
//                
// AUTHOR:        Chris Belter, cbelter@piezojena.com 15/07/13.  XYStage and ZStage by Chris Belter
//
// COPYRIGHT:     Piezosystem Jena, Germany, 2013
//
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES. 
//
//

#ifndef _DEVICELIST_H_
#define _DEVICELIST_H_

#include <string>

class devicelist
{
public:
	devicelist(void);
	~devicelist(void);

	bool isStage(std::string name);
	bool isShutter(std::string name);
	bool isXYStage(std::string name);
	bool isTritor(std::string name);
	bool isMirror(std::string name);
	bool isMirror1(std::string name);
	bool isMirror2(std::string name);
	bool isMirror3(std::string name);
};



#endif //_DEVICELIST_H_