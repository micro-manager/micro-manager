<?php
//print_r($_FILES);


date_default_timezone_set('America/Los_Angeles');
$month =  date('Ym');
$uploadPlace_ = '/home/MM/corelogUploads/' . $month;
if (!file_exists($uploadPlace_)) 
{
   mkdir($uploadPlace_, 0750);
}

if ($_FILES['file']['error'] > 0)
{
  echo 'Return Code: ' . $_FILES['file']['error'] . '<br />';
}
else
{
   echo 'Upload: ' . $_FILES['file']['name'] . '<br />';
   echo 'Type: ' . $_FILES['file']['type'] . '<br />';
   echo 'Size: ' . ($_FILES['file']['size'] / 1024) . ' Kb<br />';
   echo 'Temp file: ' . $_FILES['file']['tmp_name'] . '<br />';
   $opath = $uploadPlace_ . '/'. $_FILES['file']['name'];

   if (file_exists($opath . '.uu' ))
   {
      echo $_FILES['file']['name'] . ' already exists. ';
   }
   else
   {
      move_uploaded_file($_FILES['file']['tmp_name'],
      $opath . '.uu');
      echo 'Stored in: ' . $opath . '.uu  <br />';
      $dcommand = 'uudecode -o ' . $opath . '.gz ' . $opath . '.uu';
      echo 'decode command: ' . $dcommand . '<br />';
      exec( $dcommand);
      $gcommand = 'gzip -d ' . $opath . '.gz';
      echo 'gzip command:  ' . $gcommand . '<br />';
      exec( $gcommand);
      
   }
}
?>
