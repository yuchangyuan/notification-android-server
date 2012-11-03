package me.ycy.notification.server.android

import java.util.Date
import java.text.SimpleDateFormat
import me.ycy.notification.api._

import android.util.Log

object Renderer {
  val Tag = "ns.Renderer"

  import NotificationService._

  def render(e: RenderEvent, p: Profile): CreateCommand = e match {
    case Sms(from, text, time) ⇒ renderSms(from, text, time, p)
    case Call(from) ⇒ renderCall(from, p)
  }

  def renderSms(from: String, text: String, time: Long, p: Profile) = {
    var title: String = null
    var body: String = null
    val name = p.queryName(from)
    val date = new Date(time)
    val fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    val iconStyle = List(
      "width:48px;",
      "height:48px;",
      "float:left;",
      "position:relative;",
      "left:-0.5em;",
      "padding:0px;"
    ).mkString

    val hr = List(
      "<hr style='",
      "border:none;",
      "height:1px;",
      "background:rgba(128,128,128,0.4);",
      "' />"
    ).mkString

    val phNo = <span style="color:blue;">{from}</span>

    // small size date1
    val date1 = <span style="font-size:0.75em">
      <em>{fmt.format(date)}</em></span>

    val iconDiv = <div style={iconStyle}>
      <img src="icons/android/smsmms.svg"
           style='max-width:100%;max-height:100%;' />
      </div>


    if (p.hideContent()) {
      title = "New SMS " + date1
      val src = name match {
        case None ⇒ <span>{phNo}</span>
        case Some(n) ⇒ <span>{n} {phNo}</span>
      }
      body = "<div class='collapsed'><div>...</div>" +
        "<div>" + src + "<br/>" +
        hr +
        text.replaceAll("\n", "<br />") +
        "</div></div>"
    }
    else {
      title = name match {
        case None ⇒ "SMS from " + phNo
        case Some(n) ⇒ "SMS from " + n
      }

      body = "<div>" + date1 + "</div>" +
        hr + "<div>" + text.replaceAll("\n", "<br />") + "</div>"
      if (name.isDefined) { body = "<div>" + phNo + "</div>" + body }
    }

    title = iconDiv + title
    body = "<div style='position:relative;left:-1em;min-height:0.5em;'>" +
      body + "</div>"

    CreateCommand(title = title, body = body, client = "android")
  }

  def renderCall(from: String, p: Profile) = {
    var title: String = null
    var body: String = null
    val name = p.queryName(from)

    val phNo = <span style="color:blue;">{from}</span>

    if (p.hideContent()) {
      title = "Incoming call"
      val src = name match {
        case None ⇒ "call from " + phNo
        case Some(n) ⇒ "call from " + n + "<br />" + phNo
      }
      body = "<div class='collapsed'><div>...</div>" +
            "<div>" + src + "</div>" +
            "</div>"
    }
    else {
      name match {
        case None ⇒ {
          title = "Incoming call from " + phNo
          body = ""
        }
        case Some(n) ⇒ {
          title = "Incoming call from " + n
          body = phNo.toString
        }
      }
    }

    CreateCommand(title = title, body = body, client = "android")
  }
}
