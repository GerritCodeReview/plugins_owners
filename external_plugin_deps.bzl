load("//tools/bzl:maven_jar.bzl", "maven_jar")

JACKSON_VER = "2.9.7"

def external_plugin_deps(omit_jackson_core = True):
    if not omit_jackson_core:
        maven_jar(
            name = "jackson-core",
            artifact = "com.fasterxml.jackson.core:jackson-core:" + JACKSON_VER,
            sha1 = "4b7f0e0dc527fab032e9800ed231080fdc3ac015",
        )

    maven_jar(
        name = "jackson-databind",
        artifact = "com.fasterxml.jackson.core:jackson-databind:" + JACKSON_VER,
        sha1 = "e6faad47abd3179666e89068485a1b88a195ceb7",
    )

    maven_jar(
        name = "jackson-annotations",
        artifact = "com.fasterxml.jackson.core:jackson-annotations:" + JACKSON_VER,
        sha1 = "4b838e5c4fc17ac02f3293e9a558bb781a51c46d",
    )

    maven_jar(
        name = "jackson-dataformat-yaml",
        artifact = "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:" + JACKSON_VER,
        sha1 = "a428edc4bb34a2da98a50eb759c26941d4e85960",
    )

    maven_jar(
        name = "snakeyaml",
        artifact = "org.yaml:snakeyaml:1.23",
        sha1 = "ec62d74fe50689c28c0ff5b35d3aebcaa8b5be68",
    )
