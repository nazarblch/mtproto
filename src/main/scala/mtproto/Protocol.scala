package mtproto

import javax.crypto.Cipher
import scodec.Codec
import zio._
import zio.nio.channels._
import zio.stream._
import scodec._
import scodec.bits._
import codecs._

trait Message

case class ReqPQ(nonce: Long) extends Message {
  val codec: Codec[ReqPQ] = (int64).as[ReqPQ]
}
case class ResPQ(nonce: Long, server_nonce: Long, pq: String) extends Message {
  val codec: Codec[ResPQ] = (int64 :: int64 :: ascii32).as[ResPQ]
}

case class Req_DH_Params(nonce: Long, server_nonce: Long, p: String, q: String) extends Message {
  val codec: Codec[Req_DH_Params] = (int64 :: int64 :: ascii32 :: ascii32).as[Req_DH_Params]
}
case class Res_DH_Params(nonce: Long, server_nonce: Long, g: String, p: String) extends Message {
  val codec: Codec[Res_DH_Params] = (int64 :: int64 :: ascii32 :: ascii32).as[Res_DH_Params]
}

object Params {
  val req_pq: ReqPQ = ReqPQ(123)
  val req_pq_code: Array[Byte] = req_pq.codec.encode(req_pq).require.toByteArray
  val res_pq: ResPQ = ResPQ(123, 1, "45346")
  val req_dh: Req_DH_Params = Req_DH_Params(123, 1, "23", "4343")
  val res_dh: Res_DH_Params = Res_DH_Params(123, 1, "33", "3443")
  val req_dh_code: Array[Byte] = req_dh.codec.encode(req_dh).require.toByteArray
}


trait State {
  type Req
  type Resp

  def read_codec: Codec[Req]
  def req_size: Int = 100
  def write_codec: Codec[Resp]

  def process(data: Req): (Option[State], Resp)
  def decode_and_process(data: Array[Byte]): Task[(Option[State], Array[Byte])] = Task{
    val (state, res) = process(read_codec.decode(BitVector(data)).require.value)
    val res_bytes = write_codec.encode(res).require.toByteArray
    (state, res_bytes)
  }

  def is_encrypted: Boolean = false
}

object State1 extends State {
  override type Req = ReqPQ
  override type Resp = ResPQ

  def read_codec: Codec[ReqPQ] = Params.req_pq.codec
  def write_codec: Codec[ResPQ] = Params.res_pq.codec

  override def toString: String = "S1"
  def process(data: ReqPQ): (Option[State], ResPQ) = {
    (Some(State2), Params.res_pq)
  }
}

object State2 extends State {
  override type Req = Req_DH_Params
  override type Resp = Res_DH_Params

  def read_codec: Codec[Req_DH_Params] = Params.req_dh.codec
  override def req_size: Int = Params.req_dh_code.length
  def write_codec: Codec[Res_DH_Params] = Params.res_dh.codec

  override def toString: String = "S2"
  def process(data: Req_DH_Params): (Option[State], Res_DH_Params) = {
    (None, Params.res_dh)
  }
  override def is_encrypted: Boolean = true
}

class Protocol {

  val initial_state: UIO[Ref[State]] = Ref.make(State1)

  def decryptRsa(data: Chunk[Byte], size: Int): Chunk[Byte] = {
    val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, Keys.privateKey)
    Chunk(cipher.doFinal(data.toArray).takeRight(size):_*)
  }

  def handle(channel: AsynchronousSocketChannel, state: Ref[State]) = {

    def read() = ZStream
      .fromEffectOption(
        for {
          s <- state.get
          data <- channel.read(1000).tap(_ => console.putStrLn("Read chunk")).orElse(ZIO.fail(None)).map(chunk => {
            if (s.is_encrypted) decryptRsa(chunk, s.req_size) else chunk
          })
        } yield {data}
      )
      .runHead.map(_.get.toArray)


    def write(data: Array[Byte]) = {
      channel.write(Chunk(data:_*)).tap(_ => console.putStrLn("Write chunk"))
    }

    def process(): ZIO[console.Console, Throwable, Option[State]] = (for {
      input <- read()
      s <- state.get
      _ <- console.putStrLn("input: " + s.read_codec.decode(BitVector(input)).require.value)
      (new_state, res) <- s.decode_and_process(input)
      _ <- console.putStrLn("out: " + s.write_codec.decode(BitVector(res)).require.value)
      _ <- write(res)
      _ <- state.update(s1 => if (new_state.isDefined) new_state.get else s1)

    } yield {
      println(s"$s -> ${new_state.getOrElse("")}")
      new_state
    }).flatMap(s => s.fold[ZIO[console.Console, Throwable, Option[State]]](ZIO(None))(_ => process()))

    process()

  }
}