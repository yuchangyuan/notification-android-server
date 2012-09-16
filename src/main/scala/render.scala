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
    val name = p.queryName(from)

    if (p.hideContent()) {
      title = "New SMS"
      val src = name match {
        case None ⇒ <span><strong>{from}</strong></span>
        case Some(n) ⇒ <span><strong>{n}</strong> <em>{from}</em></span>
      }
      body = "<div class='collapsed'><div>...</div>" +
        "<div>" + src + "<br/>" + text +
        "</div></div>"
    }
    else {
      title = name match {
        case None ⇒ "SMS from " + <strong>{from}</strong>
        case Some(n) ⇒ "SMS from " + <strong>{n}</strong>
      }

      body = name match {
        case None ⇒ text
        case Some(_) ⇒ {
          "<div><div><em>" +
          from +
          "</em><div>" + text + "</div></div>"
        }
      }
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
