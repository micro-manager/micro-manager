///////////////////////////////////////////////////////////////////////////////
// FILE:       CoboltOfficial.cpp
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

#include "CoboltOfficial.h"

using namespace std;
using namespace cobolt;

const char * g_DeviceName = "Cobolt Laser";
const char * g_DeviceDescription = "Official device adapter for Cobolt lasers.";
const char * g_DeviceVendorName = "Cobolt - a HÜBNER Group company";

const char* const g_Property_Port_None = "None";

/// ###
/// DLL API Exports

MODULE_API void InitializeModuleData()
{
    RegisterDevice( g_DeviceName, MM::ShutterDevice, g_DeviceDescription );
}

MODULE_API MM::Device* CreateDevice( const char* deviceName )
{
    if ( deviceName == 0 ) {
        return 0;
    } else if ( strcmp( deviceName, g_DeviceName ) == 0 ) {
        return new CoboltOfficial;
    } else {
        return 0;
    }
}

MODULE_API void DeleteDevice( MM::Device* pDevice )
{
    delete pDevice;
}

/// ### 
/// Supporting Classes

class GuiPropertyAdapter : public cobolt::GuiProperty
{
public:

    GuiPropertyAdapter( MM::PropertyBase* mm_property ) : mm_property_( mm_property ) {}
    virtual bool Set( const std::string& value ) { return mm_property_->Set( value.c_str() ); }
    virtual bool Get( std::string& value ) const { return mm_property_->Get( value ); }
    
private:

    MM::PropertyBase* mm_property_;
};

/// ###
/// CoboltOfficial Implementation

CoboltOfficial::CoboltOfficial() :
    laser_( NULL ),
    isInitialized_( false ),
    isBusy_( false ),
    port_( "None" )
{
    cobolt::Logger::Instance()->SetupWithGateway( this ); // TODO: Must be one instance per device.
    
    assert( strlen( g_DeviceName ) < (unsigned int) MM::MaxStrLength );

    InitializeDefaultErrorMessages();

    // Make sure cobolt::return_code items that should map to global return codes do so correctly:
    assert( cobolt::return_code::ok == DEVICE_OK );
    assert( cobolt::return_code::error == DEVICE_ERR );
    assert( cobolt::return_code::unsupported_command == DEVICE_UNSUPPORTED_COMMAND );
    
    // Map cobolt specific error codes to readable strings:
    SetErrorText( cobolt::return_code::illegal_port_change,                     "Port change not allowed."       );
    SetErrorText( cobolt::return_code::laser_startup_incomplete,                "Laser not ready (check keyswitch)." );
    SetErrorText( cobolt::return_code::invalid_value,                           "Invalid value"                  );
    SetErrorText( cobolt::return_code::serial_port_undefined,                   "No valid serial port selected." );
    SetErrorText( cobolt::return_code::property_not_settable_in_current_state,  "Change of this property not allowed in current state." );
    SetErrorText( cobolt::return_code::unsupported_device_property_value,       "Unsupported device response." );
    
    // Create non-laser properties:
    CreateProperty( MM::g_Keyword_Name,         g_DeviceName,               MM::String, true );
    CreateProperty( "Vendor",                   g_DeviceVendorName,         MM::String, true );
    CreateProperty( MM::g_Keyword_Description,  g_DeviceDescription,        MM::String, true );
    CreateProperty( MM::g_Keyword_Port,         g_Property_Port_None,       MM::String, false, new CPropertyAction( this, &CoboltOfficial::OnPropertyAction_Port ), true );
    
    UpdateStatus();
}

CoboltOfficial::~CoboltOfficial()
{
    Shutdown();

    if ( laser_ != NULL ) {
        delete laser_;
        laser_ = NULL;
    }
}

int CoboltOfficial::Initialize()
{
    if ( isInitialized_ ) {
        return cobolt::return_code::ok;
    }

    if ( port_ == g_Property_Port_None ) {

        Logger::Instance()->LogError( "CoboltOfficial::Initialize(): Serial port not selected" );
        return cobolt::return_code::serial_port_undefined;
    }

    // Make sure 'device mode' is selected:
    SendCommand( "1" );

    laser_ = Laser::Create( this );

    if ( laser_ == NULL ) {
        return cobolt::return_code::error;
    }

    for ( Laser::PropertyIterator it = laser_->GetPropertyIteratorBegin(); it != laser_->GetPropertyIteratorEnd(); it++ ) {

        ExposeToGui( it->second );
        it->second->IntroduceToGuiEnvironment( this );
    }

    isInitialized_ = true;

    cobolt::Logger::Instance()->LogMessage( "CoboltOfficial::Initialize(): Initialization successful", true );

    return cobolt::return_code::ok;
}

int CoboltOfficial::Shutdown()
{
    if ( isInitialized_ == true ) {
        isInitialized_ = false;
    }

    return cobolt::return_code::ok;
}

bool CoboltOfficial::Busy()
{
    return isBusy_;
}

void CoboltOfficial::GetName( char* name ) const
{
    CDeviceUtils::CopyLimitedString( name, g_DeviceName );
}

int CoboltOfficial::SetOpen( bool open )
{
    if ( !laser_->IsShutterEnabled() ) {
        return cobolt::return_code::laser_startup_incomplete;
    }
    
    laser_->SetShutterOpen( open );
    
    return cobolt::return_code::ok;
}

/**
 * Tells whether the shutter is open or not (i.e. whether laser is shining or not).
 */
int CoboltOfficial::GetOpen( bool& open )
{
    open = ( laser_->IsShutterEnabled() && laser_->IsShutterOpen() );

    return cobolt::return_code::ok;
}

/**
 * Opens the shutter for the given duration, then closes it again.
 * Currently not implemented in any shutter adapters
 */
int CoboltOfficial::Fire( double deltaT )
{
    int reply = SetOpen( true );

    if ( reply == cobolt::return_code::ok ) {

        CDeviceUtils::SleepMs( (long) ( deltaT + 0.5f ) );
        reply = SetOpen( false );
    }

    return reply;
}

/**
 * \brief Adds some Cobolt laser serial communication handling on top of the Micro-manager
 *        serial communication class' handling.
 *
 * Sends the command, fetches the laser response and detects unsupported laser commands.
 */
int CoboltOfficial::SendCommand( const std::string& command, std::string* response )
{
    Logger::Instance()->LogMessage( "CoboltOfficial::SendCommand: About to send command '" + command + "', response expected=" + ( response != NULL ? "yes" : "no" ), true );

    int returnCode = SendSerialCommand( port_.c_str(), command.c_str(), "\r" );
    
    if ( returnCode == cobolt::return_code::ok && response != NULL ) {

        returnCode = GetSerialAnswer( port_.c_str(), "\r\n", *response );
        
        if ( returnCode != cobolt::return_code::ok ) {

            Logger::Instance()->LogMessage( "CoboltOfficial::SendCommand: GetSerialAnswer Failed: " + std::to_string( (_Longlong) returnCode ), true );

        } else if ( response->find( "error" ) != std::string::npos ||
                    response->find( "Error" ) != std::string::npos ||
                    response->find( "ERROR" ) != std::string::npos ) {

            Logger::Instance()->LogMessage( "CoboltOfficial::SendCommand: Sent: " + command + " Reply received: " + *response, true );
            returnCode = cobolt::return_code::unsupported_command;
        }
    } else {

        // Flush the response (failing to do so will result in this response being provided as the response of the next command):
        std::string ignoredResponse;
        GetSerialAnswer( port_.c_str(), "\r\n", ignoredResponse );
        
        if ( returnCode != cobolt::return_code::ok ) {
            Logger::Instance()->LogMessage( "CoboltOfficial::SendCommand: SendSerialCommand Failed: " + std::to_string( (_Longlong) returnCode ), true );
        }
    }
    return returnCode;
}

void CoboltOfficial::SendLogMessage( const char* message, bool debug ) const
{
    LogMessage( message, debug );
}

int CoboltOfficial::RegisterAllowedGuiPropertyValue( const std::string& propertyName, const std::string& value )
{
    return AddAllowedValue( propertyName.c_str(), value.c_str() );
}

int CoboltOfficial::RegisterAllowedGuiPropertyRange( const std::string& propertyName, double min, double max )
{
    return SetPropertyLimits( propertyName.c_str(), min, max );
}

int CoboltOfficial::OnPropertyAction_Port( MM::PropertyBase* mm_property, MM::ActionType action )
{
    if ( action == MM::BeforeGet ) {

        mm_property->Set( port_.c_str() );

    } else if ( action == MM::AfterSet ) {

        if ( isInitialized_ ) {
            
            // Port change after initialization not allowed, thus reset port value:
            mm_property->Set( port_.c_str() );
            
            return cobolt::return_code::illegal_port_change;
        }

        mm_property->Get( port_ );
    }

    return cobolt::return_code::ok;
}

int CoboltOfficial::OnPropertyAction_Laser( MM::PropertyBase* mm_property, MM::ActionType action )
{
    GuiPropertyAdapter guiProperty( mm_property );

    int returnCode = return_code::ok;
    Property* property = laser_->GetProperty( mm_property->GetName() );
    
    if ( action == MM::BeforeGet ) {

        returnCode = property->OnGuiGetAction( guiProperty );

    } else if ( action == MM::AfterSet ) {
    
        returnCode = property->OnGuiSetAction( guiProperty );
    }
    
    return returnCode;
}

MM::PropertyType CoboltOfficial::ResolvePropertyType( const cobolt::Property::Stereotype stereotype ) const
{
    switch ( stereotype ) {

        case Property::Float:   return MM::Float;
        case Property::Integer: return MM::Integer;
        case Property::String:  return MM::String;
    }

    return MM::Undef;
}

int CoboltOfficial::ExposeToGui( const Property* property )
{
    const std::string initialValue = property->GetValue();

    CPropertyAction* action = new CPropertyAction( this, &CoboltOfficial::OnPropertyAction_Laser );
    const int returnCode = CreateProperty(
        property->GetName().c_str(),
        initialValue.c_str(),
        ResolvePropertyType( property->GetStereotype() ),
        !property->IsMutable(),
        action );
    
    if ( returnCode != return_code::ok ) {
        Logger::Instance()->LogMessage( "CoboltOfficial::ExposeToGui( '" + property->GetName() + "' ): Failed to expose property { " + property->ObjectString() + " } to GUI.", true );
    } else {
        Logger::Instance()->LogMessage( "CoboltOfficial::ExposeToGui( '" + property->GetName() + "' ): Exposed property { " + property->ObjectString() + " } to GUI with initial value = '" + initialValue + "'.", true );
    }

    return returnCode;
}
