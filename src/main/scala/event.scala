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

  override def onReceive(ctx: Context, i: Intent): Unit = {
    Log.d(Tag, "get sms")

    val bundle = i.getExtras()
    val pdus = bundle.get("pdus").asInstanceOf[Array[Object]]
    for (i ← 0 until pdus.size) {
      val msg = SmsMessage.createFromPdu(pdus(i).asInstanceOf[Array[Byte]])
      val body = msg.getDisplayMessageBody
      val from = msg.getDisplayOriginatingAddress

      queue.put(Sms(from, body))
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
