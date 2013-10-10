#!/bin/bash

# To be run in Git Bash on Windows
# Preconditions:
# - Git-svn working copy of micromanager2/trunk
# - Git-svn working copy of micromanager2/trunk/SecretDeviceAdapters (just placed in root; not submodule or anything)
# - Python 3.3 installed and in path as 'python'
# - Run this script from repository root

set -e

scripts_dir=buildscripts/vs2008-to-vs2010

base_branch=$(git rev-parse --abbrev-ref HEAD)
pushd SecretDeviceAdapters
secret_base_branch=$(git rev-parse --abbrev-ref HEAD)
popd
conv_branch=vs2010-test

in_both_repos() {
  eval "$1"
  pushd SecretDeviceAdapters
  eval "$1"
  popd
}

project_dirs="DeviceAdapters SecretDeviceAdapters TestDeviceAdapters DeviceKit MMCore MMCoreJ_wrap MMCorePy_wrap"
test_projects="DeviceKit/CoreTest/MMCoreTest DeviceKit/DeviceTest/DeviceTest DeviceKit/LibraryTest/LibraryTest DeviceKit/MultiThreadTest/MultiThreadTest"

###

echo
echo 'Creating git branches for conversion'
set +e
in_both_repos 'git stash save "Stash before conversion to vs2010"'
set -e
in_both_repos 'git checkout -b $conv_branch'

###

drop_script=$scripts_dir/drop-converted.sh
echo
echo 'Writing revert script to' $drop_script

cat > $drop_script <<EOF
#!/bin/bash
set -e
git checkout $base_branch
git branch -D $conv_branch
pushd SecretDeviceAdapters
git checkout $secret_base_branch
git branch -D $conv_branch
popd
EOF

###

echo
echo 'Ensuring all project files use CRLF newlines'
find $project_dirs -name '*.vcproj' -print0 | xargs -0 python $scripts_dir/fixnl.py --crlf
set +e # Skip silently if no files affected
in_both_repos 'git commit -a -m "Use CRLF newlines in all VC++ project files."'
set -e

###

echo
echo 'Switching to a single solution file'
echo
echo 'Manual step:'
echo 'Open MMCoreJ_wrap/MMCoreJ_wrap.sln in VS2008'
echo 'Save solution as micromanager.sln'
read -n 1 -p 'Press any key to continue'

git add micromanager.sln
git rm MMCoreJ_wrap/MMCoreJ_wrap.sln
git commit -m 'Replace MMCoreJ_wrap.sln with micromanager.sln.'

echo
echo 'Manual step:'
echo 'Add MMCorePy_wrap/MMCorePy_wrap.vcproj to micromanager.sln'
echo 'Add dependency on MMCore'
echo 'Save micromanager.sln'
read -n 1 -p 'Press any key to continue'

git add micromanager.sln
git rm MMCorePy_wrap/MMCorePy_wrap.sln
git commit -m 'Merge MMCorePy_wrap.sln into micromanager.sln.'

echo
echo 'Manual step:'
echo 'Add the following to micromanager.sln'
for proj in $test_projects; do echo ' ' ${proj}.vcproj; done
echo 'Add dependency for each on MMCore'
echo 'Then save micromanager.sln and close VS2008'
read -n 1 -p 'Press any key to continue'

git add micromanager.sln
for proj in $test_projects; do git rm ${proj}.sln; done
git commit -m 'Merge DeviceKit test projects into micromanager.sln.'

git rm TestDeviceAdapters/MMCamera/MMCamera.sln
git rm Test_Programs/\*.sln
git commit -m 'Remove per-project solution files (will become obsolete).'

pushd SecretDeviceAdapters
git rm \*.sln
git commit -m 'Remove per-project solution files (will become obsolete).'
popd

###

echo
echo 'Remove settings that cause problems during conversion'

find $project_dirs -name '*.vcproj' -exec sed -i -e '/^[ \t]*OutputDirectory=/d' '{}' \;
find $project_dirs -name '*.vcproj' -exec sed -i -e '/^[ \t]*IntermediateDirectory=/d' '{}' \;
find $project_dirs -name '*.vcproj' -exec sed -i -e '/^[ \t]*OutputFile=.*mmgr_dal_/d' '{}' \;
sed -i -e '/^[ \t]*OutputFile=.*_MMCorePy/d' MMCorePy_wrap/MMCorePy_wrap.vcproj

# Convert all back to CRLF newlines (sed writes LFs)
find $project_dirs -name '*.vcproj' -print0 | xargs -0 python $scripts_dir/fixnl.py --crlf

in_both_repos 'git commit -a -m "Prepare vcprojs for conversion to VS2010."'

###

echo
echo 'Converting to VS2010 project files'
echo
echo 'Manual step:'
echo 'Open micromanager.sln in VS2010; autoconvert and quit'
read -n 1 -p 'Press any key to continue'

echo
echo 'Unconverted projects to be converted individually:'
for vcproj in $(find DeviceAdapters TestDeviceAdapters SecretDeviceAdapters -name '*.vcproj'); do
  if [ ! -f ${vcproj%vcproj}vcxproj ]; then
    echo $vcproj
  fi
done
echo
echo 'Manual step:'
echo 'Convert the above projects outside of micromanager.sln'
read -n 1 -p 'Press any key to continue'

echo 'Adding *.vcxproj'
set +e
in_both_repos 'git add \*.vcxproj'
set -e

echo 'Deleting *.vcproj where *.vcxproj has been added'
in_both_repos 'for vcxproj in $(git diff --cached --name-only); do
  vcproj=${vcxproj%vcxproj}vcproj
  git rm $vcproj
done'

echo 'Adding *.vcxproj.filters'
set +e
in_both_repos 'git add \*.vcxproj.filters'
set -e

echo 'Removing *.vsprops and adding *.props'
set +e
git rm \*.vsprops
git add \*.props
set -e

echo 'Adding micromanager.sln'
git add -u micromanager.sln

in_both_repos 'git commit -a -m "Autoconvert projects to Visual Studio 2010."'

###

echo 'Manual step:'
echo 'Set TargetName = _MMCorePy and TargetExt = .pyd in MMCorePy_wrap.vcxproj'
read -n 1 -p 'Press any key to continue'

git add MMCorePy_wrap/MMCorePy_wrap.vcxproj
git commit -m 'Fix MMCorePy target name'

###

echo 'Post-processing VS2010 projects'
# Python 3.3
python $scripts_dir/fixup-vcxproj.py
set +e
in_both_repos 'git add \*.vcxproj'
in_both_repos 'git add \*.vcxproj.filters'
set -e
in_both_repos 'git commit -m "Apply scripted normalizations to VS2010 projects."'

###

echo
echo 'Ready to apply post-conversion patch set via git-rebase or git-am.'
