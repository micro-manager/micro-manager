/*
 * Micro-Manager deivce adapter for Hamilton Modular Valve Positioner
 *
 * Author: Mark A. Tsuchida <mark@open-imaging.com>
 *
 * Copyright (C) 2018 Open Imaging, Inc.
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

#include <gtest/gtest.h>

#include "MVPValves.h"

#include <iostream>


struct RotationAngleParams
{
   MVPValveType valveType;
   bool ccw;
   int startPos;
   int destPos;

   RotationAngleParams() {}

   RotationAngleParams(MVPValveType vt, bool ccw, int start, int dest) :
      valveType(vt),
      ccw(ccw),
      startPos(start),
      destPos(dest)
   {}

   friend std::ostream& operator<<(std::ostream& os, const RotationAngleParams& params);
};

std::ostream& operator<<(std::ostream& os, const RotationAngleParams& params)
{
   os << '[' << GetValveTypeName(params.valveType) << ", " <<
      (params.ccw ? "CCW" : "CW") << ", " <<
      params.startPos << "->" << params.destPos << ']';
   return os;
}


class ParameterizedValveRotationAngleTest : public ::testing::Test,
   public ::testing::WithParamInterface< std::pair<RotationAngleParams, int> >
{
protected:
   RotationAngleParams params_;
   int expected_;

   virtual void SetUp()
   {
      params_ = GetParam().first;
      expected_ = GetParam().second;
   }
};

TEST_P(ParameterizedValveRotationAngleTest, MatchWithExpected)
{
   ASSERT_EQ(expected_, GetValveRotationAngle(params_.valveType,
            params_.ccw, params_.startPos, params_.destPos));
}

INSTANTIATE_TEST_CASE_P(BasicTestCase, ParameterizedValveRotationAngleTest,
   ::testing::Values(
      std::make_pair(RotationAngleParams(
            ValveType2Ports90DegreesApart, false, 0, 0), 0),
      std::make_pair(RotationAngleParams(
            ValveType2Ports90DegreesApart, true, 0, 0), 0),
      std::make_pair(RotationAngleParams(
            ValveType2Ports90DegreesApart, false, 0, 1), 90),
      std::make_pair(RotationAngleParams(
            ValveType2Ports90DegreesApart, true, 0, 1), 270),
      std::make_pair(RotationAngleParams(
            ValveType2Ports90DegreesApart, false, 1, 0), 270),
      std::make_pair(RotationAngleParams(
            ValveType2Ports90DegreesApart, true, 1, 0), 90),
      std::make_pair(RotationAngleParams(
            ValveType2Ports90DegreesApart, false, 1, 1), 0),
      std::make_pair(RotationAngleParams(
            ValveType2Ports90DegreesApart, true, 1, 1), 0),

      std::make_pair(RotationAngleParams(
            ValveType3Ports, false, 0, 0), 0),
      std::make_pair(RotationAngleParams(
            ValveType3Ports, true, 0, 0), 0),
      std::make_pair(RotationAngleParams(
            ValveType3Ports, false, 0, 1), 90),
      std::make_pair(RotationAngleParams(
            ValveType3Ports, true, 0, 1), 270),
      std::make_pair(RotationAngleParams(
            ValveType3Ports, false, 0, 2), 180),
      std::make_pair(RotationAngleParams(
            ValveType3Ports, true, 0, 2), 180),
      std::make_pair(RotationAngleParams(
            ValveType3Ports, false, 1, 0), 270),
      std::make_pair(RotationAngleParams(
            ValveType3Ports, true, 1, 0), 90),
      std::make_pair(RotationAngleParams(
            ValveType3Ports, false, 1, 1), 0),
      std::make_pair(RotationAngleParams(
            ValveType3Ports, true, 1, 1), 0),
      std::make_pair(RotationAngleParams(
            ValveType3Ports, false, 1, 2), 90),
      std::make_pair(RotationAngleParams(
            ValveType3Ports, true, 1, 2), 270),
      std::make_pair(RotationAngleParams(
            ValveType3Ports, false, 2, 0), 180),
      std::make_pair(RotationAngleParams(
            ValveType3Ports, true, 2, 0), 180),
      std::make_pair(RotationAngleParams(
            ValveType3Ports, false, 2, 1), 270),
      std::make_pair(RotationAngleParams(
            ValveType3Ports, true, 2, 1), 90),
      std::make_pair(RotationAngleParams(
            ValveType3Ports, false, 2, 2), 0),
      std::make_pair(RotationAngleParams(
            ValveType3Ports, true, 2, 2), 0),

      std::make_pair(RotationAngleParams(
            ValveType8Ports, false, 0, 0), 0),
      std::make_pair(RotationAngleParams(
            ValveType8Ports, false, 0, 1), 45),
      std::make_pair(RotationAngleParams(
            ValveType8Ports, false, 0, 2), 90),
      std::make_pair(RotationAngleParams(
            ValveType8Ports, false, 1, 2), 45),

      std::make_pair(RotationAngleParams(
            ValveType8Ports, true, 0, 0), 0),
      std::make_pair(RotationAngleParams(
            ValveType8Ports, true, 0, 1), 315),
      std::make_pair(RotationAngleParams(
            ValveType8Ports, true, 0, 2), 270),
      std::make_pair(RotationAngleParams(
            ValveType8Ports, true, 1, 2), 315)
   ));


int main(int argc, char **argv)
{
   ::testing::InitGoogleTest(&argc, argv);
   return RUN_ALL_TESTS();
}
