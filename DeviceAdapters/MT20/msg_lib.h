// Olympus MT20 Device Adapter
//
// Copyright 2010 
// Michael Mitchell
// mich.r.mitchell@gmail.com
//
// Last modified 27.7.10
//
//
// This file is part of the Olympus MT20 Device Adapter.
//
// This device adapter requires the Real-Time Controller board that came in the original
// Cell^R/Scan^R/Cell^M/etc. computer to work. It uses TinyXML ( http://www.grinninglizard.com/tinyxml/ )
// to parse XML messages from the device.
//
// The Olympus MT20 Device Adapter is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// any later version.
//
// The Olympus MT20 Device Adapter is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with the Olympus MT20 Device Adapter.  If not, see <http://www.gnu.org/licenses/>.

#ifndef MSG_LIB_H_
#define MSG_LIB_H_


//////////////////////////////////////////////////////////////////////////////
// External error codes
//////////////////////////////////////////////////////////////////////////////
#define ERR_UNABLE_TO_CONNECT		10000
#define ERR_INVALID_STATE			10001
#define ERR_INVALID_POSITION		10002
#define ERR_EXECUTING_CMD			10003
#define ERR_SET_FAILED				10004
#define ERR_REPLACE_BURNER_SOON		10005


//////////////////////////////////////////////////////////////////////////////
// PCMsg command library
//////////////////////////////////////////////////////////////////////////////
#define ENUMERATE_DEVICES			0

#define GET_SETTING_SC_0			100
#define GET_SETTING_LPT_0			101
#define GET_SETTING_BB_0			102
#define GET_SETTING_BB_0_LS_0		103
#define GET_SETTING_SER_0_STAGE_0	104
#define GET_SETTING_CAN_0			105
#define GET_SETTING_CAN_0_LS_0		106
#define GET_SETTING_CAN_0_LS_1		107
#define GET_SETTING_CAN_0_LS_2		108
#define GET_SETTING_CAN_0_FILTWL_0	109
#define GET_SETTING_CAN_0_FILTWL_1	110
#define GET_SETTING_CAN_0_TURRET_0	111
#define GET_SETTING_CAN_0_LASER_0	112
#define GET_SETTING_CAN_0_LASER_1	113
#define GET_SETTING_CAN_0_LASER_2	114
#define GET_SETTING_CAN_0_STAGE_0	115

#define GET_STATE_SC_0				200
#define GET_STATE_LPT_0				201
#define GET_STATE_BB_0				202
#define GET_STATE_BB_0_LS_0			203
#define GET_STATE_SER_0_STAGE_0		204
#define GET_STATE_CAN_0				205
#define GET_STATE_CAN_0_LS_0		206
#define GET_STATE_CAN_0_LS_1		207
#define GET_STATE_CAN_0_LS_2		208
#define GET_STATE_CAN_0_FILTWL_0	209
#define GET_STATE_CAN_0_FILTWL_1	210
#define GET_STATE_CAN_0_TURRET_0	211
#define GET_STATE_CAN_0_LASER_0		212
#define GET_STATE_CAN_0_LASER_1		213
#define GET_STATE_CAN_0_LASER_2		214
#define GET_STATE_CAN_0_STAGE_0		215

#define ENTER_STANDBY				1000
#define EXIT_STANDBY				1001

#define BURNER_ON					1002
#define BURNER_OFF					1003

#define EXPERIMENT					1100
#define GO_EXP						1101

#define OPEN_SHUTTER				1200
#define CLOSE_SHUTTER				1201
#define FIRE_SHUTTER				1202

#define SET_ATTENUATOR_100			1300
#define SET_ATTENUATOR_89			1301
#define SET_ATTENUATOR_78			1302
#define SET_ATTENUATOR_71			1303
#define SET_ATTENUATOR_68			1304
#define SET_ATTENUATOR_57			1305
#define SET_ATTENUATOR_42			1306
#define SET_ATTENUATOR_32			1307
#define SET_ATTENUATOR_23			1308
#define SET_ATTENUATOR_11			1309
#define SET_ATTENUATOR_12			1310
#define SET_ATTENUATOR_8			1311
#define SET_ATTENUATOR_4			1312
#define SET_ATTENUATOR_2			1313

#define SET_FILTWL_0				1400
#define SET_FILTWL_1				1401
#define SET_FILTWL_2				1402
#define SET_FILTWL_3				1403
#define SET_FILTWL_4				1404
#define SET_FILTWL_5				1405
#define SET_FILTWL_6				1406
#define SET_FILTWL_7				1407




const char msg_begin[] = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n<PCMsg MsgId=\"";

// msg_num == 0
const char enumerate_devices[] = "\">\r\n<Controlling>\r\n<Enumerate Id=\"*\" Type=\"Devices\"/>\r\n</Controlling>\r\n</PCMsg>\r\n";


// msg_num == 100
const char get_setting_SC_0[] = "\">\r\n<Controlling>\r\n<GetSetting><Device Id=\"SC.0\"/></GetSetting>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 101
const char get_setting_LPT_0[] = "\">\r\n<Controlling>\r\n<GetSetting><Device Id=\"LPT.0\"/></GetSetting>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 102
const char get_setting_BB_0[] = "\">\r\n<Controlling>\r\n<GetSetting><Device Id=\"BB.0\"/></GetSetting>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 103
const char get_setting_BB_0_LS_0[] = "\">\r\n<Controlling>\r\n<GetSetting><Device Id=\"BB.0-LS.0\"/></GetSetting>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 104
const char get_setting_SER_0_Stage_0[] = "\">\r\n<Controlling>\r\n<GetSetting><Device Id=\"SER.0-Stage.0\"/></GetSetting>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 105
const char get_setting_CAN_0[] = "\">\r\n<Controlling>\r\n<GetSetting><Device Id=\"CAN.0\"/></GetSetting>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 106
const char get_setting_CAN_0_LS_0[] = "\">\r\n<Controlling>\r\n<GetSetting><Device Id=\"CAN.0-LS.0\"/></GetSetting>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 107
const char get_setting_CAN_0_LS_1[] = "\">\r\n<Controlling>\r\n<GetSetting><Device Id=\"CAN.0-LS.1\"/></GetSetting>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 108
const char get_setting_CAN_0_LS_2[] = "\">\r\n<Controlling>\r\n<GetSetting><Device Id=\"CAN.0-LS.2\"/></GetSetting>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 109
const char get_setting_CAN_0_Filtwl_0[] = "\">\r\n<Controlling>\r\n<GetSetting><Device Id=\"CAN.0-Filtwl.0\"/></GetSetting>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 110
const char get_setting_CAN_0_Filtwl_1[] = "\">\r\n<Controlling>\r\n<GetSetting><Device Id=\"CAN.0-Filtwl.1\"/></GetSetting>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 111
const char get_setting_CAN_0_Turret_0[] = "\">\r\n<Controlling>\r\n<GetSetting><Device Id=\"CAN.0-Turret.0\"/></GetSetting>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 112
const char get_setting_CAN_0_Laser_0[] = "\">\r\n<Controlling>\r\n<GetSetting><Device Id=\"CAN.0-Laser.0\"/></GetSetting>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 113
const char get_setting_CAN_0_Laser_1[] = "\">\r\n<Controlling>\r\n<GetSetting><Device Id=\"CAN.0-Laser.1\"/></GetSetting>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 114
const char get_setting_CAN_0_Laser_2[] = "\">\r\n<Controlling>\r\n<GetSetting><Device Id=\"CAN.0-Laser.2\"/></GetSetting>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 115
const char get_setting_CAN_0_Stage_0[] = "\">\r\n<Controlling>\r\n<GetSetting><Device Id=\"CAN.0-Stage.0\"/></GetSetting>\r\n</Controlling>\r\n</PCMsg>\r\n";


// msg_num == 200
const char get_state_SC_0[] = "\">\r\n<Controlling>\r\n<GetState><Device Id=\"SC.0\"/></GetState>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 201
const char get_state_LPT_0[] = "\">\r\n<Controlling>\r\n<GetState><Device Id=\"LPT.0\"/></GetState>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 202
const char get_state_BB_0[] = "\">\r\n<Controlling>\r\n<GetState><Device Id=\"BB.0\"/></GetState>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 203
#define GET_STATE_BB_0_LS_0 203
const char get_state_BB_0_LS_0[] = "\">\r\n<Controlling>\r\n<GetState><Device Id=\"BB.0-LS.0\"/></GetState>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 204
const char get_state_SER_0_Stage_0[] = "\">\r\n<Controlling>\r\n<GetState><Device Id=\"SER.0-Stage.0\"/></GetState>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 205
const char get_state_CAN_0[] = "\">\r\n<Controlling>\r\n<GetState><Device Id=\"CAN.0\"/></GetState>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 206
const char get_state_CAN_0_LS_0[] = "\">\r\n<Controlling>\r\n<GetState><Device Id=\"CAN.0-LS.0\"/></GetState>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 207
const char get_state_CAN_0_LS_1[] = "\">\r\n<Controlling>\r\n<GetState><Device Id=\"CAN.0-LS.1\"/></GetState>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 208
const char get_state_CAN_0_LS_2[] = "\">\r\n<Controlling>\r\n<GetState><Device Id=\"CAN.0-LS.2\"/></GetState>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 209
const char get_state_CAN_0_Filtwl_0[] = "\">\r\n<Controlling>\r\n<GetState><Device Id=\"CAN.0-Filtwl.0\"/></GetState>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 210
const char get_state_CAN_0_Filtwl_1[] = "\">\r\n<Controlling>\r\n<GetState><Device Id=\"CAN.0-Filtwl.1\"/></GetState>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 211
const char get_state_CAN_0_Turret_0[] = "\">\r\n<Controlling>\r\n<GetState><Device Id=\"CAN.0-Turret.0\"/></GetState>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 212
const char get_state_CAN_0_Laser_0[] = "\">\r\n<Controlling>\r\n<GetState><Device Id=\"CAN.0-Laser.0\"/></GetState>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 213
const char get_state_CAN_0_Laser_1[] = "\">\r\n<Controlling>\r\n<GetState><Device Id=\"CAN.0-Laser.1\"/></GetState>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 214
const char get_state_CAN_0_Laser_2[] = "\">\r\n<Controlling>\r\n<GetState><Device Id=\"CAN.0-Laser.2\"/></GetState>\r\n</Controlling>\r\n</PCMsg>\r\n";

// msg_num == 215
const char get_state_CAN_0_Stage_0[] = "\">\r\n<Controlling>\r\n<GetState><Device Id=\"CAN.0-Stage.0\"/></GetState>\r\n</Controlling>\r\n</PCMsg>\r\n";



// Enter standby; queries: get_state_BB_0_LS_0	get_state_SER_0_Stage_0	get_state_CAN_0_Stage_0	get_state_BB_0	get_setting_BB_0_LS_0
// get_setting_CAN_0_Turret_0	get_setting_CAN_0_Filtwl_0	get_setting_SC_0	get_setting_BB_0	get_setting_CAN_0_LS_0
// msg_num == 1000
const char enter_standby[] = "\">\r\n<Controlling>\r\n<SetDevState Id=\"BB.0-LS.0\" DevState=\"stby\"/>\r\n</Controlling>\r\n</PCMsg>\r\n";

// Exit standby; queries as for enter_standby
// msg_num == 1001
const char exit_standby[] = "\">\r\n<Controlling>\r\n<SetDevState Id=\"BB.0-LS.0\" DevState=\"act\"/>\r\n</Controlling>\r\n</PCMsg>\r\n";

// Turn burner on; query get_state_BB_0_LS_0 reports status
// msg_num == 1002
const char burner_on[] = "\">\r\n<Controlling>\r\n<SetLampState Id=\"BB.0-LS.0-Lamp.0\">1</SetLampState>\r\n</Controlling>\r\n</PCMsg>\r\n";

// Turn burner off; query get_state_BB_0_LS_0 reports status
// msg_num == 1003
const char burner_off[] = "\">\r\n<Controlling>\r\n<SetLampState Id=\"BB.0-LS.0-Lamp.0\">0</SetLampState>\r\n</Controlling>\r\n</PCMsg>\r\n";


// Begin an experiment description
// msg_num = 1100
const char experiment[] = "\">\r\n<Experiment ExpId=\"scspec_killseq\">\r\n</Experiment>\r\n</PCMsg>\r\n";


// Carry out experiment
// msg_num = 1101
const char go_exp[] = "\">\r\n<Controlling>\r\n<GoExp>999</GoExp>\r\n</Controlling>\r\n</PCMsg>\r\n";


// Commands to be executed during experiment
	
	// open shutter; query get_state_BB_0_LS_0 reports status
	// msg_num = 1200
	const char open_shutter[] = "\">\r\n<Experiment ExpId=\"999\">\r\n<Commands ComId=\"1\">\r\n<SetShutter Id=\"BB.0-LS.0-Shut.0\" Par=\"Val\"><WaitTime>100</WaitTime><State>1</State><Store>0</Store></SetShutter>\r\n</Commands>\r\n</Experiment>\r\n</PCMsg>\r\n";
	
	// close shutter; query get_state_BB_0_LS_0 reports status
	// msg_num = 1201
	const char close_shutter[] = "\">\r\n<Experiment ExpId=\"999\">\r\n<Commands ComId=\"1\">\r\n<SetShutter Id=\"BB.0-LS.0-Shut.0\" Par=\"Val\"><WaitTime>100</WaitTime><State>0</State><Store>0</Store></SetShutter>\r\n</Commands>\r\n</Experiment>\r\n</PCMsg>\r\n";

////	commands for deprecated shutter fire command

//	// open shutter for the specified number (number inserted between fire_shutter_1 and fire_shutter_2) of microseconds
//	// msg_num == 1202
//	const char fire_shutter_1[] = "\">\r\n<Experiment ExpId=\"999\">\r\n<Commands ComId=\"0\">\r\n<SetVar Id=\"21\"><WaitTime>100</WaitTime><Expression>";
//	const char fire_shutter_2[] = "</Expression></SetVar>\r\n<WaitNOP Par=\"Val\"><WaitTime>100</WaitTime><Store>0</Store></WaitNOP>\r\n<Repeat>\r\n<RepeatCycles>1</RepeatCycles>\r\n<Commands ComId=\"0\">\r\n<If>\r\n<WaitTime>0></WaitTime>\r\n<Expression>Var23 MOD 2</Expression>\r\n<IfTrue>\r\n<Commands ComId=\"0\">\r\n<SetShutter Id=\"BB.0-LS.0-Shut.0\" Par=\"Val\"><WaitTime>100</WaitTime><State>1</State><Store>0</Store></SetShutter>\r\n</Commands>\r\n</IfTrue>\r\n</If>\r\n<WaitNOP Par=\"DAddr\"><WaitTime>21</WaitTime><Store>0</Store></WaitNOP>\r\n<If>\r\n<WaitTime>0</WaitTime>\r\n<Expression>Var23 MOD 2</Expression>\r\n<IfTrue>\r\n<Commands ComId=\"0\">\r\n<SetShutter Id=\"BB.0-LS.0-Shut.0\" Par=\"Val\"><WaitTime>100</WaitTime><State>0</State><Store>0</Store></SetShutter>\r\n</Commands>\r\n</IfTrue>\r\n</If>\r\n\r\n</Commands>\r\n</Repeat>\r\n</Commands>\r\n</Experiment>\r\n</PCMsg>\r\n";

	// set attenuator value; concatenate set_att_1 + transX + set_att_2
	const char set_att_1[] = "\">\r\n<Experiment ExpId=\"999\">\r\n<Commands ComId=\"1\">\r\n<SetAttenuator Id=\"BB.0-LS.0-Att.0\" Par=\"Val\"><WaitTime>100</WaitTime><Position>";
	const char set_att_2[] = "</Position><Store>0></Store></SetAttenuator>\r\n</Commands>\r\n</Experiment>\r\n</PCMsg>\r\n";
	// msg_num = 1300
	const int trans100 = 0;
	// msg_num = 1301
	const int trans89 = 1;
	// msg_num = 1302
	const int trans78 = 2;
	// msg_num = 1303
	const int trans71 = 3;
	// msg_num = 1304
	const int trans68 = 4;
	// msg_num = 1305
	const int trans57 = 5;
	// msg_num = 1306
	const int trans42 = 6;
	// msg_num = 1307
	const int trans32 = 7;
	// msg_num = 1308
	const int trans23 = 8;
	// msg_num = 1309
	const int trans11 = 9;
	// msg_num = 1310
	const int trans12 = 10;
	// msg_num = 1311
	const int trans8 = 11;
	// msg_num = 1312
	const int trans4 = 12;
	// msg_num = 1313
	const int trans2 = 13;

	// set filterwheel position; concatenate set_filtwl_1 + filtwl_X + set_filtwl_2
	const char set_filtwl_1[] = "\">\r\n<Experiment ExpId=\"999\">\r\n<Commands ComId=\"1\">\r\n<SetFilterwheel Id=\"BB.0-LS.0-Filtwl.0\" Par=\"Val\"><WaitTime>100</WaitTime><Position>";
	const char set_filtwl_2[] = "</Position><Store>0></Store></SetFilterwheel>\r\n</Commands>\r\n</Experiment>\r\n</PCMsg>\r\n";
	// msg_num = 1400
	const int filtwl_empty0 = 0;
	// msg_num = 1401
	const int filtwl_empty1 = 1;
	// msg_num = 1402
	const int filtwl_empty2 = 2;
	// msg_num = 1403
	const int filtwl_empty3 = 3;
	// msg_num = 1404
	const int filtwl_empty4 = 4;
	// msg_num = 1405
	const int filtwl_empty5 = 5;
	// msg_num = 1406
	const int filtwl_empty6 = 6;
	// msg_num = 1407
	const int filtwl_empty7 = 7;


//////////////////////////////////////////////////////////////////////////////
// SCMsg response library
//////////////////////////////////////////////////////////////////////////////
#define LAN_ACK								-1
#define PARSE_ACK_COM						-2
#define PARSE_ACK_EXP						-3
#define PARSE_ERR_XML						-4
#define PARSE_ERR_DEVID						-5
#define PARSE_ERR_STATE						-6
#define PARSE_ERR_POS						-7
#define PARSE_ERR_ATTR						-8
#define PARSE_ERR_INVALID_ID				-9
#define SYS_ACK_COM							-10
#define EXP_EXECUTED						-11
#define CONTROL_ERR_SYS_ACTIVE				-12
#define CONTROL_ERR_LIGHTSOURCE_NOT_AVAIL	-13
#define UNKNOWN_ERROR						-14

const char lan_ack[] = "LAN Ack: Successfully connected to Server";

const char parse_ack_com[] = "Parsing Ack: Parsed successfully a Controlling Command List";
const char parse_ack_exp[] = "Parsing Ack: Parsed successfully an Experiment Description";

const char parse_err_xml[] = "Parsing Error: The XML-File is not correct";
const char parse_err_devID[] = "Parsing Error: Invalid Device-ID";
const char parse_err_state[] = "Parsing Error: Invalid State";
const char parse_err_pos[] = "Parsing Error: Invalid Position";
const char parse_err_attr[] = "Parsing Error: Invalid Attribute";
const char parse_err_invalID[] = "Parsing Error: Invalid Experiment Id";

const char sys_ack_com[] = "System Ack: Controlling Command executed";

const char exp_executed[] = "Experiment executed</RepText>\n  <ExpErrors>0</ExpErrors>";

const char control_err_sys_active[] = "Controlling Error: System already active";
const char control_err_lightsource_not_available[] = "Controlling Error: Lightsource not available";

const char unknown_error[] = "ErrorReport";


#endif