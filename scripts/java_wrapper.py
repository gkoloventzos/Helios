#!/usr/bin/python

import argparse
import sys
import os
from os.path import expanduser
import shlex, subprocess

parser = argparse.ArgumentParser()
parser.parse_args()
parser.add_argument("user", help="The user's google id")
parser.add_argument("-s","--search", help="A string to searched in the results")

home = expanduser("~")
user_path = home + os.sep + "Users" + os.sep + parser.user
candidates = user_path + os.sep + "candidates"
success = user_path + os.sep + "success"

if not os.path.isdir(candidates):
	os.makedir(candidates)
if not os.path.isdir(success):
        os.makedir(success)

directory = home + os.sep + "BarcodeLocalizer"

if not chdir(directory):
	sys.exit(-1)

cmd = "java -Djava.library.path=\"/usr/local/share/OpenCV/java/\" -cp " + home + "/opencv-2.4.9/build/bin/opencv-249.jar:.:./Barcode.jar:./javase-3.1.0.jar:./core-3.1.0.jar BarcodeBatchTester -dir " + user_path

arguments = shlex.split(cmd)
process = subprocess.Popen(args)
i(output, stderr) = process.communicate()

if p.returncode:
	print "running"
else
	print "not running"

