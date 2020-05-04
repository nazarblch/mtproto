package mtproto

import java.net.InetSocketAddress
import java.nio.channels._
import java.nio.ByteBuffer
import javax.crypto.Cipher
import scodec.bits.BitVector

object TestClient extends App {

  val client = AsynchronousSocketChannel.open
  val hostAddress = new InetSocketAddress("localhost", 8080)
  val future = client.connect(hostAddress)

  future.get()

  def encryptRsa(data: Array[Byte]): Array[Byte] = {
    val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, Keys.publicKey)
    cipher.doFinal(data)
  }

  def sendMessage(message: Array[Byte], encrypt: Boolean = false) = {
    val buffer = ByteBuffer.wrap(if (encrypt) encryptRsa(message) else message)
    client.write(buffer).get()
    val buffer2 = ByteBuffer.allocate(100)
    client.read(buffer2).get()
    buffer2.array
  }

  val resp1 = sendMessage(Params.req_pq_code)
  val resp2 = sendMessage(Params.req_dh_code, encrypt = true)

  println(Params.res_pq.codec.decode(BitVector(resp1)).require.value)
  println(Params.res_dh.codec.decode(BitVector(resp2)).require.value)
}