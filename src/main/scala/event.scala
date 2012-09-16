package me.ycy.notification.server.android

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.Context
import android.telephony.SmsMessage
import android.telephony.TelephonyManager
import android.util.Log

class SmsReceiver extends BroadcastReceiver {
  import NotificationService._

  val Tag = "NsSms"

  def retrieveSms(i: Intent): List[Sms] = {
    var ret = List[(String, StringBuilder, Long)]()

    val bundle = i.getExtras()
    val pdus = bundle.get("pdus").asInstanceOf[Array[Object]]

    if (pdus == null) return List()

    for (i ← 0 until pdus.size) {
      val msg = SmsMessage.createFromPdu(pdus(i).asInstanceOf[Array[Byte]])

      if (msg != null) {
        Log.d(Tag, "sms[" + i + "] @ " + msg.getTimestampMillis())

        val body = msg.getDisplayMessageBody
        val from = msg.getDisplayOriginatingAddress
        val ts = msg.getTimestampMillis

        if ((ret.size > 0) && (ret.head._1 == from) &&
            (ret.head._3 == ts)) {
          ret.head._2 ++= body
        }
        else {
          ret ::= (from, new StringBuilder(body), ts)
        }
      }
    }

    ret.reverse.map(x ⇒ Sms(x._1, x._2.mkString))
  }

  override def onReceive(ctx: Context, i: Intent): Unit = {
    Log.d(Tag, "get sms")

    val smss = retrieveSms(i)

    for (sms ← smss) {
      queue.put(sms)
    }
  }
}

class BootReceiver extends BroadcastReceiver {
  override def onReceive(ctx: Context, i: Intent): Unit = {
    NotificationService.startStopService(ctx)
  }
}

class PhoneStateReceiver extends BroadcastReceiver {
  import TelephonyManager._
  import NotificationService._

  val Tag = "NsPhone"

  override def onReceive(ctx: Context, i: Intent): Unit = {
    val number = i.getStringExtra(EXTRA_INCOMING_NUMBER)
    val state = i.getStringExtra(EXTRA_STATE)
    Log.d(Tag, "phone state change " + state)

    state match {
      case EXTRA_STATE_RINGING ⇒
        queue.put(Call(number))
      // case EXTRA_STATE_OFFHOOK ⇒
      // case EXTRA_STATE_IDLE ⇒
      case _ ⇒
    }
  }
}
