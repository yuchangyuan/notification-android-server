package me.ycy.notification.server.android

import android.app.Activity
import android.os.Bundle

import android.content.Intent
import android.content.Context
import android.app.Service
import android.app.NotificationManager
import android.app.Notification
import android.app.PendingIntent
import android.os.{Binder, IBinder}
import android.preference.PreferenceManager

import scala.collection.JavaConversions._

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.handshake.ServerHandshakeBuilder
import org.java_websocket.server.WebSocketServer
import org.java_websocket.exceptions.InvalidDataException
import org.java_websocket.drafts.Draft

import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingQueue
import java.util.UUID

import android.util.Log

import me.ycy.notification.api._

object NotificationService {
  sealed trait Event extends Serializable
  sealed trait RenderEvent extends Event
  case class Sms(from: String, text: String, time: Long) extends RenderEvent
  case class Call(from: String) extends RenderEvent

  // client number change
  case class ClientChange(newClient: Boolean, n: Int) extends Event

  val ExtraData = "data"

  object conf {
    val serverEnabled = "serverEnabled"
    val port = "port"
    val bindLocal = "bindLocal"
    val hideContent = "hideContent"
    val remoteAllowed = "remoteAllowed"
  }

  val queue: LinkedBlockingQueue[RenderEvent] =
    new LinkedBlockingQueue[RenderEvent]()

  def startStopService(context: Context): Unit = {
    val i: Intent = new Intent(context, classOf[NotificationService])
    context.startService(i)
  }

  def startStopServiceExtra(context: Context, data: Serializable): Unit = {
    val i: Intent = new Intent(context, classOf[NotificationService])
    i.putExtra(ExtraData, data)
    context.startService(i)
  }

  trait Profile {
    val addr: InetSocketAddress
    def remoteAllowed(addr: InetSocketAddress): Boolean
    def hideContent(): Boolean
    def queryName(number: String): Option[String]
    def sendEvent(e: Event)
  }
}

class NotificationServer(val p: NotificationService.Profile)
extends WebSocketServer(p.addr) {
  import NotificationService._

  val AccessDenied = 4000
  val Tag = "NotificationServer"

  override def onWebsocketHandshakeReceivedAsServer(
    c: WebSocket, d: Draft, r: ClientHandshake
  ): ServerHandshakeBuilder = {
    val addr = c.getRemoteSocketAddress()
    if (!p.remoteAllowed(addr)) {
      Log.d(Tag, addr.getAddress.getHostAddress() + " access denied")
      throw new InvalidDataException(AccessDenied)
    }
    else {
      super.onWebsocketHandshakeReceivedAsServer(c, d, r)
    }
  }


  override def onOpen(c: WebSocket, handshake: ClientHandshake): Unit = {
    Log.d(Tag, c + " connected")

    try {
      val cc = CreateCommand(
        client = "android",
        title = "Greetings",
        notificationClass = List("low-urgency")
      )
      c.send(cc.toJson.toString)

      p.sendEvent(ClientChange(true, connections().size))
    }
    catch {
      case _: Throwable ⇒
    }
  }

  override def onClose(
    c: WebSocket,
    code: Int,
    reason: String,
    remote: Boolean
  ): Unit = {
    p.sendEvent(ClientChange(false, connections().size))
  }

  override def onMessage(c: WebSocket, m: String): Unit = {
    Log.d(Tag, c.getRemoteSocketAddress + " get message " + m);
    // only need to process closed event
  }

  override def onError(c: WebSocket, ex: Exception): Unit = {
    Log.d(Tag, c.getRemoteSocketAddress() + " exception " + ex)
  }

  def sendToAll(m: String) = this.synchronized {
    for (c ← connections()) {
      Log.e(Tag, "try send to " + c.getRemoteSocketAddress)
      try { c.send(m) } catch { case _: Throwable ⇒ }
    }
  }

  val queueReader = new Thread {
    override def run() = {
      while (!Thread.interrupted()) {
        try {
          val e = queue.take()
          Log.e(Tag, "queue get event.")
          sendToAll(Renderer.render(e, p).toJson.toString)
        }
        catch { case _: InterruptedException ⇒ }
      }
    }
  }

  override def start(): Unit = {
    super.start()
    queueReader.start()
  }

  override def stop(): Unit = {
    queueReader.interrupt()
    super.stop()
  }
}

class NotificationService extends Service {
  import NotificationService._
  val Tag = "NotificationService"

  //  ----------------- websocket server -----------------
  private var ns: NotificationServer = null
  private val nsSync = new Object

  private def nsStart(): Unit = nsSync.synchronized {
    if (ns == null) {
      ns = new NotificationServer(createProfile())
      ns.start()
      showNotification(
        getText(R.string.service_started).toString,
        getText(R.string.service_label).toString,
        getText(R.string.service_started).toString)
    }
  }

  private def nsStop(): Unit = nsSync.synchronized {
    if (ns != null) {
      ns.stop()
      ns = null
      showNotification(
        getText(R.string.service_stopped).toString,
        getText(R.string.service_label).toString,
        getText(R.string.service_stopped).toString,
        R.drawable.app_n1
      )
    }
  }

  private def createProfile() = {
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    val port = {
      try { Integer.parseInt(prefs.getString(conf.port, "7765")) }
      catch { case _: Throwable ⇒ 7765 }
    }
    val bindAddr =
      if (prefs.getBoolean(conf.bindLocal, true))
        new InetSocketAddress("127.0.0.1", port)
      else
        new InetSocketAddress(port)

    val remoteAllowedRegexp = {
      prefs.getString(conf.remoteAllowed, ".*")
    }

    new Profile {
      import android.net.Uri
      import android.provider.ContactsContract.PhoneLookup
      import android.content.ContentResolver

      val addr = bindAddr
      def hideContent(): Boolean = {
        prefs.getBoolean(conf.hideContent, true)
      }

      def remoteAllowed(addr: InetSocketAddress): Boolean = {
        try {
          addr.getAddress.getHostAddress().matches(remoteAllowedRegexp)
        }
        catch {
          case _: Throwable ⇒ false
        }
      }

      def queryName(number: String): Option[String] = {
        val resolver = NotificationService.this.getContentResolver;
        val uri = Uri.withAppendedPath(
          PhoneLookup.CONTENT_FILTER_URI,
          Uri.encode(number)
        )

        val cur = resolver.query(
          uri,
          Array("display_name"),
          null, null, null
        )

        val ret = if (cur.moveToFirst()) Some(cur.getString(0)) else None
        cur.close

        ret
      }

      def sendEvent(e: Event) = {
        startStopServiceExtra(NotificationService.this, e)
      }
    }
  }

  //  ------------------- notification -------------------
  val NotificationId = R.string.service_started

  lazy val nm: NotificationManager =
    getSystemService(Context.NOTIFICATION_SERVICE).
    asInstanceOf[NotificationManager]

  private def showNotification(
    scroll: String,
    title: String,
    body: String,
    icon: Int = R.drawable.app_n
  ) {
    val n = new Notification(
      icon, scroll,
      System.currentTimeMillis()
    )

    val contentIntent = PendingIntent.getActivity(
      this, 0,
      new Intent(this, classOf[MainActivity]), 0
    )

    n.setLatestEventInfo(
      this, title,
      body, contentIntent
    )

    startForeground(NotificationId, n)
  }

  //  ---------------- service interface -----------------
  override def onCreate(): Unit = {
    nsStart()
  }

  override def onDestroy(): Unit = {
    nsStop()
  }

  override def onStartCommand(i: Intent, flags: Int, startId: Int): Int = {
    Log.d(Tag, "start command")
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    val run = prefs.getBoolean(conf.serverEnabled, true)

    if (run) { nsStart() } else { nsStop() }

    if (i != null) {
      i.getSerializableExtra(ExtraData) match {
        case e: Event ⇒ {
          Log.d(Tag, "with event, type = " + e.getClass.getName)
          e match {
            case re: RenderEvent ⇒ queue.put(re)
            case ClientChange(nc, n) ⇒ {
              val msg = if (nc) getText(R.string.client_connected)
                        else getText(R.string.client_disconnected)
              showNotification(
                msg.toString,
                getText(R.string.service_label).toString,
                getText(R.string.service_started).toString + ", " +
                n.toString + " client connected."
              )
            }
          }
        }
        case _ ⇒
      }
    }

    Service.START_STICKY
  }


  val binder = new Binder {
    def getService() = NotificationService.this;
  }

  override def onBind(i: Intent) = binder
}
