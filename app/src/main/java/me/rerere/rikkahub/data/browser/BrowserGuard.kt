package me.rerere.rikkahub.data.browser

import me.rerere.rikkahub.data.datastore.BrowserSettings
import org.json.JSONObject
import java.net.URI

enum class BrowserRiskClass {
    MONEY,
    DESTRUCTIVE,
    OUTBOUND,
}

object BrowserTapWords {
    val secretField = Regex(
        "passw|pwd|密码|口令|verif|otp|一次性|验证码|校验码|动态码|短信码|sms.?code|auth.?code|security.?code|\\bpin\\b|captcha|图形码|cvv|cvc|安全码",
        RegexOption.IGNORE_CASE,
    )
    private val moneyTap = Regex(
        "支付|付款|立即购买|确认购买|去支付|确认支付|结算|下单|提交订单|立即下单|买单|充值|转账|打赏|购买|订阅|开通|续费|确认付款|立即支付|\\bpay\\b|pay now|payment|checkout|place order|buy now|purchase|(?<!un)subscribe|donate|transfer|top ?up|confirm order|complete order|confirm purchase|book now|reserve",
        RegexOption.IGNORE_CASE,
    )
    private val destructiveTap = Regex(
        "删除|移除|清空|注销|解绑|退订|取消订单|退款|\\bdelete\\b|\\bremove\\b|clear all|unsubscribe|deactivate|cancel order|\\brefund\\b|\\bdiscard\\b|\\berase\\b",
        RegexOption.IGNORE_CASE,
    )
    private val outboundTap = Regex(
        "^发送$|发送|发布|回复|投递|转发|评论|发表|发帖|提交|确认发送|\\bsend\\b|\\bpost\\b|\\bpublish\\b|\\breply\\b|\\bshare\\b|\\btweet\\b|\\bcomment\\b|\\bsubmit\\b|\\bapply\\b",
        RegexOption.IGNORE_CASE,
    )

    fun classify(label: String): BrowserRiskClass? = when {
        label.isBlank() -> null
        moneyTap.containsMatchIn(label) -> BrowserRiskClass.MONEY
        destructiveTap.containsMatchIn(label) -> BrowserRiskClass.DESTRUCTIVE
        outboundTap.containsMatchIn(label) -> BrowserRiskClass.OUTBOUND
        else -> null
    }

    fun reason(cls: BrowserRiskClass, short: String, where: String?): String = when (cls) {
        BrowserRiskClass.MONEY -> "taps \"$short\": looks like a payment"
        BrowserRiskClass.DESTRUCTIVE -> "taps \"$short\": looks like it deletes something"
        BrowserRiskClass.OUTBOUND -> "taps \"$short\": looks like it sends or posts"
    } + (where?.let { " on $it" } ?: "")
}

/**
 * Configurable safety guard for `browser_use` navigation, typing, and clicking.
 */
object BrowserGuard {
    const val HANDOFF_PREFIX = "[lastchat_handoff]"

    sealed class Verdict {
        data object Proceed : Verdict()
        data class Refuse(val text: String) : Verdict()
        data class Ask(
            val riskClass: BrowserRiskClass,
            val reason: String,
            val host: String?,
            val elementText: String,
        ) : Verdict()
    }

    data class Target(
        val found: Boolean,
        val tag: String,
        val type: String,
        val name: String,
        val id: String,
        val placeholder: String,
        val autocomplete: String,
        val inputMode: String,
        val aria: String,
        val label: String,
        val text: String,
        val href: String,
        val maxLength: Int,
        val url: String,
        val host: String,
    ) {
        companion object {
            fun parse(json: String): Target? = runCatching {
                val o = JSONObject(json)
                Target(
                    found = o.optBoolean("found", false),
                    tag = o.optString("tag"),
                    type = o.optString("type"),
                    name = o.optString("name"),
                    id = o.optString("id"),
                    placeholder = o.optString("placeholder"),
                    autocomplete = o.optString("autocomplete"),
                    inputMode = o.optString("inputmode"),
                    aria = o.optString("aria"),
                    label = o.optString("label"),
                    text = o.optString("text"),
                    href = o.optString("href"),
                    maxLength = o.optString("maxlength").toIntOrNull() ?: 0,
                    url = o.optString("url"),
                    host = o.optString("host"),
                )
            }.getOrNull()
        }
    }

    fun isDomainBlocked(url: String?, blockedDomains: List<String>): Boolean {
        if (url.isNullOrBlank() || blockedDomains.isEmpty()) return false
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull()
            ?: return false
        return blockedDomains
            .map { it.trim().lowercase().removePrefix("*.") }
            .filter { it.isNotEmpty() }
            .any { blocked -> host == blocked || host.endsWith(".$blocked") }
    }

    fun describeJs(selector: String?, x: Int?, y: Int?): String {
        val pick = when {
            selector != null -> "document.querySelector(${JSONObject.quote(selector)})"
            x != null && y != null -> "document.elementFromPoint($x, $y)"
            else -> "null"
        }
        return """
            (function(){
              var el = null; try { el = $pick; } catch (e) {}
              if (!el) return JSON.stringify({found:false, url: location.href, host: location.host});
              function s(v){ return ((v == null ? '' : v) + '').replace(/\s+/g,' ').trim().slice(0, 160); }
              var inp = el.closest ? (el.closest('input,textarea,select,[contenteditable=true],[contenteditable=""]') || el) : el;
              var btn = el.closest ? (el.closest('button,a,[role=button],[role=link],input[type=submit],input[type=button],input[type=image],[onclick],summary,label') || el) : el;
              var lbl = '';
              try { if (inp.labels && inp.labels[0]) lbl = inp.labels[0].innerText; else if (inp.id) { var l = document.querySelector('label[for="' + inp.id + '"]'); if (l) lbl = l.innerText; } } catch (e) {}
              var txt = btn.innerText || btn.value || btn.getAttribute('aria-label') || btn.getAttribute('title') || btn.getAttribute('alt') || '';
              if (!s(txt) && btn.querySelector) { var im = btn.querySelector('img[alt]'); if (im) txt = im.getAttribute('alt'); }
              return JSON.stringify({
                found: true, tag: el.tagName, type: s(inp.getAttribute && inp.getAttribute('type')).toLowerCase(),
                name: s(inp.getAttribute && inp.getAttribute('name')), id: s(inp.id), placeholder: s(inp.getAttribute && inp.getAttribute('placeholder')),
                autocomplete: s(inp.getAttribute && inp.getAttribute('autocomplete')).toLowerCase(), inputmode: s(inp.getAttribute && inp.getAttribute('inputmode')).toLowerCase(),
                aria: s(inp.getAttribute && inp.getAttribute('aria-label')), label: s(lbl), text: s(txt),
                href: s(btn.getAttribute && btn.getAttribute('href')), maxlength: s(inp.getAttribute && inp.getAttribute('maxlength')),
                url: location.href, host: location.host
              });
            })()
        """.trimIndent()
    }

    fun judgeType(t: Target, settings: BrowserSettings): Verdict {
        if (settings.fullAutonomyMode || !settings.autoHandoffOnSecretFields) {
            return Verdict.Proceed
        }
        if (!t.found) return Verdict.Proceed
        val looksSecret = t.type == "password" ||
            t.autocomplete in setOf("one-time-code", "current-password", "new-password", "cc-csc") ||
            listOf(t.name, t.id, t.placeholder, t.aria, t.label).any { BrowserTapWords.secretField.containsMatchIn(it) } ||
            (t.inputMode == "numeric" && t.maxLength in 4..8 && listOf(t.placeholder, t.aria, t.label).any {
                Regex("code|码", RegexOption.IGNORE_CASE).containsMatchIn(it)
            })
        if (!looksSecret) return Verdict.Proceed
        return Verdict.Refuse(
            "$HANDOFF_PREFIX Refused: that field looks like a password or verification code, and auto-handoff on " +
                "secret fields is enabled. The in-app browser has been brought forward — ask the user to enter it " +
                "there themselves and tell you when done, then continue from the page as it is.",
        )
    }

    fun judgeClick(t: Target, settings: BrowserSettings): Verdict {
        if (settings.fullAutonomyMode || !settings.requireApprovalForRiskyClicks) {
            return Verdict.Proceed
        }
        if (!t.found) return Verdict.Proceed
        val label = listOf(t.text, t.aria).firstOrNull { it.isNotBlank() } ?: return Verdict.Proceed
        val host = t.host.ifBlank { null }
        val cls = BrowserTapWords.classify(label) ?: return Verdict.Proceed
        val short = label.take(40)
        return Verdict.Ask(
            riskClass = cls,
            reason = BrowserTapWords.reason(cls, short, host),
            host = host,
            elementText = short,
        )
    }
}
