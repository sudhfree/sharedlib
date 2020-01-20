Param ($ComputerName,

       $WorkSpace,

       $Webapp_Path,

       $appname)



$secpass = ConvertTo-SecureString "Purusharth@123" -AsPlainText -Force



$cred = New-Object System.Management.Automation.PSCredential("Administrator", $secpass)

try {

$session = New-PSSession -ComputerName $ComputerName -Credential $cred


#New-Item -Path "$Webapp_Path" -Name "testfile1.txt" -ItemType "file" -Value "This is a text string."
Copy-Item -Path "$appname.war" -Destination "$Webapp_Path\$appname.war" -ToSession $session

}

catch (e)

{

       throw e

}