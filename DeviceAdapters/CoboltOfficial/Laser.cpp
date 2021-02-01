///////////////////////////////////////////////////////////////////////////////
// FILE:       Laser.cpp
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

#include <assert.h>
#include "Laser.h"
#include "Logger.h"

#include "LaserDriver.h"
#include "StaticStringProperty.h"
#include "DeviceProperty.h"
#include "ImmutableEnumerationProperty.h"
#include "LaserStateProperty.h"
#include "MutableDeviceProperty.h"
#include "EnumerationProperty.h"
#include "NumericProperty.h"
#include "LaserShutterProperty.h"
#include "NoShutterCommandLegacyFix.h"

using namespace std;
using namespace cobolt;

const std::string Laser::Milliamperes = "mA";
const std::string Laser::Amperes = "A";
const std::string Laser::Milliwatts = "mW";
const std::string Laser::Watts = "W";

const std::string Laser::EnumerationItem_On = "on";
const std::string Laser::EnumerationItem_Off = "off";
const std::string Laser::EnumerationItem_Enabled = "enabled";
const std::string Laser::EnumerationItem_Disabled = "disabled";

const std::string Laser::EnumerationItem_RunMode_ConstantCurrent = "Constant Current";
const std::string Laser::EnumerationItem_RunMode_ConstantPower = "Constant Power";
const std::string Laser::EnumerationItem_RunMode_Modulation = "Modulation";

int Laser::NextId__ = 1;

Laser::Laser( const std::string& name, LaserDriver* driver ) :
    id_( std::to_string( (long double) NextId__++ ) ),
    name_( name ),
    laserDriver_( driver ),
    currentUnit_( "?" ),
    powerUnit_( "?" ),
    laserOnOffProperty_( NULL ),
    shutter_( NULL )
{
}

Laser::~Laser()
{
    const bool pausedPropertyIsPublic = ( shutter_ != NULL && properties_.find( shutter_->GetName() ) != properties_.end() );
    
    if ( !pausedPropertyIsPublic ) {
        delete shutter_;
    }

    for ( PropertyIterator it = GetPropertyIteratorBegin(); it != GetPropertyIteratorEnd(); it++ ) {
        delete it->second;
    }

    properties_.clear();
}

const std::string& Laser::GetId() const
{
    return id_;
}

const std::string& Laser::GetName() const
{
    return name_;
}

void Laser::SetOn( const bool on )
{
    // Reset shutter on laser on/off:
    SetShutterOpen( false );

    if ( laserOnOffProperty_ != NULL && false ) { // TODO: replace 'false' with 'autostart disabled'
        
        laserOnOffProperty_->SetValue( ( on ? EnumerationItem_On : EnumerationItem_Off ) );

    } else {
        
        if ( on ) {
            laserDriver_->SendCommand( "restart" );
        } else {
            laserDriver_->SendCommand( "abort" );
        }
    }
}

void Laser::SetShutterOpen( const bool open )
{
    if ( shutter_ == NULL ) {

        Logger::Instance()->LogError( "Laser::SetShutterOpen(): Shutter not available" );
        return;
    }

    shutter_->SetValue( open ? LaserShutterProperty::Value_Open : LaserShutterProperty::Value_Closed );
}

bool Laser::IsShutterEnabled() const
{
    if ( laserOnOffProperty_ != NULL ) {

        return ( laserOnOffProperty_->GetValue() == EnumerationItem_On ); // TODO: && not in modulation state

    } else if ( laserStateProperty_ != NULL ) {
        
        return ( laserStateProperty_->AllowsShutter() );
    }
    
    Logger::Instance()->LogError( "Laser::IsShutterEnabled(): Expected properties were not initialized" );
    return false;
}

bool Laser::IsShutterOpen() const
{
    if ( shutter_ == NULL ) {

        Logger::Instance()->LogError( "Laser::IsShutterOpen(): Shutter not available" );
        return false;
    }

    return ( shutter_->IsOpen() );
}

Property* Laser::GetProperty( const std::string& name ) const
{
    return properties_.at( name );
}

Property* Laser::GetProperty( const std::string& name )
{
    return properties_[ name ];
}

Laser::PropertyIterator Laser::GetPropertyIteratorBegin()
{
    return properties_.begin();
}

Laser::PropertyIterator Laser::GetPropertyIteratorEnd()
{
    return properties_.end();
}

void Laser::CreateNameProperty()
{
    RegisterPublicProperty( new StaticStringProperty( "Name", this->GetName() ) );
}

void Laser::CreateModelProperty()
{
    RegisterPublicProperty( new DeviceProperty( Property::String, "Model", laserDriver_, "glm?") );
}

void Laser::CreateWavelengthProperty( const std::string& wavelength)
{
    RegisterPublicProperty( new StaticStringProperty( "Wavelength", wavelength ) );
}

void Laser::CreateKeyswitchProperty()
{
    ImmutableEnumerationProperty* property = new ImmutableEnumerationProperty( "Keyswitch", laserDriver_, "gkses?" );

    property->RegisterEnumerationItem( "0", "Disabled" );
    property->RegisterEnumerationItem( "1", "Enabled" );
    
    RegisterPublicProperty( property );
}

void Laser::CreateSerialNumberProperty()
{
    RegisterPublicProperty( new DeviceProperty( Property::String, "Serial Number", laserDriver_, "gsn?") );
}

void Laser::CreateFirmwareVersionProperty()
{
    RegisterPublicProperty( new DeviceProperty( Property::String, "Firmware Version", laserDriver_, "gfv?") );
}

void Laser::CreateAdapterVersionProperty()
{
    RegisterPublicProperty( new StaticStringProperty( "Adapter Version", COBOLT_MM_DRIVER_VERSION ) );
}

void Laser::CreateOperatingHoursProperty()
{
    RegisterPublicProperty( new DeviceProperty( Property::String, "Operating Hours", laserDriver_, "hrs?") );
}

void Laser::CreateCurrentSetpointProperty()
{
    MutableDeviceProperty* property;
   
    if ( IsShutterCommandSupported() || !IsInCdrhMode() ) {
        property = new NumericProperty<double>( "Current Setpoint [" + currentUnit_ + "]", laserDriver_, "glc?", "slc", 0.0f, MaxCurrentSetpoint() );
    } else {
        property = new legacy::no_shutter_command::LaserCurrentProperty( "Current Setpoint [" + currentUnit_ + "]", laserDriver_, "glc?", "slc", 0.0f, MaxCurrentSetpoint(), this );
    }

    RegisterPublicProperty( property );
}

void Laser::CreateCurrentReadingProperty()
{
    DeviceProperty* property = new DeviceProperty( Property::Float, "Measured Current [" + currentUnit_ + "]", laserDriver_, "i?" );
    property->SetCaching( false );
    RegisterPublicProperty( property );
}

void Laser::CreatePowerSetpointProperty()
{
    MutableDeviceProperty* property = new NumericProperty<double>( "Power Setpoint [" + powerUnit_ + "]", laserDriver_, "glp?", "slp", 0.0f, MaxPowerSetpoint() );
    RegisterPublicProperty( property );
}

void Laser::CreatePowerReadingProperty()
{
    DeviceProperty* property = new DeviceProperty( Property::String, "Power Reading [" + powerUnit_ + "]", laserDriver_, "pa?" );
    property->SetCaching( false );
    RegisterPublicProperty( property );
}

void Laser::CreateLaserOnOffProperty()
{
    EnumerationProperty* property = new EnumerationProperty( "Laser Status", laserDriver_, "l?" );

    property->RegisterEnumerationItem( "0", "abort", EnumerationItem_Off );
    property->RegisterEnumerationItem( "1", "restart", EnumerationItem_On );
    
    RegisterPublicProperty( property );
    laserOnOffProperty_ = property;
}

void Laser::CreateShutterProperty()
{
    if ( IsShutterCommandSupported() ) {
        shutter_ = new LaserShutterProperty( "Emission Status", laserDriver_, this );
    } else {

        if ( IsInCdrhMode() ) {
            shutter_ = new legacy::no_shutter_command::LaserShutterPropertyCdrh( "Emission Status", laserDriver_, this );
        } else {
            shutter_ = new legacy::no_shutter_command::LaserShutterPropertyOem( "Emission Status", laserDriver_, this );
        }
    }
    
    RegisterPublicProperty( shutter_ );
}

void Laser::CreateDigitalModulationProperty()
{
    EnumerationProperty* property = new EnumerationProperty( "Digital Modulation", laserDriver_, "gdmes?" );
    property->RegisterEnumerationItem( "0", "sdmes 0", EnumerationItem_Disabled );
    property->RegisterEnumerationItem( "1", "sdmes 1", EnumerationItem_Enabled );
    RegisterPublicProperty( property );
}

void Laser::CreateAnalogModulationFlagProperty()
{
    EnumerationProperty* property = new EnumerationProperty( "Analog Modulation", laserDriver_,  "games?" );
    property->RegisterEnumerationItem( "0", "sames 0", EnumerationItem_Disabled );
    property->RegisterEnumerationItem( "1", "sames 1", EnumerationItem_Enabled );
    RegisterPublicProperty( property );
}

void Laser::CreateModulationPowerSetpointProperty()
{
    std::string maxModulationPowerSetpointResponse;
    if ( laserDriver_->SendCommand( "gmlp?", &maxModulationPowerSetpointResponse ) != return_code::ok ) {

        Logger::Instance()->LogError( "Laser::CreatePowerSetpointProperty(): Failed to retrieve max power sepoint" );
        return;
    }
    
    const double maxModulationPowerSetpoint = atof( maxModulationPowerSetpointResponse.c_str() );
    
    RegisterPublicProperty( new NumericProperty<double>( "Modulation Power Setpoint", laserDriver_, "glmp?", "slmp", 0, maxModulationPowerSetpoint ) );
}

void Laser::CreateAnalogImpedanceProperty()
{
    EnumerationProperty* property = new EnumerationProperty( "Analog Impedance", laserDriver_, "galis?" );
    
    property->RegisterEnumerationItem( "0", "salis 0", "1 kOhm" );
    property->RegisterEnumerationItem( "1", "salis 1", "50 Ohm" );

    RegisterPublicProperty( property );
}

void Laser::CreateModulationCurrentLowSetpointProperty()
{
    MutableDeviceProperty* property;
    property = new NumericProperty<double>( "Modulation Low Current Setpoint [" + currentUnit_ + "]", laserDriver_, "glth?", "slth", 0.0f, MaxCurrentSetpoint() );
    RegisterPublicProperty( property );
}

void Laser::CreateModulationCurrentHighSetpointProperty()
{
    MutableDeviceProperty* property;
    property = new NumericProperty<double>( "Modulation Low Current Setpoint [" + currentUnit_ + "]", laserDriver_, "gmc?", "smc", 0.0f, MaxCurrentSetpoint() );
    RegisterPublicProperty( property );
}

void Laser::CreateModulationHighPowerSetpointProperty()
{
    MutableDeviceProperty* property = new NumericProperty<double>( "Modulation Power Setpoint [" + powerUnit_ + "]", laserDriver_, "glmp?", "slmp", 0.0f, MaxPowerSetpoint() );
    RegisterPublicProperty( property );
}

bool Laser::IsShutterCommandSupported() const // TODO: Split into IsShutterCommandSupported() and IsPauseCommandSupported()
{
    std::string response;
    laserDriver_->SendCommand( "l0r", &response );
    
    return ( response.find( "OK" ) != std::string::npos );
}

bool Laser::IsInCdrhMode() const
{
    std::string response;
    laserDriver_->SendCommand( "gas?", &response );

    return ( response == "1" );
}

void Laser::RegisterPublicProperty( Property* property )
{
    assert( property != NULL );
    properties_[ property->GetName() ] = property;
}

double Laser::MaxCurrentSetpoint()
{
    std::string maxCurrentSetpointResponse;
    if ( laserDriver_->SendCommand( "gmlc?", &maxCurrentSetpointResponse ) != return_code::ok ) {

        Logger::Instance()->LogError( "Laser::MaxCurrentSetpoint(): Failed to retrieve max current sepoint" );
        return 0.0f;
    }
    
    return atof( maxCurrentSetpointResponse.c_str() );
}

double Laser::MaxPowerSetpoint()
{
    std::string maxPowerSetpointResponse;
    if ( laserDriver_->SendCommand( "gmlp?", &maxPowerSetpointResponse ) != return_code::ok ) {

        Logger::Instance()->LogError( "Laser::MaxPowerSetpoint(): Failed to retrieve max power sepoint" );
        return 0.0f;
    }

    return atof( maxPowerSetpointResponse.c_str() );
}
