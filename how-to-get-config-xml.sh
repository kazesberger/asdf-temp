echo username: 
read username
echo apitoken:
read apitoken
echo foldername:
read foldername
echo jobname:
read jobname

curl -X GET -u $username:$apitoken https://ci.infonova.at/job/${foldername}/job/${jobname}/config.xml -o ./jobname-config.xml
