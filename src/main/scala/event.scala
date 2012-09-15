package me.ycy.notification.server.android

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.Context
import android.telephony.SmsMessage

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
