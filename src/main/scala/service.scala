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

import me.ycy.notification.api._

object NotificationService {
  sealed trait Event
  case class Sms(from: String, text: String) extends Event
  case class Call(from: String) extends Event

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

  trait Profile {
    val addr: InetSocketAddress
    def hideContent(): Boolean
  }
}

class NotificationServer(p: NotificationService.Profile)
extends WebSocketServer(p.addr) {
  import NotificationService._

  override def onOpen(c: WebSocket, handshake: ClientHandshake): Unit = {
    System.out.println(c + " connected" )
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
    println(c + ": " + m);
    // only need to process closed event
  }

  override def onError(c: WebSocket, ex: Exception): Unit = {
    println(c + "exception " + ex)
  }

  def sendToAll(m: String) = this.synchronized {
    for (c ← asScalaSet(connections())) {
      try { c.send(m) } catch { case _: Throwable ⇒ }
    }
  }

  private def notifySms(sms: Sms): Unit = {
    val cc = CreateCommand(
      client = "android",
      title = "SMS from " + sms.from,
      body = sms.text
    )
    sendToAll(cc.toJson.toString)
  }

  private def notifyCall(call: Call): Unit = {
    val cc = CreateCommand(
      client = "android",
      title = "Phone call from " + call.from
    )
    sendToAll(cc.toJson.toString)
  }

  private def notifyEvent(e: Event): Unit = e match {
    case sms: Sms ⇒ notifySms(sms)
    case call: Call ⇒ notifyCall(call)
  }


  val queueReader = new Thread {
    override def run() = {
      while (!Thread.interrupted()) {
        try { notifyEvent(queue.take()) }
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
        getText(R.string.service_stopped).toString)
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
      val addr = bindAddr
      def hideContent(): Boolean = {
        prefs.getBoolean(conf.hideContent, true)
      }
    }
  }

  //  ------------------- notification -------------------
  val NotificationId = R.string.service_started

  lazy val nm: NotificationManager =
    getSystemService(Context.NOTIFICATION_SERVICE).
    asInstanceOf[NotificationManager]

  private def showNotification(scroll: String, title: String, body: String) {
    val n = new Notification(
      R.drawable.app_n, scroll,
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

    Service.START_STICKY
  }


  val binder = new Binder {
    def getService() = NotificationService.this;
  }

  override def onBind(i: Intent) = binder
}
