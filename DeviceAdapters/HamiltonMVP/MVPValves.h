/*
 * Micro-Manager deivce adapter for Hamilton Modular Valve Positioner
 *
 * Author: Mark A. Tsuchida <mark@open-imaging.com>
 *
 * Copyright (C) 2018 Applied Materials, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

#include <string>
#include <vector>


enum MVPValveType {
   ValveTypeUnknown = 0,
   ValveTypeMin = 2,
   ValveTypeMax = 7,

   // The following match the values returned by the device
   ValveType8Ports = 2,
   ValveType6Ports = 3,
   ValveType3Ports = 4,
   ValveType2Ports180DegreesApart = 5,
   ValveType2Ports90DegreesApart = 6,
   ValveType4Ports = 7,
};


inline int GetValveNumberOfPositions(MVPValveType vt)
{
   switch (vt)
   {
      case ValveType8Ports:
         return 8;
      case ValveType6Ports:
         return 6;
      case ValveType3Ports:
         return 3;
      case ValveType2Ports180DegreesApart:
      case ValveType2Ports90DegreesApart:
         return 2;
      case ValveType4Ports:
         return 4;
      default:
         return 0;
   }
}


inline std::string GetValveTypeName(MVPValveType vt)
{
   switch (vt)
   {
      case ValveType8Ports:
         return "8 ports";
      case ValveType6Ports:
         return "6 ports";
      case ValveType3Ports:
         return "3 ports";
      case ValveType2Ports180DegreesApart:
         return "2 ports 180 degrees apart";
      case ValveType2Ports90DegreesApart:
         return "2 ports 90 degrees apart";
      case ValveType4Ports:
         return "4 ports";
      default:
         return "Unknown";
   }
}


inline MVPValveType GetValveTypeFromString(const std::string& s)
{
   if (s == "8 ports") return ValveType8Ports;
   if (s == "6 ports") return ValveType6Ports;
   if (s == "3 ports") return ValveType3Ports;
   if (s == "2 ports 180 degrees apart") return ValveType2Ports180DegreesApart;
   if (s == "2 ports 90 degrees apart") return ValveType2Ports90DegreesApart;
   if (s == "4 ports") return ValveType4Ports;
   return ValveTypeUnknown;
}


inline std::vector<std::string> GetAllValveTypeNames()
{
   std::vector<std::string> ret;
   for (int t = ValveTypeMin; t < ValveTypeMax; ++t)
   {
      ret.push_back(GetValveTypeName(MVPValveType(t)));
   }
   return ret;
}


inline int GetValveRotationAngle_Equal(int nPositions, bool ccw, int startPos, int destPos)
{
   int increment = 360 / nPositions;
   int startAngle = increment * startPos;
   int destAngle = increment * destPos;
   int deltaAngle = destAngle - startAngle;
   if (ccw)
      deltaAngle = -deltaAngle;
   if (deltaAngle >= 0)
      return deltaAngle;
   return 360 + deltaAngle;
}


inline int GetValveRotationAngle(MVPValveType vt, bool ccw, int startPos, int destPos)
{
   switch (vt)
   {
      case ValveType8Ports:
         return GetValveRotationAngle_Equal(8, ccw, startPos, destPos);
      case ValveType6Ports:
         return GetValveRotationAngle_Equal(6, ccw, startPos, destPos);
      case ValveType3Ports:
         {
            const int angle[3][3] = {
               {   0, 270, 180, },
               {  90,   0, 270  },
               { 180,  90,   0, },
            };
            return ccw ? angle[startPos][destPos] : angle[destPos][startPos];
         }
      case ValveType2Ports180DegreesApart:
         return GetValveRotationAngle_Equal(2, ccw, startPos, destPos);
      case ValveType2Ports90DegreesApart:
         {
            const int angle[2][2] = {
               {  0, 270, },
               { 90,   0, },
            };
            return ccw ? angle[startPos][destPos] : angle[destPos][startPos];
         }
      case ValveType4Ports:
         return GetValveRotationAngle_Equal(4, ccw, startPos, destPos);
      default:
         return 0;
   }
}
