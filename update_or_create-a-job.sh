#!/bin/bash

if [ -z "$1" ] ; then
	echo "usage: script.sh <config.xml>"
	exit 1
fi

echo username: 
read username
echo apitoken:
read apitoken
echo foldername:
read foldername
echo jobname:
read jobname

curl -X POST -u $username:$apitoken https://ci.infonova.at/job/${foldername}/job/${jobname}/config.xml --data-binary "@$1"
