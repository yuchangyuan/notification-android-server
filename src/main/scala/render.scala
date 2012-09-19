package me.ycy.notification.server.android

import java.util.Date
import java.text.SimpleDateFormat
import me.ycy.notification.api._

import android.util.Log

object Renderer {
  val Tag = "ns.Renderer"

  import NotificationService._

  def render(e: Event, p: Profile): CreateCommand = e match {
    case Sms(from, text, time) ⇒ renderSms(from, text, time, p)
    case Call(from) ⇒ renderCall(from, p)
  }

  def renderSms(from: String, text: String, time: Long, p: Profile) = {
    var title: String = null
    var body: String = null
    val name = p.queryName(from)
    val date = new Date(time)
    val fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    if (p.hideContent()) {
      title = "New SMS (<em>" + fmt.format(date) + "</em>)"
      val src = name match {
        case None ⇒ <span><strong>{from}</strong></span>
        case Some(n) ⇒ <span><strong>{n}</strong> <em>{from}</em></span>
      }
      body = "<div class='collapsed'><div>...</div>" +
        "<div>" + src + "<br/>" +
        "<em>" + date + "</em><br/>" +
        "<hr style='border-width:1px;'>" +
        text.replaceAll("\n", "<br />") +
        "</div></div>"
    }
    else {
      title = name match {
        case None ⇒ "SMS from " + <strong>{from}</strong>
        case Some(n) ⇒ "SMS from " + <strong>{n}</strong>
      }

      body = name match {
        case None ⇒ {
          "<div><em>" + date + "</em></div>" +
          "<hr style='border-width:1px;'>" +
          "<div>" + text.replaceAll("\n", "<br />") + "</div>"
        }
        case Some(_) ⇒ {
          "<div>" + from + "</div>" +
          "<div><em>" + date + "</em></div>" +
          "<hr style='border-width:1px;'>" +
          "<div>" + text.replaceAll("\n", "<br />") + "</div>"
        }
      }
    }

    CreateCommand(title = title, body = body, client = "android")
  }

  def renderCall(from: String, p: Profile) = {
    var title: String = null
    var body: String = null
    val name = p.queryName(from)

    if (p.hideContent()) {
      title = "Incoming call"
      val src = name match {
        case None ⇒ "call from <em>" + from + "</em>"
        case Some(n) ⇒ "call from <strong>" + n + "</strong><br />" +
          "<em>" + from + "</em>"
      }
      body = "<div class='collapsed'><div>...</div>" +
            "<div>" + src + "</div>" +
            "</div>"
    }
    else {
      name match {
        case None ⇒ {
          title = "Incoming call from <em>" + from + "</em>"
          body = ""
        }
        case Some(n) ⇒ {
          title = "Incoming call from <strong>" + n + "</strong>"
          body = "<em>" + from + "</em>"
        }
      }
    }

    CreateCommand(title = title, body = body, client = "android")
  }
}
