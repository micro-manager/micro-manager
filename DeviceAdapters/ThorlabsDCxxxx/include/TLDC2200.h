//==============================================================================
//
// Title:		TLDC2200
// Purpose:		A short description of the interface.
//
// Created on:	9/30/2014 at 4:22:59 PM by cwestphal.
// Copyright:	. All Rights Reserved.
//
//==============================================================================

#ifndef __TLDC2200_H__
#define __TLDC2200_H__

#ifdef __cplusplus
    extern "C" {
#endif

//==============================================================================
// Include files
//==============================================================================
#include "vpptype.h"
#include "TLDC2200_defines.h"  


//==============================================================================
// Global functions
//==============================================================================  

ViStatus _VI_FUNC TLDC2200_init (ViRsrc rsrcName, ViBoolean id_query, ViBoolean reset_instr, ViPSession vi);

ViStatus _VI_FUNC TLDC2200_close  (ViSession vi);  


//==============================================================================
// Resource functions
//==============================================================================

ViStatus _VI_FUNC TLDC2200_get_device_count (ViSession vi, ViPUInt32 device_count);


ViStatus _VI_FUNC TLDC2200_get_device_info  (	ViSession vi,
                                             ViUInt32 device_index,
                                             ViChar manufacturer[],
                                             ViChar model_name[],
                                             ViChar serial_number[],
                                             ViPBoolean device_available,
                                             ViChar resource_name[]);
//==============================================================================
// Utility functions
//==============================================================================
ViStatus _VI_FUNC TLDC2200_reset (ViSession vi);

ViStatus _VI_FUNC TLDC2200_self_test (ViSession vi, ViPInt16 test_result, ViChar test_message[]);

ViStatus _VI_FUNC TLDC2200_error_query (ViSession vi, ViPInt32 error_code, ViChar error_message[]);

ViStatus _VI_FUNC TLDC2200_error_message (ViSession vi, ViStatus status_code, ViChar message[]);  

ViStatus _VI_FUNC TLDC2200_revision_query (ViSession vi, ViChar driver_rev[], ViChar instr_rev[]);

ViStatus _VI_FUNC TLDC2200_get_head_info (ViSession vi, ViChar serialNumber[], ViChar name[], ViPInt32 type);

//==============================================================================
// Configuration functions
//==============================================================================
ViStatus _VI_FUNC TLDC2200_get_limit_current_range (ViSession vi,
                                                    ViUInt16 terminal,
                                                    ViReal64 voltage,
                                                    ViPReal64 minimumCurrent,
                                                    ViPReal64 maximumCurrent);

ViStatus _VI_FUNC TLDC2200_setLimitCurrent (ViSession vi, ViReal32 limit); 
ViStatus _VI_FUNC TLDC2200_getLimitCurrent (ViSession vi, ViPReal32 limit);

ViStatus _VI_FUNC TLDC2200_get_limit_voltage_range (ViSession vi,
                                                    ViUInt16 terminal,
                                                    ViReal64 current,
                                                    ViPReal64 minimumVoltage,
                                                    ViPReal64 maximumVoltage);

ViStatus _VI_FUNC TLDC2200_set_limit_voltage (ViSession vi, ViReal64 limitVoltage);
ViStatus _VI_FUNC TLDC2200_get_limit_voltage (ViSession vi,ViPReal64 limitVoltage);

ViStatus _VI_FUNC TLDC2200_setOperationMode (ViSession vi, ViInt32 operationMode); 
ViStatus _VI_FUNC TLDC2200_getOperationMode (ViSession vi, ViPInt32 operationMode);

ViStatus _VI_FUNC TLDC2200_set_output_terminal  (ViSession vi, ViUInt16 terminal); 
ViStatus _VI_FUNC TLDC2200_get_output_terminal (ViSession vi,ViPUInt16 terminal);

ViStatus _VI_FUNC TLDC2200_setLedOnOff (ViSession vi, ViBoolean LEDOnOff); 
ViStatus _VI_FUNC TLDC2200_getLedOnOff (ViSession vi, ViPBoolean LEDOutputState);

ViStatus _VI_FUNC TLDC2200_test_head (ViSession vi, ViUInt16 terminal);

ViStatus _VI_FUNC TLDC2200_get_output_condition (ViSession vi, ViPBoolean outputCondition);  
ViStatus _VI_FUNC TLDC2200_get_limit_current_protection  (ViSession vi, ViPBoolean limitCurrent); 
ViStatus _VI_FUNC TLDC2200_get_limit_voltage_protection (ViSession vi, ViPBoolean limitVoltage); 
ViStatus _VI_FUNC TLDC2200_get_interlock_protection (ViSession vi, ViPBoolean interlockCircuitProtection); 
ViStatus _VI_FUNC TLDC2200_get_driver_over_temperature_protection (ViSession vi, ViPBoolean driverOverTemperature);
ViStatus _VI_FUNC TLDC2200_get_head_over_temperature_protection (ViSession vi, ViPBoolean headOverTemperature);

ViStatus _VI_FUNC TLDC2200_setConstCurrent (ViSession vi,  ViReal32 current);
ViStatus _VI_FUNC TLDC2200_getConstCurrent (ViSession vi, ViPReal32 current);

ViStatus _VI_FUNC TLDC2200_set_brightness (ViSession vi, ViReal64 brightness);
ViStatus _VI_FUNC TLDC2200_get_brightness (ViSession vi, ViPReal64 brightness);

ViStatus _VI_FUNC TLDC2200_setPWMCurrent (ViSession vi,ViReal32 current); 
ViStatus _VI_FUNC TLDC2200_getPWMCurrent (ViSession vi, ViPReal32 current);

ViStatus _VI_FUNC TLDC2200_setPWMFrequency (ViSession vi, ViReal64 frequency);
ViStatus _VI_FUNC TLDC2200_getPWMFrequency (ViSession vi, ViPReal64 frequency);

ViStatus _VI_FUNC TLDC2200_setPWMDutyCycle (ViSession vi, ViInt32 dutyCycle);
ViStatus _VI_FUNC TLDC2200_getPWMDutyCycle (ViSession vi, ViPInt32 dutyCycle);

ViStatus _VI_FUNC TLDC2200_setPWMCounts (ViSession vi, ViInt32 counts);  
ViStatus _VI_FUNC TLDC2200_getPWMCounts (ViSession vi, ViPInt32 counts);

ViStatus _VI_FUNC TLDC2200_set_pulse_brightness (ViSession vi, ViReal64 brightness); 
ViStatus _VI_FUNC TLDC2200_get_pulse_brightness (ViSession vi, ViPReal64 brightness);

ViStatus _VI_FUNC TLDC2200_set_pulse_on_time  (ViSession vi,ViReal64 onTime); 
ViStatus _VI_FUNC TLDC2200_get_pulse_on_time  (ViSession vi, ViPReal64 onTime);

ViStatus _VI_FUNC TLDC2200_set_pulse_off_time (ViSession vi,ViReal64 offTime); 
ViStatus _VI_FUNC TLDC2200_get_pulse_off_time (ViSession vi,ViPReal64 offTime);

ViStatus _VI_FUNC TLDC2200_set_pulse_counts (ViSession vi, ViInt32 counts); 
ViStatus _VI_FUNC TLDC2200_get_pulse_counts  (ViSession vi, ViPInt32 counts);

ViStatus _VI_FUNC TLDC2200_get_internal_modulation_current_max_range (	ViSession vi, 
																								ViPReal64 minimum, 
																								ViPReal64 maximum);

ViStatus _VI_FUNC TLDC2200_set_internal_modulation_current_max (ViSession vi,ViReal64 currentMax); 
ViStatus _VI_FUNC TLDC2200_get_internal_modulation_current_max (ViSession vi, ViPReal64 currentMax);

ViStatus _VI_FUNC TLDC2200_get_internal_modulation_current_min_range (	ViSession vi, 
																								ViPReal64 minimum, 
																								ViPReal64 maximum);

ViStatus _VI_FUNC TLDC2200_set_internal_modulation_current_min  (ViSession vi, ViReal64 currentMin);
ViStatus _VI_FUNC TLDC2200_get_internal_modulation_current_min (ViSession vi, ViPReal64 currentMax);

ViStatus _VI_FUNC TLDC2200_set_internal_modulation_shape (ViSession vi,ViInt32 shape); 
ViStatus _VI_FUNC TLDC2200_get_internal_modulation_shape (ViSession vi,ViPInt32 shape);

ViStatus _VI_FUNC TLDC2200_set_internal_modulation_frequency (ViSession vi, ViReal64 frequency);
ViStatus _VI_FUNC TLDC2200_get_internal_modulation_frequency (ViSession vi, ViPReal64 frequency);

ViStatus _VI_FUNC TLDC2200_set_ttl_modulation_current (ViSession vi, ViReal64 current); 
ViStatus _VI_FUNC TLDC2200_get_ttl_modulation_current  (ViSession vi, ViPReal64 current);

//==============================================================================
// Measurement functions
//============================================================================== 
ViStatus _VI_FUNC TLDC2200_get_led_current_measurement (ViSession vi, ViPReal64 currentMeasurement);

ViStatus _VI_FUNC TLDC2200_get_led_voltage_measurement (ViSession vi, ViPReal64 voltageMeasurement);

ViStatus _VI_FUNC TLDC2200_get_temperature_measurement (ViSession vi, ViPReal64 temperatureMeasurement);

//==============================================================================
// System functions
//==============================================================================  
ViStatus _VI_FUNC TLDC2200_set_beeper_state (ViSession vi, ViBoolean state); 
ViStatus _VI_FUNC TLDC2200_get_beeper_state (ViSession vi, ViPBoolean state);

ViStatus _VI_FUNC TLDC2200_set_beeper_volume (ViSession vi, ViReal64 volume); 
ViStatus _VI_FUNC TLDC2200_get_beeper_volume (ViSession vi, ViPReal64 volume);

ViStatus _VI_FUNC TLDC2200_setDispBright (ViSession vi, ViInt32 displayBrightness);
ViStatus _VI_FUNC TLDC2200_getDispBright (ViSession vi, ViPInt32 displayBrightness);

ViStatus _VI_FUNC TLDC2200_set_auto_dimming (ViSession vi, ViBoolean state);
ViStatus _VI_FUNC TLDC2200_get_auto_dimming (ViSession vi, ViPBoolean state);

ViStatus _VI_FUNC TLDC2200_set_temperature_unit (ViSession vi, ViInt32 temperatureUnit); 
ViStatus _VI_FUNC TLDC2200_get_temperature_unit (ViSession vi, ViPInt32 temperatureUnit); 

ViStatus _VI_FUNC TLDC2200_getStatusRegister (ViSession vi, ViPInt32 statusRegister);  
ViStatus _VI_FUNC TLDC2200_get_measurement_status_register  (ViSession vi, ViPInt32 statusRegister);
ViStatus _VI_FUNC TLDC2200_get_standard_operation_register (ViSession vi, ViPInt32 standardRegister);

ViStatus _VI_FUNC TLDC2200_getWavelength (ViSession vi, ViPReal32 wavelength);

ViStatus _VI_FUNC TLDC2200_getForwardBias (ViSession vi,ViPReal32 forwardBias);

ViStatus _VI_FUNC TLDC2200_is_equipped_with_temperature_sensor (ViSession vi, ViPBoolean temperatureSensorAvailable);

//==============================================================================
// LED Head Information functions
//============================================================================== 
ViStatus _VI_FUNC TLDC2200_get_head_temperature_sensor_range (ViSession vi, ViPReal64 minimum, ViPReal64 maximum);

ViStatus _VI_FUNC TLDC2200_is_equipped_with_fan (ViSession vi, ViUInt16 terminal, ViPBoolean fanAvailable);



#ifdef __cplusplus
    }
#endif

#endif  /* ifndef __TLDC2200_H__ */
