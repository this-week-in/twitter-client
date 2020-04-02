package com.joshlong.twitter

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties("twitter.organizer")
class TwitterPinboardOrganizerProperties(val consumerKey: String, val consumerSecret: String)