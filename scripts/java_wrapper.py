#!/usr/bin/python


import argparse
from os.path import expanduser



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

