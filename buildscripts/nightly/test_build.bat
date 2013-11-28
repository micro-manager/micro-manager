@echo off
setlocal enableextensions enabledelayedexpansion

rem This batch file is not actually used for the nightly build, but performs
rem similar steps and is handy for testing the build with similar settings.

rem It also demonstrates how to set up an automated build by calling Ant
rem directly.

rem Note, however, that this batch file will not clean the source tree before
rem building.


set batch_file_dir=%~dp0
set src_root=%batch_file_dir%\..\..
set log_filename=%src_root%\buildlog.xml
set report_filename=%src_root%\buildreport.html

if exist %log_filename% (
  del %log_filename%
)

rem Prevent 'only one logger' error.
set ANT_ARGS=

call ant -f %src_root%\buildscripts\nightly\nightlybuild_Windows.xml ^
  -listener org.apache.tools.ant.XmlLogger ^
  -logger org.apache.tools.ant.listener.SimpleBigProjectLogger ^
  -DXmlLogger.file=%log_filename% ^
  -Dmm.versionstring=test_build

if not exist %log_filename% (
  echo Ant failed without producing XML log file
  exit /b 1
)

python -c "import sys" -c "if sys.version_info.major != 3 or sys.version_info.minor < 3: sys.exit(1)"
if errorlevel 1 (
  echo Python 3.3 or later required to generate build error report
  exit /b
)

python %src_root%\buildscripts\nightly\genreport_Windows.py %src_root% %log_filename% %report_filename%
start %report_filename%
