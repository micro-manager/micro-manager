<?php

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
   $ipaddress = getenv(REMOTE_ADDR);

   $opath = $uploadPlace_ . '/'. $_FILES['file']['name'] . '_' . $ipaddress;

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
      $rcommand = 'rm ' . $opath . '.uu';
      exec( $rcommand);

      $mess = $opath . "\n" ;
      // extract beginning of report
      $rhandle = fopen( $opath, 'r');
      $lineCount = 0;
      if ($rhandle){
	 while(! feof($rhandle) ){
            $line = fgets($rhandle);
            $mess = $mess . rtrim($line) . "\n";
            $lineCount = $lineCount + 1;
            if( 2000 < $lineCount)
                break 1;
            }
       } 
       fclose($rhandle);

       mail('info@micro-manager.org', 'New Problem Report!', $mess);
       mail('karl.hoover@ucsf.edu', 'New Problem Report!', $mess);
       //mail('mugwortmoxa@hotmail.com', 'New Problem Report!', $mess);

   }// uploaded .uu was found
} // FILES was parsed
?>
