
set cleantarget=
IF "%1%"=="FULL" SET cleantarget=clean

PUSHD %~dp0\..

PUSHD acqEngine
call build.bat
POPD

PUSHD autofocus
call ant -buildfile build.xml %cleantarget% compile build
POPD

echo building pixelcalibrator
pushd plugins\PixelCalibrator 
call ant -buildfile build.xml %cleantarget% compile build
popd

pushd plugins\Projector
call ant -buildfile build.xml %cleantarget% compile build
popd

pushd plugins\Recall
call ant -buildfile build.xml %cleantarget% compile build
popd

pushd plugins\SlideExplorer
call ant -buildfile build.xml %cleantarget% compile build
popd

pushd plugins\StageControl
call ant -buildfile build.xml %cleantarget% compile build
popd

pushd plugins\Big
call ant -buildfile build.xml %cleantarget% compile build 
popd

pushd plugins\MultiCamera
call ant -buildfile build.xml %cleantarget% compile build 
popd

pushd plugins\SplitView
call ant -buildfile build.xml %cleantarget% compile build 
popd

pushd plugins\ClojureEditor
call ant -buildfile build.xml %cleantarget% compile build 
popd

pushd plugins\CRISP
call build
popd

rem pushd plugins\Bleach
rem call ant -buildfile build.xml %cleantarget% compile build
rem popd

rem pushd plugins\Tracker 
rem call ant -buildfile build.xml %cleantarget% compile build 
rem popd

pushd plugins\DataBrowser
call build.bat
popd

pushd plugins\Gaussian
call build.bat
popd

pushd plugins\ImageFlipper
call build.bat
popd


POPD
