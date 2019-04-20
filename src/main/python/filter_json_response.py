import json
import sys
import argparse

parser = argparse.ArgumentParser(description='Download Disqus comments.')
parser.add_argument('username', type=str)
#parser.add_argument('num_comments', type=int, default=0)

args = parser.parse_args()

def filter_json_response(json_obj):
	objects = json_obj["response"]["objects"]
	comments = []
	filtered_json_obj = {"cursor": json_obj["cursor"], "response": {"objects": {}}}
	for obj_name, obj in objects.iteritems():
		if not obj_name.startswith("forums.Post"):
			continue
		if obj["author"].get("username", None) != args.username:
			continue
		filtered_json_obj["response"]["objects"][obj_name] = {"message": obj["message"], "author": obj["author"], "createdAt": obj["createdAt"], "url": obj.get("url", ""),
			"forum": obj["forum"], "likes": obj["likes"], "dislikes": obj["dislikes"]}
	sys.stdout.write(json.dumps(filtered_json_obj))
	

response_json = sys.stdin.read()
filter_json_response(json.loads(response_json))