/*
  Copyright (c) 2018, Zachary Phillips (UC Berkeley)
  All rights reserved.


  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are met:
      Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
      Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
      Neither the name of the UC Berkley nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
  DISCLAIMED. IN NO EVENT SHALL ZACHARY PHILLIPS (UC BERKELEY) BE LIABLE FOR ANY
  DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
  LOSS OF USE, DATA , OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/


#ifndef COMMAND_CONSTANTS_H
#define COMMAND_CONSTANTS_H

// List of command indicies in below array
#define COMMAND_COUNT 7

#define CMD_HELP_IDX 0
#define CMD_SET_POSITION 1
#define CMD_GET_POSITION 2
#define CMD_SET_ACCEL 3
#define CMD_SET_SPEED 4
#define CMD_IS_RUNNING 5
#define CMD_STOP 6

// Syntax is: {short command, long command, description, syntax}
const char* command_list[COMMAND_COUNT][4] = {

  // High-level Commands
  {"?", "help", "Display help info", "?"},
  {"sp", "setposition", "Get position in steps", "sp.20"},
  {"gp", "getposition", "Runs setup routine again, for resetting LED array", "gp"},
  {"sa", "setacceleration", "Set maximum acceleration", "sa.20"},
  {"ss", "setspeed", "Set maximum speed", "ss.20"},
  {"im", "ismoving", "Check if moving", "ismoving"},
  {"x", "stop", "Stop Motion", "x"},


};

#endif
