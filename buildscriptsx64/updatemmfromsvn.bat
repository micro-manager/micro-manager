cd \projects\micromanager
pushd ..\3rdparty
rem skip the 3rdparty since it is time consuming
rem svn update
popd
svn update --non-interactive
pushd SecretDeviceAdapters
svn update --non-interactive
popd
exit/b
