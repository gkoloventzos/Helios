#!/usr/bin/python

import argparse
import sys
import os
from os.path import expanduser
import shlex, subprocess

parser = argparse.ArgumentParser()
parser.add_argument("user", help="The user's google id")
parser.add_argument("-s","--search", help="A string to searched in the results")
parser.add_argument("-d","--decode", help="To decode again", action="store_true")

args=parser.parse_args()

home = "/home/ubuntu"
user_path = home + os.sep + "Users" + os.sep + args.user
candidates = user_path + os.sep + "candidates"
success = user_path + os.sep + "success"

if not os.path.isdir(candidates):
	os.makedirs(candidates)
if not os.path.isdir(success):
        os.makedirs(success)

directory = home + os.sep + "BarcodeLocalizer"

os.chdir(directory)

if args.decode:
	cmd = "sudo java -Djava.library.path=\"/usr/local/share/OpenCV/java/\" -cp " + home\
		+ "/opencv-2.4.9/build/bin/opencv-249.jar:.:./Barcode.jar:./javase-3.1.0.jar:./core-3.1.0.jar BarcodeBatchTester --matrix -dir /home/ubuntu/Users"\
		+ os.sep + args.user
	arguments = shlex.split(cmd)
	process = subprocess.Popen(arguments)
	(output, stderr) = process.communicate()

	if not process.returncode:
		print "running"
	else:
		print "not running"
		sys.exit(0)

if not args.search:
	sys.exit(0)

search_cmd = "grep \"" + args.search + "\" " + user_path + os.sep + "results.txt"
search_arguments = shlex.split(search_cmd)
search_process = subprocess.Popen(search_arguments)
(s_output, s_stderr) = search_process.communicate()

