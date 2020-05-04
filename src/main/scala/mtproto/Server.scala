package mtproto

import zio.{App, Managed, Ref, console}
import zio.nio.channels.AsynchronousServerSocketChannel
import zio.nio.core.SocketAddress
import zio.stream.ZStream

object Server extends App {

  def run(args: List[String]) =
    ZStream
      .managed(server(8080))
      .flatMap(handleConnections)
      .runDrain
      .orDie
      .as(0)

  def server(port: Int): Managed[Exception, AsynchronousServerSocketChannel] =
    for {
      server        <- AsynchronousServerSocketChannel()
      socketAddress <- SocketAddress.inetSocketAddress(port).toManaged_
      _             <- server.bind(socketAddress).toManaged_
    } yield server

  def handleConnections(server: AsynchronousServerSocketChannel): ZStream[console.Console, Throwable, Unit] =
    ZStream
      .repeatEffect(server.accept.preallocate)
      .map(conn => ZStream.managed(conn.ensuring(console.putStrLn("Connection closed")).withEarlyRelease))
      .flatMapPar[console.Console, Throwable, Unit](16) { connection =>
        connection
          .mapM {
            case (closeConn, channel) =>
              val proto = new Protocol()
              for {
                _ <- console.putStrLn("Received connection")
                state <- proto.initial_state
                res <- proto.handle(channel, state)
                _ <- closeConn
              } yield {
                println("proto res:" + res)
              }
          }
      }
}