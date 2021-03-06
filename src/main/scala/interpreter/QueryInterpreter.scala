package interpreter

import java.net.InetAddress
import java.nio.{ByteOrder, ByteBuffer}
import java.util.concurrent.TimeUnit

import actor.{SendMessage, SharedFileManagerActor}
import actor.SharedFileManagerActor.FileSearch
import akka.actor.ActorContext
import akka.pattern.ask
import akka.util.Timeout
import descriptor.{QueryHitsDescriptor, QueryDescriptor}
import descriptor.QueryHitsDescriptor.Result
import gnutella.Gnutella
import model.Settings
import util.{NetworkUtil, ActorUtil, Logger}

/**
 * QueryDescriptorのバイト列を解釈して処理を行なうオブジェクト
 * Created by Junya on 15/05/14.
 */
object QueryInterpreter extends HeaderInterpreter {

  /**
   * QueryDescriptorに対する処理を行なう
   * @param header ヘッダーのバイト列
   * @param payload ペイロードのバイト列
   * @param callerContext 呼び出し元のActorRef
   * @return 転送するQueryDescriptor
   */
  def execute(header: Array[Byte], payload: Array[Byte],
              callerContext: ActorContext): Option[QueryDescriptor] = {
    val query = parse(header, payload)
    Logger.info("query: " + query.descriptorId)
    Logger.debug("query->criteria: " + query.searchCriteria)
    processQuery(query, callerContext)
    Option(query)
  }

  /**
   * Queryの内容から，ファイルを探索し，結果をQueryHitsとして送信する
   * @param query
   */
  private def processQuery(query: QueryDescriptor, caller: ActorContext) = {
    import scala.concurrent.ExecutionContext.Implicits.global

    val fileManager = ActorUtil.getActor(caller.system, SharedFileManagerActor.name)

    implicit val timeout = Timeout(5000, TimeUnit.MILLISECONDS)

    val f = fileManager ? FileSearch(query.searchCriteria, true)

    f.onSuccess( {
      case res: List[SharedFileManagerActor.FileInfo] => sendQueryHits(query, res, caller)
      case _ => Logger.info("No search result. query is [" + query.searchCriteria  +"]")
    })
  }

  /**
   * 検索結果に応じたQueryHitsを送信する
   * @param query QueryDescriptor
   * @param result 検索結果
   */
  private def sendQueryHits(query: QueryDescriptor,
                            result: List[SharedFileManagerActor.FileInfo],
                            caller: ActorContext): Unit = {
    val queryHits = new QueryHitsDescriptor
    // Header
    queryHits.descriptorId(query.descriptorId)
    queryHits.ttl = query.hops
    queryHits.hops = 0
    //Payload
    queryHits.numberOfHits = result.length.toByte
    queryHits.port = Settings.FILE_SERVER_PORT.toShort
    queryHits.ipAddress = getLocalAddress()
    queryHits.firewalledIndicator = false //TODO:適切に判断
    queryHits.xmlMetaData = false
    queryHits.resultSet_(buildResultSet(result))
    queryHits.optionalQhdData = Array() // Optionなので無視
    queryHits.serventIdentifier = Gnutella.getServentIdentifier

    caller.self ! SendMessage(queryHits)
  }

  private def getLocalAddress(): InetAddress = {
    NetworkUtil.getMyAddress match {
      case Some(addr) => addr
      case None => Logger.fatal("cannot get local address"); InetAddress.getLocalHost
    }
  }

  /**
   * 検索結果より，ResultSetを作成する
   * @param result
   * @return
   */
  private def buildResultSet(result: List[SharedFileManagerActor.FileInfo]): Array[Result] = {
    result map(fi => new Result(fi._4, fi._2, fi._1, "")) toArray
  }

  /**
   * QueryDescriptorのバイト列をパースする
   * @param header ヘッダーのバイト列
   * @param payload ペイロードのバイト列
   * @return パースされたQueryDescriptorオブジェクト
   */
  private def parse(header: Array[Byte], payload: Array[Byte]): QueryDescriptor = {
    val query = new QueryDescriptor
    parseHeader(header, query)

    val head = payload.slice(0,2)
    val tail = payload.slice(2,payload.length)
    query.minimumSpeed = ByteBuffer.allocate(2).put(head)
      .order(ByteOrder.LITTLE_ENDIAN).getShort(0)

    query.searchCriteria = new String(tail.takeWhile(_ != 0.toByte))

    // 拡張用なので無視
    // query.queryData
    query
  }

}
