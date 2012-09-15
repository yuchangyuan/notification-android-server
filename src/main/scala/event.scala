package me.ycy.notification.server.android

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.Context
import android.telephony.SmsMessage
import android.telephony.TelephonyManager

class SmsReceiver extends BroadcastReceiver {
  import NotificationService._

  override def onReceive(ctx: Context, i: Intent): Unit = {
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

  override def onReceive(ctx: Context, i: Intent): Unit = {
    val number = i.getStringExtra(EXTRA_INCOMING_NUMBER)
    i.getStringExtra(EXTRA_STATE) match {
      case EXTRA_STATE_RINGING ⇒
        queue.put(Call(number))
      // case EXTRA_STATE_OFFHOOK ⇒
      // case EXTRA_STATE_IDLE ⇒
      case _ ⇒
    }
  }
}
