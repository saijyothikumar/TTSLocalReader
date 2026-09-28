package com.tts.reader.data.scraper

class CaptchaChallengeException(
    val url: String,
    val reason: String = "Cloudflare or bot protection challenge detected."
) : Exception("Captcha / Bot Protection detected for $url: $reason")
