@rem We reset the echo mode after any "call" below to keep it consistent.
@set ECHO_MODE=on
@echo %ECHO_MODE%

cd /d %~dp0\..\..

set ANT_SSH_ARGS=-Dmm.nightly.upload.user=%1 -Dmm.nightly.upload.ssh_key=%2

rem (Set USE_SVN to no for testing on a git-svn copy)
set USE_SVN=yes

rem (Set TARGET to package for testing)
set TARGET=upload

shift
:CHECK_OPTIONAL_ARGS
shift
if "%1" == "nosvn" (
    set USE_SVN=no
    goto :CHECK_OPTIONAL_ARGS
)
if "%1" == "noupload" (
    set TARGET=package
    goto :CHECK_OPTIONAL_ARGS
)
if "%1" == "" (
    goto :DONE_CHECKING_ARGS
)
@echo Unknown keyword: %1
exit /B 1
:DONE_CHECKING_ARGS

@echo Using SVN: %USE_SVN%; target: %TARGET%

pushd ..\3rdparty
call :UPDATE_SVN_SOURCE
popd

pushd ..\3rdpartypublic
call :UPDATE_SVN_SOURCE
popd

call :UPDATE_SVN_SOURCE

pushd SecretDeviceAdapters
call :UPDATE_SVN_SOURCE
popd

call ant -f buildscripts\nightly\nightlybuild.xml unstage-all clean-all
if errorlevel 1 (
    @echo Failed to unstage and clean
    exit /B 1
)
@echo %ECHO_MODE%

rem (Build of Clojure projects may fail if the JVM at JAVA_HOME does not match
rem mm.architecture, due to failure to load MMCoreJ_wrap.dll. We prevent this
rem (for now) by building the MMCoreJ_wrap.dll matching the build JVM first.)

set ARCH=x64
call :BUILD_ARCH

set ARCH=Win32
call :BUILD_ARCH

goto :EOF


:UPDATE_SVN_SOURCE
if "%USE_SVN%" == "yes" (
    svn cleanup --non-interactive
    svn revert --non-interactive --depth=infinity .
    svn update --non-interactive --accept theirs-full --force --ignore-externals
)
goto :EOF


:BUILD_ARCH
call ant -f buildscripts\nightly\nightlybuild.xml -Dmm.platform=Windows -Dmm.architecture=%ARCH% %ANT_SSH_ARGS% %TARGET%
if errorlevel 1 (
    @echo Failed to build %ARCH%
    exit /B 1
)
@echo %ECHO_MODE%
goto :EOF
