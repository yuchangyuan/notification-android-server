package me.ycy.notification.server.android

import _root_.android.app.Activity
import _root_.android.os.Bundle

import android.content.Intent
import android.preference.PreferenceActivity
import android.preference.Preference

class MainActivity extends PreferenceActivity with TypedActivity {
  import NotificationService._

  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)

    addPreferencesFromResource(R.xml.preferences)

    val serverEnabled = findPreference(conf.serverEnabled)
    serverEnabled.setOnPreferenceChangeListener(
      new Preference.OnPreferenceChangeListener {
        def onPreferenceChange(p: Preference, v: Object) = {
          startStopService(MainActivity.this)
          true
        }
      }
    )

    startStopService(this)
  }
}
