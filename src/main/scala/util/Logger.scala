package util

/**
 * ログを吐くためのラッパークラス
 * Created by Junya on 15/05/02.
 */
object Logger {

  def info(msg: String): Unit = {
    println("info: " + msg)
  }

  def error(msg: String): Unit = {
    println("[[[Error]]]: " + msg)
  }

  def debug(msg: String): Unit = {
    println("===Debug===: " + msg)
  }

  def fatal(msg: String): Unit = {
    println("!!!!fatal!!!!")
    println(msg)
    sys.exit(1)
  }

  def dump(msg: String, byteArray: Array[Byte]): Unit = {
    println("++dump: " + msg + "++ " )
    println(byteArray.map(_.binaryDump + " ").foldLeft("")(_+_))
  }

  implicit class byteDump(val byte: Byte) extends AnyVal {
    def binaryDump = {
      String.format("%8s:%1s", Integer.toBinaryString(byte & 0xFF), new String(Array(byte))).replace(' ', '0')
    }
  }

}
