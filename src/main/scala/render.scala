package me.ycy.notification.server.android

import me.ycy.notification.api._

object Renderer {
  import NotificationService._

  def render(e: Event, p: Profile): CreateCommand = e match {
    case Sms(from, text) ⇒ renderSms(from, text, p)
    case Call(from) ⇒ renderCall(from, p)
  }

  def renderSms(from: String, text: String, p: Profile) = {
    var title: String = null
    var body: String = null

    if (p.hideContent()) {
      title = "New SMS"
      body = "<div class='collapsed'><div>...</div>" +
        "<div>" + from + "<br/>" + text +
        "</div></div>"
    }
    else {
      title = "SMS from " + from
      body = text
    }

    CreateCommand(title = title, body = body, client = "android")
  }

  def renderCall(from: String, p: Profile) = {
    var title: String = null
    var body: String = null

    if (p.hideContent()) {
      title = "Incoming call"
      body =  "<div class='collapsed'><div>...</div>" +
        "<div>call from " + from +
        "</div></div>"
    }
    else {
      title = "Incoming call from " + from
      body = ""
    }

    CreateCommand(title = title, body = body, client = "android")
  }
}
