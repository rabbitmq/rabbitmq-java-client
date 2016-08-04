import java.security.MessageDigest

def md5(final file) {
  MessageDigest digest = MessageDigest.getInstance("MD5")
  file.withInputStream() { is ->
    byte[] buffer = new byte[8192]
    int read = 0
    while ((read = is.read(buffer)) > 0) {
      digest.update(buffer, 0, read);
    }
  }
  byte[] md5sum = digest.digest()
  BigInteger bigInt = new BigInteger(1, md5sum)
  return bigInt.toString(16)
}

def generate_source(final type, final filename) {
  String[] command = [
    'python',
    properties['script'], type,
    properties['spec'],
    filename
  ]

  def pb = new ProcessBuilder(command)
  pb.environment().put('PYTHONPATH', properties['codegen.dir'])
  pb.redirectErrorStream(true)

  def process = pb.start()
  process.waitFor()
  if (process.exitValue() != 0) {
    println(process.in.text.trim())
    fail("Failed to generate ${filename} with command: ${command.join(' ')}")
  }
}

def maybe_regen_source(final type, final filename) {
  def file = new File(filename)

  if (file.exists()) {
    def tmp_filename = filename + '.new'
    def tmp_file = new File(tmp_filename)

    generate_source(type, tmp_filename)
    old_md5 = md5(file)
    new_md5 = md5(tmp_file)

    if (old_md5 == new_md5) {
      tmp_file.delete()
    } else {
      tmp_file.renameTo(file)
    }
  } else {
    generate_source(type, filename)
  }

}

maybe_regen_source('header', properties['header'])
maybe_regen_source('body', properties['body'])
