#~/bin/bash

# This script takes the input disk image Micro-Manager.dmg
# copies the content of SOURCE (which has a fully working Micro-Manager installation)
# to the mounted disk, unmounts, compresses the resulting disk image
# and renames to MicroManager$VERSION.dmg

# Options:
#     -r Release version
#     -d Daily version
#     -s /full/path/to/binaries (default to /Applications/Micro-Manager1.3
# script defaults to daily version

export SOURCE=/Applications/Micro-Manager1.4
export TMP=Micro-Manager1.4
export VERSION=`cat ../version.txt`
#default to daily build
export VERSION=$VERSION-`date "+%Y%m%d"`

while getopts "rdhs:" optname
  do
    case "$optname" in
      "s")
        export SOURCE=$OPTARG
        ;;
      "d")
        ;;
      "r")
         export VERSION=`cat ../version.txt`
        ;;
      "h")
        echo "Usage: $0 <options>
          -r Release version
          -d Daily build
          -s /full/path/to/binaries (default to $SOURCE)"
        exit
        ;;
    esac
  done

test -f $TMP.sparseImage && rm $TMP.sparseImage
hdiutil convert Micro-Manager1.4.dmg -format UDSP -o $TMP
hdiutil mount $TMP.sparseImage
echo "Installing code from $SOURCE"
cp -r $SOURCE/* /Volumes/Micro-Manager/Micro-Manager1.4/
hdiutil eject /Volumes/Micro-Manager
test -f Micro-Manager$VERSION.dmg && rm Micro-Manager$VERSION.dmg
hdiutil convert $TMP.sparseImage -format UDBZ -o Micro-Manager$VERSION.dmg
test -f $TMP.sparseImage && rm $TMP.sparseImage
