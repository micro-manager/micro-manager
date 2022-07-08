<?php
//print_r($_FILES);


date_default_timezone_set('America/Los_Angeles');
$month =  date('Ym');
$uploadPlace_ = '/home/MM/configUploads/' . $month;
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
   $ipaddress = getenv(REMOTE_ADDR);
   $opath = $uploadPlace_ . '/'. $_FILES['file']['name'] . '_' . $ipaddress;

   if (file_exists($opath))
   {
      echo $_FILES['file']['name'] . ' already exists. ';
   }
   else
   {
      move_uploaded_file($_FILES['file']['tmp_name'],
      $opath);
      echo 'Stored in: ' . $opath;
   }
}
?>
