///////////////////////////////////////////////////////////////////////////////
// FILE:       LaserFactory.cpp
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
#include "LaserFactory.h"
#include "Logger.h"

#include "LaserDriver.h"

#include "Dpl06Laser.h"
#include "Mld06Laser.h"
#include "SkyraLaser.h"

//#include "StaticStringProperty.h"
//#include "DeviceProperty.h"
//#include "ImmutableEnumerationProperty.h"
//#include "LaserStateProperty.h"
//#include "MutableDeviceProperty.h"
//#include "EnumerationProperty.h"
//#include "NumericProperty.h"
//#include "LaserShutterProperty.h"
//#include "NoShutterCommandLegacyFix.h"

using namespace std;
using namespace cobolt;

Laser* LaserFactory::Create( LaserDriver* driver )
{
    assert( driver != NULL );
    
    std::string firmwareVersion;
    if ( driver->SendCommand( "gfv?", &firmwareVersion ) != return_code::ok ) {
        return NULL;
    }

    std::string modelString;
    if ( driver->SendCommand( "glm?", &modelString ) != return_code::ok ) {
        return NULL;
    }
    
    std::vector<std::string> modelTokens;
    DecomposeModelString( modelString, modelTokens );
    std::string wavelength = "Unknown";
    
    if ( modelTokens.size() > 0 ) {
        wavelength = std::to_string( (_Longlong) atoi( modelTokens[ 0 ].c_str() ) ); // TODO: Verify this, modelTokens[ 0 ] seems to use wrong index for wavelength...
    }

    Laser* laser;

    if ( modelString.find( "-06-91-" ) != std::string::npos ) {

        laser = new Dpl06Laser( wavelength, driver );

    } else if ( modelString.find( "-06-01-" ) != std::string::npos ||
                modelString.find( "-06-03-" ) != std::string::npos ) {

        laser = new Mld06Laser( "06-MLD", driver );

    } else if ( firmwareVersion.find( "9.001" ) != std::string::npos ) {

        laser = new SkyraLaser( driver );

    } else {

        laser = new Laser( "Unknown", driver );
    }
    
    Logger::Instance()->LogMessage( "Created laser '" + laser->GetName() + "'", true );

    laser->SetShutterOpen( false );

    Property::ResetIdGenerator();

    return laser;
}

void LaserFactory::DecomposeModelString( std::string modelString, std::vector<std::string>& modelTokens )
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
