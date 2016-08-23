String[] command = [
  properties['make.bin'],
  '-C', properties['rabbitmq.dir'],
  '--no-print-directory',
  'show-test-tls-certs-dir',
  "DEPS_DIR=${properties['deps.dir']}",
]

def pb = new ProcessBuilder(command)
pb.redirectErrorStream(true)

def process = pb.start()

// We are only interested in the last line of output. Previous lines, if
// any, are related to the generation of the test certificates.
def whole_output = ""
process.inputStream.eachLine {
  whole_output += it
  project.properties['test-tls-certs.dir'] = it.trim()
}
process.waitFor()
if (process.exitValue() != 0) {
  println(whole_output.trim())
  fail("Failed to query test TLS certs directory with command: ${command.join(' ')}")
}
