cd \projects\micromanager1.3
pushd ..\3rdparty
svn cleanup --non-interactive
svn update --non-interactive
popd
svn cleanup --non-interactive
svn update --non-interactive
pushd SecretDeviceAdapters
svn cleanup --non-interactive
svn update --non-interactive
popd
exit/b
