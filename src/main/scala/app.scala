package me.ycy.notification.server.android

import _root_.android.app.Activity
import _root_.android.os.Bundle

import android.content.Intent

class MainActivity extends Activity with TypedActivity {
  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)
    setContentView(R.layout.main)

    findView(TR.textview).setText("hello, world!")

    // start server
    val i: Intent = new Intent(this, classOf[NotificationService])
    i.putExtra(NotificationService.Run, true)
    startService(i)
  }
}
