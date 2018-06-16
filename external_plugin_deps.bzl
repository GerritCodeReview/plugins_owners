load("//tools/bzl:maven_jar.bzl", "maven_jar")

JACKSON_REV = "2.8.9"

def external_plugin_deps():
        maven_jar(
          name = "jackson-core",
          artifact = "com.fasterxml.jackson.core:jackson-core:" + JACKSON_REV,
          sha1 = "82ad1c5f92f6dcc6291f5c46ebacb975eaa844de",
        )

        maven_jar(
          name = "jackson-databind",
          artifact = "com.fasterxml.jackson.core:jackson-databind:" + JACKSON_REV,
          sha1 = "4dfca3975be3c1a98eacb829e70f02e9a71bc159",
        )

        maven_jar(
          name = "jackson-annotations",
          artifact = "com.fasterxml.jackson.core:jackson-annotations:" + JACKSON_REV,
          sha1 = "e0e758381a6579cb2029dace23a7209b90ac7232",
        )

        maven_jar(
          name = "jackson-dataformat-yaml",
          artifact = "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:" + JACKSON_REV,
          sha1 = "607f3253c20267e385c85f60c859760a73a29e37",
        )

        maven_jar(
          name = "snakeyaml",
          artifact = "org.yaml:snakeyaml:1.17",
          sha1 = "7a27ea250c5130b2922b86dea63cbb1cc10a660c",
        )
