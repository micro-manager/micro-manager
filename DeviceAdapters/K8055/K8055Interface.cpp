/*
   This file is part of the libk8055 Library.

   The libk8055 Library is free software; you can redistribute it and/or
   modify it under the terms of the GNU Lesser General Public
   License as published by the Free Software Foundation; either
   version 2.1 of the License, or (at your option) any later version.

   The libk8055 Library is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
   Lesser General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program; if not, write to the
   Free Software Foundation, Inc.,
   59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

   http://opensource.org/licenses/

   Copyleft (C) 2005 by Sven Lindberg
     k8055@k8055.mine.nu

   Copyright (C) 2007 by Pjetur G. Hjaltason
       pjetur@pjetur.net
       Commenting, general rearrangement of code, bugfixes,
       python interface with swig and simple k8055 python class


	Input packet format

	+---+---+---+---+---+---+---+---+
	|DIn|Sta|A1 |A2 |   C1  |   C2  |
	+---+---+---+---+---+---+---+---+
	DIn = Digital input in high nibble, except for input 3 in 0x01
	Sta = Status,x01 = OK ?
	A1  = Analog input 1, 0-255
	A2  = Analog input 2, 0-255
	C1  = Counter 1, 16 bits (lsb)
	C2  = Counter 2, 16 bits (lsb)

	Output packet format

	+---+---+---+---+---+---+---+---+
	|CMD|DIG|An1|An2|Rs1|Rs2|Dbv|Dbv|
	+---+---+---+---+---+---+---+---+
	CMD = Command 
	DIG = Digital output bitmask
	An1 = Analog output 1 value, 0-255
	An2 = Analog output 2 value, 0-255
	Rs1 = Reset counter 1, command 3
	Rs2 = Reset counter 3, command 4
	Dbv = Debounce value for counter 1 and 2, command 1 and 2

	Or split by commands

	Cmd 0, Reset ??
	Cmd 1, Set debounce Counter 1
	+---+---+---+---+---+---+---+---+
	|CMD|   |   |   |   |   |Dbv|   |
	+---+---+---+---+---+---+---+---+
	Cmd 2, Set debounce Counter 2
	+---+---+---+---+---+---+---+---+
	|CMD|   |   |   |   |   |   |Dbv|
	+---+---+---+---+---+---+---+---+
	Cmd 3, Reset counter 1
	+---+---+---+---+---+---+---+---+
	| 3 |   |   |   | 00|   |   |   |
	+---+---+---+---+---+---+---+---+
	Cmd 4, Reset counter 2
	+---+---+---+---+---+---+---+---+
	| 4 |   |   |   |   | 00|   |   |
	+---+---+---+---+---+---+---+---+
	cmd 5, Set analog/digital
	+---+---+---+---+---+---+---+---+
	| 5 |DIG|An1|An2|   |   |   |   |
	+---+---+---+---+---+---+---+---+

**/


#include "K8055.h"
#include "K8055Interface.h"
#include <cstdio>
#include <math.h>

#define STR_BUFF 256
#define PACKET_LEN 8

#define K8055_ERROR -1

#define DIGITAL_INP_OFFSET 0
#define DIGITAL_OUT_OFFSET 1
#define ANALOG_1_OFFSET 2
#define ANALOG_2_OFFSET 3
#define COUNTER_1_OFFSET 4
#define COUNTER_2_OFFSET 6

#define CMD_RESET 0x00
#define CMD_SET_DEBOUNCE_1 0x01
#define CMD_SET_DEBOUNCE_2 0x01
#define CMD_RESET_COUNTER_1 0x03
#define CMD_RESET_COUNTER_2 0x04
#define CMD_SET_ANALOG_DIGITAL 0x05

/* set debug to 0 to not print excess info */
int DEBUG = 1;

/* globals for datatransfer */
//unsigned char data_in[PACKET_LEN+1], data_out[PACKET_LEN+1];

/* char* device_id[]; */

K8055Interface::K8055Interface() :
initialized_ (false)
{
}

K8055Interface::~K8055Interface()
{
}


int K8055Interface::ReadK8055Data(MM::Device& device, MM::Core& core)
{
    unsigned long read;
    return core.ReadFromSerial(&device, port_.c_str(), data_in, 8, read);
}

int K8055Interface::WriteK8055Data(MM::Device& device, MM::Core& core, unsigned char cmd)
{
    data_out[0] = cmd;
    return core.WriteToSerial(&device, port_.c_str(), data_out, 8);
}


int K8055Interface::OpenDevice()
{
   if (!initialized_)
      return ERR_BOARD_NOT_FOUND;

   std::string vellemanName = "Velleman K8055-";
   if (port_.find(vellemanName) != 0)
     return ERR_BOARD_NOT_FOUND;

   return DEVICE_OK;
}

long K8055Interface::ReadAnalogChannel(MM::Device& device, MM::Core& core, long channel)
{
    if (channel == 1 || channel == 2)
    {
        if ( ReadK8055Data(device, core) == 0)
        {
            if (channel == 2)
                return data_in[ANALOG_2_OFFSET];
            else
                return data_in[ANALOG_1_OFFSET];
        }
        else
            return K8055_ERROR;
    }
    else
        return K8055_ERROR;
}

int K8055Interface::ReadAllAnalog(MM::Device& device, MM::Core& core, long *data1, long *data2)
{
    if (ReadK8055Data(device, core) == 0)
    {
        *data1 = data_in[ANALOG_1_OFFSET];
        *data2 = data_in[ANALOG_2_OFFSET];
        return 0;
    }
    else
        return K8055_ERROR;
}

int K8055Interface::OutputAnalogChannel(MM::Device& device, MM::Core& core, long channel, long data)
{
    if (channel == 1 || channel == 2)
    {
        if (channel == 2)
            data_out[ANALOG_2_OFFSET] = (unsigned char)data;
        else
            data_out[ANALOG_1_OFFSET] = (unsigned char)data;

        return WriteK8055Data(device, core, CMD_SET_ANALOG_DIGITAL);
    }
    else
        return K8055_ERROR;
}

int K8055Interface::OutputAllAnalog(MM::Device& device, MM::Core& core, long data1, long data2)
{
    data_out[2] = (unsigned char)data1;
    data_out[3] = (unsigned char)data2;

    return WriteK8055Data(device, core, CMD_SET_ANALOG_DIGITAL);
}

int K8055Interface::ClearAllAnalog(MM::Device& device, MM::Core& core)
{
    return OutputAllAnalog(device, core, 0, 0);
}

int K8055Interface::ClearAnalogChannel(MM::Device& device, MM::Core& core, long channel)
{
    if (channel == 1 || channel == 2)
    {
        if (channel == 2)
            return OutputAnalogChannel(device, core, 2, 0);
        else
            return OutputAnalogChannel(device, core, 1, 0);
    }
    else
        return K8055_ERROR;
}

int K8055Interface::SetAnalogChannel(MM::Device& device, MM::Core& core, long channel)
{
    if (channel == 1 || channel == 2)
    {
        if (channel == 2)
            return OutputAnalogChannel(device, core, 2, 0xff);
        else
            return OutputAnalogChannel(device, core, 1, 0xff);
    }
    else
        return K8055_ERROR;

}

int K8055Interface::SetAllAnalog(MM::Device& device, MM::Core& core)
{
    return OutputAllAnalog(device, core, 0xff, 0xff);
}

int K8055Interface::WriteAllDigital(MM::Device& device, MM::Core& core, long data)
{
    data_out[1] = (unsigned char)data;
    return WriteK8055Data(device, core, CMD_SET_ANALOG_DIGITAL);
}

int K8055Interface::ClearDigitalChannel(MM::Device& device, MM::Core& core, long channel)
{
    unsigned char data;

    if (channel > 0 && channel < 9)
    {
        data = data_out[1] ^ (unsigned char) (1 << (channel-1));
        return WriteAllDigital(device, core, data);
    }
    else
        return K8055_ERROR;
}

int K8055Interface::ClearAllDigital(MM::Device& device, MM::Core& core)
{
    return WriteAllDigital(device, core, 0x00);
}

int K8055Interface::SetDigitalChannel(MM::Device& device, MM::Core& core, long channel)
{
    unsigned char data;

    if (channel > 0 && channel < 9)
    {
        data = data_out[1] | (unsigned char) (1 << (channel-1));
        return WriteAllDigital(device, core, data);
    }
    else
        return K8055_ERROR;
}

int K8055Interface::SetAllDigital(MM::Device& device, MM::Core& core)
{
    return WriteAllDigital(device, core, 0xff);
}

int K8055Interface::ReadDigitalChannel(MM::Device& device, MM::Core& core, long channel)
{
    int rval;
    if (channel > 0 && channel < 9)
    {
        if ((rval = ReadAllDigital(device, core)) == K8055_ERROR) return K8055_ERROR;
        return ((rval & (1 << (channel-1))) > 0);
    }
    else
        return K8055_ERROR;
}

long K8055Interface::ReadAllDigital(MM::Device& device, MM::Core& core)
{
    int return_data = 0;

    if (ReadK8055Data(device, core) == 0)
    {
	return_data = (
		((data_in[0] >> 4) & 0x03) |  /* Input 1 and 2 */
		((data_in[0] << 2) & 0x04) |  /* Input 3 */
		((data_in[0] >> 3) & 0x18) ); /* Input 4 and 5 */

        return return_data;
    }
    else
        return K8055_ERROR;
}

/*
int K8055Interface::ReadAllValues(MM::Device& device, MM::Core& core, long int *data1, long int * data2, long int * data3, long int * data4, long int * data5)
{
    if (ReadK8055Data(device, core) == 0)
    {
	*data1 = (
		((data_in[0] >> 4) & 0x03) |  // Input 1 and 2 
		((data_in[0] << 2) & 0x04) |  // Input 3 
		((data_in[0] >> 3) & 0x18) ); // Input 4 and 5 
        *data2 = data_in[ANALOG_1_OFFSET];
        *data3 = data_in[ANALOG_2_OFFSET];
        *data4 = *((short int *)(&data_in[COUNTER_2_OFFSET]));
        *data5 = *((short int *)(&data_in[COUNTER_1_OFFSET]));
 	return 0;
    }
    else
        return K8055_ERROR;
}
*/

int K8055Interface::ResetCounter(MM::Device& device, MM::Core& core, long counterno)
{
    if (counterno == 1 || counterno == 2)
    {
        data_out[0] = 0x02 + (unsigned char)counterno;  /* counter selection */
        data_out[3 + counterno] = 0x00;
        return WriteK8055Data(device, core, data_out[0]);
    }
    else
        return K8055_ERROR;
}

long K8055Interface::ReadCounter(MM::Device& device, MM::Core& core, long counterno)
{
    if (counterno == 1 || counterno == 2)
    {
        if (ReadK8055Data(device, core) == 0)
        {
            if (counterno == 2)
                return *((short int *)(&data_in[COUNTER_2_OFFSET]));
            else
                return *((short int *)(&data_in[COUNTER_1_OFFSET]));
        }
        else
            return K8055_ERROR;
    }
    else
        return K8055_ERROR;
}

int K8055Interface::SetCounterDebounceTime(MM::Device& device, MM::Core& core, long counterno, long debouncetime)
{
    float value;

    if (counterno == 1 || counterno == 2)
    {
        data_out[0] = (unsigned char)counterno;
        /* the velleman k8055 use a exponetial formula to split up the
           debouncetime 0-7450 over value 1-255. I've tested every value and
           found that the formula dbt=0,338*value^1,8017 is closest to
           vellemans dll. By testing and measuring times on the other hand I
           found the formula dbt=0,115*x^2 quite near the actual values, a
           little below at really low values and a little above at really
           high values. But the time set with this formula is within +-4% */
        if (debouncetime > 7450)
            debouncetime = 7450;
        value = sqrtf(debouncetime / 0.115f);
        if (value > ((int)value + 0.49999999))  /* simple round() function) */
            value += 1;
        data_out[5 + counterno] = (unsigned char)value;
        if (DEBUG)
            fprintf(stderr, "Debouncetime%d value for k8055:%d\n",
                    (int)counterno, data_out[5 + counterno]);
        return WriteK8055Data(device, core, data_out[0]);
    }
    else
        return K8055_ERROR;
}
