#!/usr/bin/python

import argparse
import sys
import os
from os.path import expanduser
import shlex, subprocess
import numpy as np
import cv2


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
	cmd = "java -Djava.library.path=\"/usr/local/share/OpenCV/java/\" -cp " + home\
		+ "/opencv-2.4.9/build/bin/opencv-249.jar:.:./Barcode.jar:./javase-3.1.0.jar:./core-3.1.0.jar BarcodeBatchTester --matrix -dir ../Users"+ os.sep + args.user + " >/dev/null 2>/dev/null"
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

search_cmd = "grep -A2 \"" + args.search + "\" " + user_path + os.sep + "results.txt"
print search_cmd
search_arguments = shlex.split(search_cmd)
#search_process = subprocess.Popen(search_arguments, stdout=subprocess.PIPE)
#(s_output, s_stderr) = search_process.communicate()
out = subprocess.check_output(search_arguments)
print out

bla = out.strip()
image = ""
create = True
for line in bla.split("\n"):
	if line.startswith('--') or not line:
		continue
	if create:
		first = line.split(" ")
		image = first[0]
		create = False
		continue
	img = cv2.imread(user_path + os.sep + image ,1)
#	cv2.imshow('image',img)
#	cv2.waitKey(0)
#	cv2.destroyAllWindows()
	coordinates = line.split(" ")
	top_left = coordinates[4]
	top_left = top_left.strip('(),')
	top_coordinates = top_left.split(",")
	bottom_right = coordinates[6]
	bottom_right = bottom_right.strip('(),')
	bottom_coordinates = bottom_right.split(",")
	img2 = cv2.rectangle(img,(int(float(top_coordinates[0])),int(float(top_coordinates[1]))),\
				(int(float(bottom_coordinates[0])),int(float(bottom_coordinates[1]))),(0,255,0),3)
	#print int(float(top_coordinates[0])), int(float(top_coordinates[1])), int(float(bottom_coordinates[0])),int(float(bottom_coordinates[1]))
	#path = user_path + os.sep + args.search + "_" + image
	path = "/tmp/" + args.search + "_" + image
	cv2.imwrite(path,img)
	create = True
#	print image + " " + top_left + " " + bottom_right
