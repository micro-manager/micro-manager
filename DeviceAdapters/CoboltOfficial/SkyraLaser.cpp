///////////////////////////////////////////////////////////////////////////////
// FILE:       SkyraLaser.cpp
// PROJECT:    MicroManager
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
// Cobolt Lasers Controller Adapter
//
// COPYRIGHT:     Cobolt AB, Stockholm, 2020
//                All rights reserved
//
// LICENSE:       MIT
//                Permission is hereby granted, free of charge, to any person obtaining a
//                copy of this software and associated documentation files( the "Software" ),
//                to deal in the Software without restriction, including without limitation the
//                rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
//                sell copies of the Software, and to permit persons to whom the Software is
//                furnished to do so, subject to the following conditions:
//                
//                The above copyright notice and this permission notice shall be included in all
//                copies or substantial portions of the Software.
//
//                THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
//                INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
//                PARTICULAR PURPOSE AND NONINFRINGEMENT.IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
//                HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
//                OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
//                SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
//
// CAUTION:       Use of controls or adjustments or performance of any procedures other than those
//                specified in owner's manual may result in exposure to hazardous radiation and
//                violation of the CE / CDRH laser safety compliance.
//
// AUTHORS:       Lukas Kalinski / lukas.kalinski@coboltlasers.com (2020)
//

#include "SkyraLaser.h"

//#include "StaticStringProperty.h"
#include "DeviceProperty.h"
//#include "ImmutableEnumerationProperty.h"
#include "LaserStateProperty.h"
//#include "MutableDeviceProperty.h"
#include "EnumerationProperty.h"
#include "NumericProperty.h"
//#include "LaserShutterProperty.h"
#include "NoShutterCommandLegacyFix.h"

using namespace std;
using namespace cobolt;

SkyraLaser::SkyraLaser(
    LaserDriver* driver,
    const bool line1Enabled,
    const bool line2Enabled,
    const bool line3Enabled,
    const bool line4Enabled ) :
    Laser( "Skyra", driver )
{
    currentUnit_ = Milliamperes;
    powerUnit_ = Milliwatts;

    CreateNameProperty();
    CreateModelProperty();
    CreateSerialNumberProperty();
    CreateFirmwareVersionProperty();
    CreateAdapterVersionProperty();
    CreateOperatingHoursProperty();

    CreateKeyswitchProperty();
    CreateLaserStateProperty();
    CreateShutterProperty();

    if ( line1Enabled ) { CreateLineSpecificProperties( 1 ); }
    if ( line2Enabled ) { CreateLineSpecificProperties( 2 ); }
    if ( line3Enabled ) { CreateLineSpecificProperties( 3 ); }
    if ( line4Enabled ) { CreateLineSpecificProperties( 4 ); }
}

void SkyraLaser::CreateLineActivationProperty( const int line )
{
    using namespace legacy::no_shutter_command;
    
    skyra::LineActivationProperty* lineActivationProperty = new skyra::LineActivationProperty( line, MakeLineName( line ), laserDriver_, this );
    RegisterPublicProperty( lineActivationProperty );
    ( ( skyra::LaserShutterProperty* )shutter_ )->RegisterLineActivationProperty( lineActivationProperty );
}

void SkyraLaser::CreateWavelengthProperty( const int line )
{
    RegisterPublicProperty( new DeviceProperty( Property::String, MakeLineName( line ) + " Wavelength", laserDriver_, MakeLineCommand( "glw?", line ) ) );
}

void SkyraLaser::CreateCurrentSetpointProperty( const int line )
{
    MutableDeviceProperty* property = new NumericProperty<double>( MakeLineName( line ) + " Current Setpoint [" + currentUnit_ + "]",
        laserDriver_, MakeLineCommand( "glc?", line ), MakeLineCommand( "slc", line ), 0.0f, MaxCurrentSetpoint( line ) );
    
    RegisterPublicProperty( property );
}

void SkyraLaser::CreateCurrentReadingProperty( const int line )
{
    DeviceProperty* property = new DeviceProperty( Property::Float, MakeLineName( line ) + " Measured Current [" + currentUnit_ + "]",
        laserDriver_, MakeLineCommand( "i?", line ) );
    property->SetCaching( false );
    RegisterPublicProperty( property );
}

void SkyraLaser::CreatePowerSetpointProperty( const int line )
{
    std::string maxPowerSetpointResponse;
    if ( laserDriver_->SendCommand( MakeLineCommand( "gmlp?", line ), &maxPowerSetpointResponse ) != return_code::ok ) {

        Logger::Instance()->LogError( "SkyraLaser::CreatePowerSetpointProperty(): Failed to retrieve max power sepoint" );
        return;
    }

    const double maxPowerSetpoint = atof( maxPowerSetpointResponse.c_str() );
    
    MutableDeviceProperty* property = new NumericProperty<double>( MakeLineName( line ) + " Power Setpoint [" + powerUnit_ + "]",
        laserDriver_, MakeLineCommand( "glp?", line ), MakeLineCommand( "slp", line ), 0.0f, maxPowerSetpoint );
    RegisterPublicProperty( property );
}

void SkyraLaser::CreatePowerReadingProperty( const int line )
{
    DeviceProperty* property = new DeviceProperty( Property::String, MakeLineName( line ) + " Power Reading [" + powerUnit_ + "]",
        laserDriver_, MakeLineCommand( "pa?", line ) );
    property->SetCaching( false );
    RegisterPublicProperty( property );
}

void SkyraLaser::CreateLaserStateProperty()
{
    if ( IsInCdrhMode() ) {

        laserStateProperty_ = new LaserStateProperty( Property::String, "Laser State", laserDriver_, "gom?" );

        laserStateProperty_->RegisterState( "0", "Off",                 false );
        laserStateProperty_->RegisterState( "1", "Waiting for TEC",     false );
        laserStateProperty_->RegisterState( "2", "Waiting for Key",     false );
        laserStateProperty_->RegisterState( "3", "Warming Up",          false );
        laserStateProperty_->RegisterState( "4", "Completed",           true );
        laserStateProperty_->RegisterState( "5", "Fault",               false );
        laserStateProperty_->RegisterState( "6", "Aborted",             false );
        laserStateProperty_->RegisterState( "7", "Waiting for Remote",  false );
        laserStateProperty_->RegisterState( "8", "Standby",             false );

    } else {

        laserStateProperty_ = new LaserStateProperty( Property::String, "Laser State", laserDriver_, "l?" );

        laserStateProperty_->RegisterState( "0", "Off",                 true );
        laserStateProperty_->RegisterState( "1", "On",                  true );
    }

    RegisterPublicProperty( laserStateProperty_ );
}

void SkyraLaser::CreateShutterProperty()
{
    if ( IsShutterCommandSupported() ) {
        //shutter_ = new LaserShutterProperty( "Emission Status", laserDriver_, this ); // TODO: Fix once there is a shutter command on Skyra
    } else {
        shutter_ = new legacy::no_shutter_command::skyra::LaserShutterProperty( "Emission Status", laserDriver_, this );
    }
    
    RegisterPublicProperty( shutter_ );
}

void SkyraLaser::CreateRunModeProperty( const int line )
{
    EnumerationProperty* property = new EnumerationProperty( MakeLineName( line ) + " Run Mode", laserDriver_, MakeLineCommand( "gam?", line ) );
    property->SetCaching( false );

    property->RegisterEnumerationItem( "0", MakeLineCommand( "ecc", line ), EnumerationItem_RunMode_ConstantCurrent );
    property->RegisterEnumerationItem( "1", MakeLineCommand( "ecp", line ), EnumerationItem_RunMode_ConstantPower );
    property->RegisterEnumerationItem( "2", MakeLineCommand( "em",  line ), EnumerationItem_RunMode_Modulation );
    
    RegisterPublicProperty( property );
}

void SkyraLaser::CreateModulationCurrentLowSetpointProperty( const int line )
{
    MutableDeviceProperty* property = new NumericProperty<double>( MakeLineName( line ) + " Modulation Low Current Setpoint [" + currentUnit_ + "]",
        laserDriver_, MakeLineCommand( "glth?", line ), MakeLineCommand( "slth", line ), 0.0f, MaxCurrentSetpoint( line ) );

    RegisterPublicProperty( property );
}

void SkyraLaser::CreateModulationCurrentHighSetpointProperty( const int line )
{
    MutableDeviceProperty* property = new NumericProperty<double>( MakeLineName( line ) + " Modulation High Current Setpoint [" + currentUnit_ + "]",
        laserDriver_, MakeLineCommand( "gmc?", line ), MakeLineCommand( "smc", line ), 0.0f, MaxCurrentSetpoint( line ) );

    RegisterPublicProperty( property );
}

std::string SkyraLaser::MakeLineCommand( std::string command, const int line )
{
    return std::to_string( (long long) line ) + command;
}

std::string SkyraLaser::MakeLineName( const int line )
{
    return ( "Line " + std::to_string( (long long) line ) );
}

void SkyraLaser::CreateLineSpecificProperties( const int line )
{
    CreateLineActivationProperty( line );
    CreateWavelengthProperty( line );
    CreateRunModeProperty( line );
    CreatePowerSetpointProperty( line );
    CreatePowerReadingProperty( line );
    CreateCurrentSetpointProperty( line );
    CreateCurrentReadingProperty( line );
    CreateModulationCurrentHighSetpointProperty( line );
    
    if ( line == 1 ) { CreateModulationCurrentLowSetpointProperty( line ); }
}

double SkyraLaser::MaxCurrentSetpoint( const int line )
{
    std::string maxCurrentSetpointResponse;
    if ( laserDriver_->SendCommand( MakeLineCommand( "gmlc?", line ), &maxCurrentSetpointResponse ) != return_code::ok ) {

        Logger::Instance()->LogError( "SkyraLaser::MaxCurrentSetpoint(): Failed to retrieve max current sepoint" );
        return 0.0f;
    }

    return atof( maxCurrentSetpointResponse.c_str() );
}
