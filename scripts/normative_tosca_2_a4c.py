import yaml
import sys
import collections
import os
from pathlib import Path

if len(sys.argv) != 2:
    raise Exception("Required params: the tosca template version")

_mapping_tag = yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG

def dict_representer(dumper, data):
  return dumper.represent_dict(data.items())

def dict_constructor(loader, node):
  return collections.OrderedDict(loader.construct_pairs(node))

def get_artifact_name(origName, serverPath):
  return serverPath + "ansible-role-" + origName.split(".")[1] + "/archive/master.zip"

yaml.add_representer(collections.OrderedDict, dict_representer)
yaml.add_constructor(_mapping_tag, dict_constructor)


toscaTemplateVer = sys.argv[1]#os.path.join(os.path.sep, sys.argv[1], "dummy_artifacts")

tosca = yaml.load(sys.stdin)


tosca["artifact_types"]["tosca.artifacts.Root"].pop("properties", None)

tosca["metadata"] = {"template_name": "normative-types",
	"template_version": toscaTemplateVer,
	"template_author": "OpenStack/GRyCAP"}
tosca["description"] = "Contains the types definition as defined by the TOSCA committee"
print(yaml.dump(tosca, default_flow_style=False))
