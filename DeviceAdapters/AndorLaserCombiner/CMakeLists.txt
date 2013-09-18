project( ANDOR_LASER_COMBINER )
cmake_minimum_required( VERSION 2.6 )
set(CMAKE_SUPPRESS_REGENERATION TRUE) # get rid of ZERO_CHECK project

# Find SDK headers
set( SDK_INCLUDE_DIRS
  "../../../3rdparty/Andor/ALC/ALC_REV_SDK/Include" )
find_path( SDK_INC
  NAMES ALC_REV.h LaserState.h
  PATHS ${SDK_INCLUDE_DIRS} )
if ( ${SDK_INC} MATCHES "SDK_INC-NOTFOUND" )
  message( FATAL_ERROR "Could not find ALC SDK headers in ${SDK_INC}" )
endif ()
message( STATUS "Found ALC SDK headers " ${SDK_INC} )

# Common libraries
set( COMMON_INC
  "../../../3rdpartypublic/boost" )

include_directories( ${SDK_INC} ${COMMON_INC} )
add_definitions( -D_USRDLL -DMODULE_EXPORTS )
set( ALC_SOURCES
  AndorLaserCombiner.cpp AndorLaserCombiner.h
  ${SDK_INC}/ALC_REV.h ${SDK_INC}/LaserState.h 
  ../../MMDevice/DeviceUtils.cpp ../../MMDevice/ModuleInterface.cpp ../../MMDevice/Property.cpp )
add_library( mmgr_dal_AndorLaserCombiner SHARED ${ALC_SOURCES} )
