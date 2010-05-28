dnl AC_FIND_FILE (file, dirs, variable)

AC_DEFUN([AC_FIND_FILE],
[
   $3=NO
   for x in $2
   do
      for y in $1
      do
         if test -r "$x/$y"
         then
            $3=$x
            break 2
         fi
      done
   done
]
)

