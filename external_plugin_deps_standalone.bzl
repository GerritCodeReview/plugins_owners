load("//tools/bzl:maven_jar.bzl", "maven_jar", "GERRIT")

JACKSON_REV = "2.1.1"
PROLOG_VERS = "1.4.3"
PROLOG_REPO = GERRIT

def external_plugin_deps_standalone():
  maven_jar(
    name = "jackson_core",
    artifact = "com.fasterxml.jackson.core:jackson-core:%s" % JACKSON_REV,
    sha1 = "82ad1c5f92f6dcc6291f5c46ebacb975eaa844de",
  )

  maven_jar(
    name = "jackson_databind",
    artifact = "com.fasterxml.jackson.core:jackson-databind:%s" % JACKSON_REV,
    sha1 = "38d2b3c0c89af5b937fd98c3e558bf6b58c14aa2",
  )

  maven_jar(
    name = "jackson_annotations",
    artifact = "com.fasterxml.jackson.core:jackson-annotations:%s" % JACKSON_REV,
    sha1 = "0b01cc83e745fc4425a3968fafbf8e5b8254a6dd",

  )

  maven_jar(
    name = "jackson_dataformat_yaml",
    artifact = "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:%s" % JACKSON_REV,
    sha1 = "3922d45de7f3ccabe8f16aad4060a6394373c6cb",
  )

  maven_jar(
    name = "prolog_runtime",
    artifact = "com.googlecode.prolog-cafe:prolog-runtime:" + PROLOG_VERS,
    attach_source = False,
    repository = PROLOG_REPO,
    sha1 = "d5206556cbc76ffeab21313ffc47b586a1efbcbb",
  )

  maven_jar(
    name = "prolog_compiler",
    artifact = "com.googlecode.prolog-cafe:prolog-compiler:" + PROLOG_VERS,
    attach_source = False,
    repository = PROLOG_REPO,
    sha1 = "f37032cf1dec3e064427745bc59da5a12757a3b2",
  )

  maven_jar(
    name = "prolog_io",
    artifact = "com.googlecode.prolog-cafe:prolog-io:" + PROLOG_VERS,
    attach_source = False,
    repository = PROLOG_REPO,
    sha1 = "d02b2640b26f64036b6ba2b45e4acc79281cea17",
  )

  maven_jar(
    name = "cafeteria",
    artifact = "com.googlecode.prolog-cafe:prolog-cafeteria:" + PROLOG_VERS,
    attach_source = False,
    repository = PROLOG_REPO,
    sha1 = "e3b1860c63e57265e5435f890263ad82dafa724f",
  )

