package com.joshlong.twitter

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "twitter")
data class TwitterClientProperties(val consumerKey: String, val consumerSecret: String)