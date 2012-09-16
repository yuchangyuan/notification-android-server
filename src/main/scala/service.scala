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

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingQueue
import java.util.UUID

import android.util.Log

import me.ycy.notification.api._

object NotificationService {
  sealed trait Event
  case class Sms(from: String, text: String) extends Event
  case class Call(from: String) extends Event

  val ExtraData = "data"

  object conf {
    val serverEnabled = "serverEnabled"
    val port = "port"
    val bindLocal = "bindLocal"
    val hideContent = "hideContent"
  }

  val queue: LinkedBlockingQueue[Event] = new LinkedBlockingQueue[Event]()

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
    def hideContent(): Boolean
    def queryName(number: String): Option[String]
  }
}

class NotificationServer(val p: NotificationService.Profile)
extends WebSocketServer(p.addr) {
  import NotificationService._

  val Tag = "NotificationServer"

  override def onOpen(c: WebSocket, handshake: ClientHandshake): Unit = {
    Log.d(Tag, c + " connected")
    try {
      val cc = CreateCommand(
        client = "android",
        title = "Greetings",
        notificationClass = List("low-urgency")
      )
      c.send(cc.toJson.toString)
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
  }

  override def onMessage(c: WebSocket, m: String): Unit = {
    Log.d(Tag, c.getRemoteSocketAddress + " get message " + m);
    // only need to process closed event
  }

  override def onError(c: WebSocket, ex: Exception): Unit = {
    Log.d(Tag, c.getRemoteSocketAddress() + " exception " + ex)
  }

  def sendToAll(m: String) = this.synchronized {
    for (c ← asScalaSet(connections())) {
      try { c.send(m) } catch { case _: Throwable ⇒ }
    }
  }

  val queueReader = new Thread {
    override def run() = {
      while (!Thread.interrupted()) {
        try { sendToAll(Renderer.render(queue.take(), p).toJson.toString) }
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

    new Profile {
      import android.net.Uri
      import android.provider.ContactsContract.PhoneLookup
      import android.content.ContentResolver

      val addr = bindAddr
      def hideContent(): Boolean = {
        prefs.getBoolean(conf.hideContent, true)
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
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    val run = prefs.getBoolean(conf.serverEnabled, true)

    if (run) { nsStart() } else { nsStop() }

    if (i != null) {
      i.getSerializableExtra(ExtraData) match {
        case e: Event ⇒ queue.put(e)
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
