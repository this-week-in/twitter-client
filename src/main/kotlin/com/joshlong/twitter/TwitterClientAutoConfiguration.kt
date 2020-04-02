package com.joshlong.twitter

import com.joshlong.twitter.api.BearerTokenInterceptor
import com.joshlong.twitter.api.SimpleTwitterClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate


@Configuration
@EnableConfigurationProperties(TwitterClientProperties::class)
class TwitterClientAutoConfiguration(
		private val twitterClientProperties: TwitterClientProperties) {

	private val authenticatedRestTemplate = RestTemplate()
			.apply {
				val bearerTokenInterceptor = BearerTokenInterceptor(
						twitterClientProperties.consumerKey, twitterClientProperties.consumerSecret)
				interceptors.add(bearerTokenInterceptor)
			}

	@Bean
	@ConditionalOnMissingBean
	fun twitterClient() = SimpleTwitterClient(this.authenticatedRestTemplate)
}
