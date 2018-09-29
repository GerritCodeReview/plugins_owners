load("//tools/bzl:maven_jar.bzl", "maven_jar")

JACKSON_REV = "2.6.6"

def external_plugin_deps(omit_jackson_core = True):
    if not omit_jackson_core:
        maven_jar(
            name = "jackson-core",
            artifact = "com.fasterxml.jackson.core:jackson-core:%s" % JACKSON_REV,
            sha1 = "02eb801df67aacaf5b1deb4ac626e1964508e47b",
        )

    maven_jar(
        name = "jackson-databind",
        artifact = "com.fasterxml.jackson.core:jackson-databind:%s" % JACKSON_REV,
        sha1 = "5108dde6049374ba980b360e1ecff49847baba4a",
    )

    maven_jar(
        name = "jackson-annotations",
        artifact = "com.fasterxml.jackson.core:jackson-annotations:%s" % JACKSON_REV,
        sha1 = "7ef6440e71531604aa44a5eb62d4b466ffbf7e8f",
    )

    maven_jar(
        name = "jackson-dataformat-yaml",
        artifact = "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:%s" % JACKSON_REV,
        sha1 = "d5c23915aa943541c38065a72ed50941f2fd278d",
    )
