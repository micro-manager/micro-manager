
pushd \projects\micromanager\buildscripts
echo working directory is 
cd
svn cleanup --non-interactive
svn update --accept postpone --force --non-interactive
popd

