# Place uv-installed tools and manually built SWIG 3.0 in path
export PATH="$HOME/.local/bin:/opt/local/bin:/usr/local/bin:$PATH"
uv tool install -U cjdk

[ -d 3rdpartypublic ] || ln -s ~/3rdpartypublic ./
cd 3rdpartypublic
svn cleanup --remove-unversioned --remove-ignored
svn update -q -r$(cat $WORKSPACE/micro-manager/3rdpartypublic-revision)
cd $WORKSPACE

[ -d 3rdparty ] || ln -s ~/3rdparty ./
cd 3rdparty
svn cleanup --remove-unversioned --remove-ignored
svn --username $SVN_USERNAME --password $SVN_PASSWORD \
    update -q -r$(cat $WORKSPACE/micro-manager/3rdparty-revision)
cd $WORKSPACE

cd micro-manager/mmCoreAndDevices
mkdir -p ~/.ssh
cp $KNOWN_HOSTS_GITHUB ~/.ssh/known_hosts
cp $MM_SECRETDEVICEADAPTERS_SSHKEY ~/.ssh/id_ed25519
./secret-device-adapters-checkout.sh use_ssh
rm -rf ~/.ssh
cd $WORKSPACE

cd micro-manager
git rev-parse --short HEAD > version.txt

deps_sha=$(cat buildscripts/nightly/nightlybuild_macOS_*.sh | shasum | cut -f1 -d' ')
deps_tar=$WORKSPACE/dependencies-$deps_sha.tar
if [ -f $deps_tar ]; then
    echo 'Extracting dependencies from $deps_tar...'
    tar xf $deps_tar
else
    rm -f $WORKSPACE/dependencies-*.tar
    buildscripts/nightly/nightlybuild_macOS_deps.sh -d
    echo 'Archiving dependencies to $deps_tar...'
    tar cf $deps_tar dependencies/
fi

security unlock-keychain -p "$KEYCHAIN_PASSWORD" build.keychain

MAKEFLAGS=-j$(sysctl -n hw.ncpu) buildscripts/nightly/nightlybuild_macOS_mm.sh -sn

security lock-keychain build.keychain