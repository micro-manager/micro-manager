// COPYRIGHT:     (c) 2009-2015 Regents of the University of California
//                (c) 2016 Open Imaging, Inc.
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
//
// AUTHOR:        Mark Tsuchida, 2016
//                Based on older code by Arthur Edelstein, 2009

#pragma once

#include <Windows.h>

#include <string>
#include <vector>


std::vector<std::string> GetMonitorNames(bool excludePrimary, bool attachedOnly);
bool DetachMonitorFromDesktop(const std::string& monitorName);
bool AttachMonitorToDesktop(const std::string& monitorName, LONG posX, LONG posY);
bool GetMonitorRect(const std::string& monitorName, LONG& x, LONG& y, LONG& w, LONG& h);
void GetRightmostMonitorTopRight(const std::vector<std::string>& monitorNames,
      LONG& x, LONG& y);
bool GetBoundingRect(const std::vector<std::string>& monitorNames, RECT& rect);