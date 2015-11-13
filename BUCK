include_defs('//plugins/gerrit-owners/common.defs')
include_defs('//lib/maven.defs')
include_defs('//lib/prolog/prolog.defs')

JACKSON_REV = '2.1.1'
maven_jar(
  name = 'jackson-core',
  id = 'com.fasterxml.jackson.core:jackson-core:%s' % JACKSON_REV,
  license = 'Apache2.0',
)

maven_jar(
  name = 'jackson-databind',
  id = 'com.fasterxml.jackson.core:jackson-databind:%s' % JACKSON_REV,
  license = 'Apache2.0',
)

maven_jar(
  name = 'jackson-annotations',
  id = 'com.fasterxml.jackson.core:jackson-annotations:%s' % JACKSON_REV,
  license = 'Apache2.0',
)

maven_jar(
  name = 'jackson-dataformat-yaml',
  id = 'com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:%s' % JACKSON_REV,
  license = 'Apache2.0',
)

# see common.defs on why this is a java_library2 rather than java_library
java_library(
  name = 'common',
  srcs = glob([
    'src/main/java/**/*.java',
  ]),
  deps = COMPILE_DEPS + EXTERNAL_DEPS,
  visibility = ['PUBLIC'],
)
