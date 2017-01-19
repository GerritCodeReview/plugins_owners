load("//tools/bzl:maven_jar.bzl", "maven_jar")

JACKSON_REV = '2.1.1'

def external_plugin_deps():
	maven_jar(
	  name = 'jackson_core',
	  artifact = 'com.fasterxml.jackson.core:jackson-core:%s' % JACKSON_REV,
	  sha1 = '82ad1c5f92f6dcc6291f5c46ebacb975eaa844de',
	)

	maven_jar(
	  name = 'jackson_databind',
	  artifact = 'com.fasterxml.jackson.core:jackson-databind:%s' % JACKSON_REV,
	  sha1 = '38d2b3c0c89af5b937fd98c3e558bf6b58c14aa2',
	)

	maven_jar(
	  name = 'jackson_annotations',
	  artifact = 'com.fasterxml.jackson.core:jackson-annotations:%s' % JACKSON_REV,
	  sha1 = '0b01cc83e745fc4425a3968fafbf8e5b8254a6dd',

	)

	maven_jar(
	  name = 'jackson_dataformat_yaml',
	  artifact = 'com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:%s' % JACKSON_REV,
	  sha1 = '3922d45de7f3ccabe8f16aad4060a6394373c6cb',
	)
