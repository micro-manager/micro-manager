@setlocal
@if "%ECHO_MODE%"=="" set ECHO_MODE=off
@echo %ECHO_MODE%

cd /d %~dp0..\..

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

call ant -f buildscripts\nightly\nightlybuild_Windows.xml -listener org.apache.tools.ant.XmlLogger -logger org.apache.tools.ant.listener.SimpleBigProjectLogger -Dmm.nightly.target=%TARGET% %ANT_SSH_ARGS%
@echo %ECHO_MODE%

goto :EOF


:UPDATE_SVN_SOURCE
if "%USE_SVN%" == "yes" (
    svn cleanup --non-interactive
    svn revert --non-interactive --depth=infinity .
    svn update --non-interactive --accept theirs-full --force --ignore-externals
)
rem End of subroutine UPDATE_SVN_SOURCE
goto :EOF

