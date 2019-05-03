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

#include <AccelStepper.h>


#ifndef COMMAND_ROUTING_H
#define COMMAND_ROUTING_H

#define MAX_RESPONSE_LENGTH_SHORT 100


//#include <mk20dx128.h>

#define MAX_ARGUMENT_ELEMENT_LENGTH 10
#define MAX_COMMAND_LENGTH 20
#define MAX_ARGUMENT_COUNT_CHAR 1500

static const char SERIAL_LINE_ENDING[] = "\n";
static const char SERIAL_COMMAND_TERMINATOR[] = "-==-";

char output_buffer_short[MAX_RESPONSE_LENGTH_SHORT];

#include "commandconstants.h"

class CommandRouter {
  public:
    int getArgumentBitDepth(char * command_header);
    void route(char * command_header, int16_t argc, void ** argv);
    void processSerialStream();
    void printHelp();
    void printTerminator();
    void setDebug(int16_t argc, char * * argv);
    void setStepper(AccelStepper* stepper);

  private:
    // Standard element variables
    int debug = 0;

    // Serial command holders
    bool send_termination_char = true;
    char command [MAX_COMMAND_LENGTH + 1]; // Allow for terminating null byte
    char * * argv;
    char current_argument[MAX_ARGUMENT_ELEMENT_LENGTH + 1];
    AccelStepper* stepper;


    
    

    char * * argument_list = NULL;
    bool * argument_list_bool = NULL;
    uint8_t * argument_list_uint8 = NULL;
    uint16_t * argument_list_uint16 = NULL;
    int16_t * argument_led_number_list = NULL;
};

void CommandRouter::setStepper(AccelStepper* s)
{
  stepper = s;
}
void CommandRouter::printHelp()
{
  Serial.printf(F("-----------------------------------%s"), SERIAL_LINE_ENDING);
  Serial.printf(F("Command List: %s"), SERIAL_LINE_ENDING);
  Serial.printf(F("-----------------------------------%s"), SERIAL_LINE_ENDING);
  for (int16_t cIdx = 0; cIdx < COMMAND_COUNT; cIdx++)
  {
    Serial.printf(F("COMMAND: %s"), SERIAL_LINE_ENDING);
    Serial.print(command_list[cIdx][0]);
    Serial.print(" / ");
    Serial.print(command_list[cIdx][1]);
    Serial.print(SERIAL_LINE_ENDING);
    Serial.print(F("SYNTAX:"));
    Serial.print(command_list[cIdx][3]);
    Serial.print(SERIAL_LINE_ENDING);
    Serial.printf(F("DESCRIPTION:%s"), SERIAL_LINE_ENDING);
    Serial.print(command_list[cIdx][2]);
    Serial.print(SERIAL_LINE_ENDING);
    Serial.printf(F("-----------------------------------%s"), SERIAL_LINE_ENDING);
  }
}

void CommandRouter::route(char * command_header, int16_t argc, void ** argv)
{
  if ((strcmp(command_header, command_list[CMD_HELP_IDX][0]) == 0) || (strcmp(command_header, command_list[CMD_HELP_IDX][1]) == 0))
    printHelp();
  else if ((strcmp(command_header, command_list[CMD_SET_POSITION][0]) == 0) || (strcmp(command_header, command_list[CMD_SET_POSITION][1]) == 0))
  {
    //move to position
    stepper->moveTo((int) (atof(((char * *) argv)[0]) * 1600 / 360));
  }
  else if ((strcmp(command_header, command_list[CMD_GET_POSITION][0]) == 0) || (strcmp(command_header, command_list[CMD_GET_POSITION][1]) == 0))
  {
    //print the current position
    memset(&output_buffer_short, ' ', sizeof(output_buffer_short));
    sprintf(output_buffer_short, "%d", (int)((float)stepper->targetPosition() / 1600 * 360));
    Serial.printf("%s%s", output_buffer_short, SERIAL_LINE_ENDING);
  }
  
  else if ((strcmp(command_header, command_list[CMD_IS_RUNNING][0]) == 0) || (strcmp(command_header, command_list[CMD_IS_RUNNING][1]) == 0))
  {
    //print the current position
    memset(&output_buffer_short, ' ', sizeof(output_buffer_short));
    sprintf(output_buffer_short, "%d", (int) stepper->isRunning());
    Serial.printf("%s%s", output_buffer_short, SERIAL_LINE_ENDING);
  }
  else if ((strcmp(command_header, command_list[CMD_SET_SPEED][0]) == 0) || (strcmp(command_header, command_list[CMD_SET_SPEED][1]) == 0))
  {
    stepper->setMaxSpeed(atoi(((char * *) argv)[0]));
  }
  else if ((strcmp(command_header, command_list[CMD_SET_ACCEL][0]) == 0) || (strcmp(command_header, command_list[CMD_SET_ACCEL][1]) == 0))
  {
    stepper->setAcceleration(atoi(((char * *) argv)[0]));
  }
  else if ((strcmp(command_header, command_list[CMD_STOP][0]) == 0) || (strcmp(command_header, command_list[CMD_STOP][1]) == 0))
  {
    stepper->stop();
  }
  else
  {
      Serial.print(F("Command ["));
      Serial.print(command_header);
      Serial.print(F("] is not implemented yet."));
      Serial.print(SERIAL_LINE_ENDING);
  }
    
}

int CommandRouter::getArgumentBitDepth(char * command_header)
{
    return (-1);
}

void CommandRouter::processSerialStream()
{
  // Initialize command string
  memset(command, 0, sizeof(command));

  // Initialize empty argument element
  memset(current_argument, 0, sizeof(current_argument));

  // Initialize indexing variables used locally by this function
  uint16_t command_position = 0;
  uint16_t argument_element_position = 0;
  uint16_t argument_count = 0;
  uint16_t argument_led_count = 0;
  uint16_t argument_total_count = 0;
  uint16_t argument_max_led_count = 0;
  bool argument_flag = false;
  int argument_bit_depth = -1;
  int argument_led_number_pitch = -1;

  while (Serial.available() > 0)
  {
    const byte new_byte = Serial.read();
    switch (new_byte)
    {
      case '\n':   // end of text
        {
          command[command_position] = 0;     // terminating null byte
          if (argument_flag)
          {
            if (debug > 0) {
              Serial.print(F("Copying new argument inside newline with index "));
              Serial.print(argument_total_count);
              Serial.print(F(" with bit depth "));
              Serial.print(argument_bit_depth);
              Serial.print(SERIAL_LINE_ENDING);
            }

            // Character argument (standard)
            if (argument_bit_depth == -1)
            {
              argument_list[argument_count] = new char[argument_element_position + 1]; // Allow for null terminating byte
              memcpy(argument_list[argument_count], current_argument, sizeof(char) * argument_element_position + 1); // Also copy null terminating byte
            }
            else
            {
              if ((argument_bit_depth > 0) && (argument_total_count == 1))
              {
                if (argument_bit_depth == 1) // numerical argument (standard)
                  argument_list_bool = new bool[1];
                else if (argument_bit_depth == 8)
                  argument_list_uint8 = new uint8_t[1];
                else
                  argument_list_uint16 = new uint16_t[1];

                argument_led_number_list = new int16_t[1];
                argument_led_number_list[0] = 0;
              }
              if (argument_bit_depth == 1) // numerical argument (standard)
                argument_list_bool[argument_count]  = atoi(current_argument) > 0;
              else if (argument_bit_depth == 8)
                argument_list_uint8[argument_count]  = (uint8_t)atoi(current_argument);
              else
                argument_list_uint16[argument_count]  = strtoul(current_argument, NULL, 0);
            }

            // Increment number of optional arguments
            argument_count++;
            argument_total_count++;
          }

         

          // Parse command and arguments based on bit depth
          if (argument_bit_depth == -1)
            route(command, argument_count, (void **) argument_list);
          else if (argument_bit_depth == 1)
            route(command, argument_count, (void **) argument_list_bool);
          else if (argument_bit_depth == 8)
            route(command, argument_count, (void **) argument_list_uint8);
          else if (argument_bit_depth == 16)
            route(command, argument_count, (void **) argument_list_uint16);

          // Clear serial buffer so we don't act on any serial input received during command processing.
          while (Serial.available())
            Serial.read();

          if (argument_count > 0)
          {
            // Delete argument list elements
            if (argument_bit_depth == -1)
            {
              for (uint16_t argument_index = 0; argument_index < argument_count; argument_index++)
              {
                delete[] argument_list[argument_index];
                if (debug > 1)
                {
                  Serial.print(" Deallocated argument ");
                  Serial.print(argument_index);
                  Serial.print(SERIAL_LINE_ENDING);
                }
              }
              delete[] argument_list;
            }
            else if (argument_bit_depth == 1)
              delete[] argument_list_bool;
            else if (argument_bit_depth == 8)
              delete[] argument_list_uint8;
            else if (argument_bit_depth == 16)
              delete[] argument_list_uint16;

            if (argument_led_number_pitch >= 0)
              delete[] argument_led_number_list;
          }

          if (send_termination_char)
          {
            Serial.print(SERIAL_COMMAND_TERMINATOR);
            Serial.print(SERIAL_LINE_ENDING);
          }
          break;
        }
      case '.':   // dot SERIAL_DELIMITER
        {
          if (!argument_flag) { // This is the case where we've just finished a command and need to initialize argument parameters
            argument_flag = true;
            argument_count = 0;
            command[command_position] = 0; // Add null terminating byte

            // Get argument bit depth from command header
            argument_bit_depth = getArgumentBitDepth(command);


            // Initialize charater argument list if we're using
            if (argument_bit_depth <= 0)
              argument_list = new char * [MAX_ARGUMENT_COUNT_CHAR];

            if (debug > 1)
              Serial.printf("Switching to argument mode%s", SERIAL_LINE_ENDING);
          }

          else if (argument_bit_depth > 0 && (argument_flag && argument_total_count == 1))
          { // This is the case where we're running a numeric storage command (such as setSequenceValue) and need to collect the number of LEDs in the list (first argument), as provided by the user.
            if (debug > 1) {
              Serial.print("Processing LED count at index ");
              Serial.print(argument_total_count);
              Serial.print(SERIAL_LINE_ENDING);
              delay(10);
            }
            // Get argument LED count
            argument_max_led_count = strtoul(current_argument, NULL, 0);

            if (argument_max_led_count > 0)
            {
              // Initialize argument arrays using bit_depth
              if (argument_bit_depth == 1)
                argument_list_bool = new bool[argument_max_led_count];
              else if (argument_bit_depth == 8)
                argument_list_uint8 = new uint8_t[argument_max_led_count];
              else if (argument_bit_depth == 16)
                argument_list_uint16 = new uint16_t[argument_max_led_count];

              // Initialize LED number list
              argument_led_number_list = new int16_t [argument_max_led_count];
            }
            else
            { // Case where user types ssl.0 (no leds on)
              // Initialize argument arrays using bit_depth
              if (argument_bit_depth == 1)
              {
                argument_list_bool = new bool[1];
                argument_list_bool[0] = 0;
              }
              else if (argument_bit_depth == 8)
              {
                argument_list_uint8 = new uint8_t[1];
                argument_list_uint8[0] = 0;
              }
              else if (argument_bit_depth == 16)
              {
                argument_list_uint16 = new uint16_t[1];
                argument_list_uint16[0] = 0;
              }

              // Initialize LED number list
              argument_led_number_list = new int16_t [1];
              argument_led_number_list[0] = 0;
            }
          }
          else if ((argument_led_number_pitch > 0) && (((argument_total_count) % argument_led_number_pitch ) == 0))
          { // In this case, we store a LED number for a numerical list
          

            // If this argument is a LED number, store it in the appropriate array
            argument_led_number_list[argument_led_count + 1] = strtol(current_argument, NULL, 0);
            argument_led_count++; // Increment number of leds measured

            if (argument_led_count > argument_max_led_count)
            {
              Serial.print(F("ERROR - max led count (")); Serial.print(argument_max_led_count); Serial.printf(F(") reached!%s"), SERIAL_LINE_ENDING);
            }
          }
          else
          {
           
            // Copy argument or led value an argument_list

            // character argument (standard)
            if (argument_bit_depth == -1)
            {
              argument_list[argument_count] = new char[argument_element_position + 1]; // Allow for null terminating byte
              memcpy(argument_list[argument_count], current_argument, sizeof(char) * argument_element_position + 1); // Also copy null terminating byte
            }
            else if (argument_bit_depth == 1) // numerical argument (standard)
              argument_list_bool[argument_count]  = atoi(current_argument) > 0;
            else if (argument_bit_depth == 8)
              argument_list_uint8[argument_count]  = (uint8_t)atoi(current_argument);
            else
              argument_list_uint16[argument_count]  = strtoul(current_argument, NULL, 0);

         
            argument_count++; // Increment number of optional arguments
          }
          argument_total_count++;

          // Clear current argument string to save memory
          memset(current_argument, 0, sizeof(current_argument));
          argument_element_position = 0;
          break;
        }
      default:
        {
          // keep adding if not full ... allow for terminating null byte
          if (argument_flag)
          {
            if (argument_element_position > MAX_ARGUMENT_ELEMENT_LENGTH)
              Serial.printf(F("ERROR: Optional element was too long!%s"), SERIAL_LINE_ENDING);
            else
            {
              // append this to the current optional argument
              current_argument[argument_element_position] = new_byte;
              argument_element_position++; // increment optional position
            }
          }
          else
          {
            if (command_position >= MAX_COMMAND_LENGTH)
              Serial.printf(F("ERROR: Command was too long! %s"), SERIAL_LINE_ENDING);
            else
              command [command_position++] = new_byte;
          }
          break;
        }
    }  // end of switch
  }
}

#endif
