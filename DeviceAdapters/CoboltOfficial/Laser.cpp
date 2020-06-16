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
//                specified in owner’s manual may result in exposure to hazardous radiation and
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

Laser* Laser::Create( LaserDriver* driver )
{
    assert( driver != NULL );
    
    std::string modelString;
    if ( driver->SendCommand( "glm?", &modelString ) != return_code::ok ) {
        return NULL;
    }
    
    std::vector<std::string> modelTokens;
    DecomposeModelString( modelString, modelTokens );
    std::string wavelength = "Unknown";
    
    if ( modelTokens.size() > 0 ) {
        wavelength = std::to_string( (_Longlong) atoi( modelTokens[ 0 ].c_str() ) );
    }

    Laser* laser;

    if ( modelString.find( "-06-91-" ) != std::string::npos ) {

        laser = new Laser( "06-DPL", wavelength, driver );

        laser->currentUnit_ = Milliamperes; 
        laser->powerUnit_ = Milliwatts;

        laser->CreateNameProperty();
        laser->CreateModelProperty();
        laser->CreateFirmwareVersionProperty();
        laser->CreateWavelengthProperty();
        laser->CreateKeyswitchProperty();
        laser->CreateLaserStateProperty<ST_06_DPL>();
        //laser->CreateLaserOnOffProperty();
        laser->CreateShutterProperty();
        laser->CreateRunModeProperty<ST_06_DPL>();
        laser->CreatePowerSetpointProperty();
        laser->CreatePowerReadingProperty();
        laser->CreateCurrentSetpointProperty();
        laser->CreateCurrentReadingProperty();
        laser->CreateDigitalModulationProperty();
        laser->CreateAnalogModulationFlagProperty();
        laser->CreateOperatingHoursProperty();
        laser->CreateSerialNumberProperty();
        laser->CreateFirmwareVersionProperty();
        laser->CreateDriverVersionProperty();

    } else if ( modelString.find( "-06-01-" ) != std::string::npos ||
                modelString.find( "-06-03-" ) != std::string::npos ) {

        laser = new Laser( "06-MLD", wavelength, driver );

        laser->currentUnit_ = Milliamperes;
        laser->powerUnit_ = Milliwatts;

        laser->CreateNameProperty();
        laser->CreateModelProperty();
        laser->CreateFirmwareVersionProperty();
        laser->CreateWavelengthProperty();
        laser->CreateKeyswitchProperty();
        laser->CreateLaserStateProperty<ST_06_MLD>();
        //laser->CreateLaserOnOffProperty();
        laser->CreateShutterProperty();
        laser->CreateRunModeProperty<ST_06_MLD>();
        laser->CreatePowerSetpointProperty();
        laser->CreatePowerReadingProperty();
        laser->CreateCurrentSetpointProperty();
        laser->CreateCurrentReadingProperty();
        laser->CreateDigitalModulationProperty();
        laser->CreateAnalogModulationFlagProperty();
        laser->CreateAnalogImpedanceProperty();
        laser->CreateModulationPowerSetpointProperty();
        laser->CreateOperatingHoursProperty();
        laser->CreateSerialNumberProperty();
        laser->CreateFirmwareVersionProperty();
        laser->CreateDriverVersionProperty();
        
    } else if ( modelString.find( "-05-" ) != std::string::npos ) {

        laser = new Laser( "Compact 05", wavelength, driver );

        laser->currentUnit_ = Amperes;
        laser->powerUnit_ = Milliwatts;
        
        laser->CreateNameProperty();
        laser->CreateModelProperty();
        laser->CreateFirmwareVersionProperty();
        laser->CreateWavelengthProperty();
        laser->CreateKeyswitchProperty();
        laser->CreateLaserStateProperty<ST_05_Series>();
        //laser->CreateLaserOnOffProperty();
        laser->CreateShutterProperty();
        laser->CreateRunModeProperty<ST_05_Series>();
        laser->CreatePowerSetpointProperty();
        laser->CreatePowerReadingProperty();
        laser->CreateCurrentSetpointProperty();
        laser->CreateCurrentReadingProperty();
        laser->CreateOperatingHoursProperty();
        laser->CreateSerialNumberProperty();
        laser->CreateFirmwareVersionProperty();
        laser->CreateDriverVersionProperty();

    } else {

        laser = new Laser( "Unknown", wavelength, driver );
    }
    
    Logger::Instance()->LogMessage( "Created laser '" + laser->GetName() + "'", true );

    laser->SetShutterOpen( false );

    Property::ResetIdGenerator();

    return laser;
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

const std::string& Laser::GetWavelength() const
{
    return wavelength_;
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
    return ( shutter_->GetValue() == LaserShutterProperty::Value_Open );
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

void Laser::DecomposeModelString( std::string modelString, std::vector<std::string>& modelTokens )
{
    std::string token;

    for ( std::string::iterator character = modelString.begin(); character != modelString.end(); character++ ) {

        if ( *character == '-' || *character == '\r' ) {

            if ( token.length() > 0 ) {
                modelTokens.push_back( token );
                token.clear();
            }

        } else {

            token.push_back( *character );
        }
    }
    
    if ( token.length() > 0 ) {
        modelTokens.push_back( token );
    }
}

Laser::Laser( const std::string& name, const std::string& wavelength, LaserDriver* driver ) :
    id_( std::to_string( (long double) NextId__++ ) ),
    name_( name ),
    wavelength_( wavelength ),
    laserDriver_( driver ),
    currentUnit_( "?" ),
    powerUnit_( "?" ),
    laserOnOffProperty_( NULL ),
    shutter_( NULL )
{
}

void Laser::CreateNameProperty()
{
    RegisterPublicProperty( new StaticStringProperty( "Name", this->GetName() ) );
}

void Laser::CreateModelProperty()
{
    RegisterPublicProperty( new DeviceProperty( Property::String, "Model", laserDriver_, "glm?") );
}

void Laser::CreateWavelengthProperty()
{
    RegisterPublicProperty( new StaticStringProperty( "Wavelength", this->GetWavelength()) );
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

void Laser::CreateDriverVersionProperty()
{
    RegisterPublicProperty( new StaticStringProperty( "Driver Version", COBOLT_MM_DRIVER_VERSION ) );
}

void Laser::CreateOperatingHoursProperty()
{
    RegisterPublicProperty( new DeviceProperty( Property::String, "Operating Hours", laserDriver_, "hrs?") );
}

void Laser::CreateCurrentSetpointProperty()
{
    std::string maxCurrentSetpointResponse;
    if ( laserDriver_->SendCommand( "gmlc?", &maxCurrentSetpointResponse ) != return_code::ok ) {

        Logger::Instance()->LogError( "Laser::CreateCurrentSetpointProperty(): Failed to retrieve max current sepoint" );
        return;
    }

    const double maxCurrentSetpoint = atof( maxCurrentSetpointResponse.c_str() );

    MutableDeviceProperty* property;
   
    if ( IsShutterCommandSupported() || !IsInCdrhMode() ) {
        property = new NumericProperty<double>( "Current Setpoint [" + currentUnit_ + "]", laserDriver_, "glc?", "slc", 0.0f, maxCurrentSetpoint );
    } else {
        property = new legacy::no_shutter_command::LaserCurrentProperty( "Current Setpoint [" + currentUnit_ + "]", laserDriver_, "glc?", "slc", 0.0f, maxCurrentSetpoint, this );
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
    std::string maxPowerSetpointResponse;
    if ( laserDriver_->SendCommand( "gmlp?", &maxPowerSetpointResponse ) != return_code::ok ) {

        Logger::Instance()->LogError( "Laser::CreatePowerSetpointProperty(): Failed to retrieve max power sepoint" );
        return;
    }

    const double maxPowerSetpoint = atof( maxPowerSetpointResponse.c_str() );
    
    MutableDeviceProperty* property = new NumericProperty<double>( "Power Setpoint [" + powerUnit_ + "]", laserDriver_, "glp?", "slp", 0.0f, maxPowerSetpoint );
    RegisterPublicProperty( property );
}

void Laser::CreatePowerReadingProperty()
{
    DeviceProperty* property = new DeviceProperty( Property::String, "Power Reading [" + powerUnit_ + "]", laserDriver_, "pa?" );
    property->SetCaching( false );
    RegisterPublicProperty( property );
}

template<> void Laser::CreateLaserStateProperty<Laser::ST_05_Series>()
{
    Logger::Instance()->LogError( "05 Series support not fully implemented" );
    assert( false ); // TODO: Implement
}

template<> void Laser::CreateLaserStateProperty<Laser::ST_06_DPL>()
{
    if ( IsInCdrhMode() ) {

        laserStateProperty_ = new LaserStateProperty( Property::String, "Laser State", laserDriver_, "gom?" );

        laserStateProperty_->RegisterState( "0", "Off", false );
        laserStateProperty_->RegisterState( "1", "Waiting for TEC", false );
        laserStateProperty_->RegisterState( "2", "Waiting for Key", false );
        laserStateProperty_->RegisterState( "3", "Warming Up", false );
        laserStateProperty_->RegisterState( "4", "Completed", true );
        laserStateProperty_->RegisterState( "5", "Fault", false );
        laserStateProperty_->RegisterState( "6", "Aborted", false );
        laserStateProperty_->RegisterState( "7", "Modulation", false );

    } else {

        laserStateProperty_ = new LaserStateProperty( Property::String, "Laser State", laserDriver_, "l?" );

        laserStateProperty_->RegisterState( "0", "Off", true );
        laserStateProperty_->RegisterState( "1", "On", true );
    }

    RegisterPublicProperty( laserStateProperty_ );
}

template<> void Laser::CreateLaserStateProperty<Laser::ST_06_MLD>()
{
    if ( IsInCdrhMode() ) {

        laserStateProperty_ = new LaserStateProperty( Property::String, "Laser State", laserDriver_, "gom?" );
    
        laserStateProperty_->RegisterState( "0", "Off", false );
        laserStateProperty_->RegisterState( "1", "Waiting for Key", false );
        laserStateProperty_->RegisterState( "2", "Completed", true );
        laserStateProperty_->RegisterState( "3", "Completed (On/Off Modulation)", false );
        laserStateProperty_->RegisterState( "4", "Completed (Modulation)", false );
        laserStateProperty_->RegisterState( "5", "Fault", false );
        laserStateProperty_->RegisterState( "6", "Aborted", false );

    } else {

        laserStateProperty_ = new LaserStateProperty( Property::String, "Laser State", laserDriver_, "l?" );

        laserStateProperty_->RegisterState( "0", "Off", true );
        laserStateProperty_->RegisterState( "1", "On", true );
    }

    RegisterPublicProperty( laserStateProperty_ );
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

template <> void Laser::CreateRunModeProperty<Laser::ST_05_Series>()
{
    EnumerationProperty* property;
    
    if ( IsShutterCommandSupported() || !IsInCdrhMode() ) {
        property = new EnumerationProperty( "Run Mode", laserDriver_, "gam?" );
    } else {
        property = new legacy::no_shutter_command::LaserRunModeProperty( "Run Mode", laserDriver_, "gam?", this );
    }

    property->SetCaching( false );

    property->RegisterEnumerationItem( "0", "ecc", EnumerationItem_RunMode_ConstantCurrent );
    property->RegisterEnumerationItem( "1", "ecp", EnumerationItem_RunMode_ConstantPower );

    RegisterPublicProperty( property );
}

template <> void Laser::CreateRunModeProperty<Laser::ST_06_DPL>()
{
    EnumerationProperty* property;
    
    if ( IsShutterCommandSupported() || !IsInCdrhMode() ) {
        property = new EnumerationProperty( "Run Mode", laserDriver_, "gam?" );
    } else {
        property = new legacy::no_shutter_command::LaserRunModeProperty( "Run Mode", laserDriver_, "gam?", this );
    }
    
    property->SetCaching( false );

    property->RegisterEnumerationItem( "0", "ecc", EnumerationItem_RunMode_ConstantCurrent );
    property->RegisterEnumerationItem( "1", "ecp", EnumerationItem_RunMode_ConstantPower );
    property->RegisterEnumerationItem( "2", "em", EnumerationItem_RunMode_Modulation );
    
    RegisterPublicProperty( property );
}

template <> void Laser::CreateRunModeProperty<Laser::ST_06_MLD>()
{
    EnumerationProperty* property;

    if ( IsShutterCommandSupported() || !IsInCdrhMode() ) {
        property = new EnumerationProperty( "Run Mode", laserDriver_, "gam?" );
    } else {
        property = new legacy::no_shutter_command::LaserRunModeProperty( "Run Mode", laserDriver_, "gam?", this );
    }
    
    property->SetCaching( false );

    property->RegisterEnumerationItem( "0", "ecc", EnumerationItem_RunMode_ConstantCurrent );
    property->RegisterEnumerationItem( "1", "ecp", EnumerationItem_RunMode_ConstantPower );
    property->RegisterEnumerationItem( "2", "em", EnumerationItem_RunMode_Modulation );

    RegisterPublicProperty( property );
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

bool Laser::IsShutterCommandSupported() const
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
