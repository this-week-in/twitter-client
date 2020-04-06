package com.joshlong.twitter.api

import org.apache.commons.logging.LogFactory
import org.junit.Assert
import org.junit.Test
import org.junit.jupiter.api.Disabled
import org.springframework.web.client.RestTemplate
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors
import kotlin.streams.asSequence
import kotlin.streams.asStream

@Disabled
class TwitterClientIntegrationTests {

	private val log = LogFactory.getLog(javaClass)
	private val authenticatedRestTemplate =
			RestTemplate()
					.apply {
						val mutableMap = System.getenv()
						fun keyIfItExists(key: String): String = if (mutableMap.containsKey(key)) mutableMap[key]!!.trim() else ""
						val apiKey = keyIfItExists("TWITTER_CONSUMER_KEY")
						val apiKeySecret = keyIfItExists("TWITTER_CONSUMER_SECRET")
						interceptors.add(BearerTokenInterceptor(apiKey, apiKeySecret))
					}

	private val twitterClient = SimpleTwitterClient(authenticatedRestTemplate)

	private val profileId = 4710974593


	@Test
	fun `get all the friends for a user`() {
		val counter = AtomicInteger()
		val friends = this.twitterClient.getFriends(this.profileId)
		friends.forEach {
			counter.incrementAndGet()
			println(it)
		}
		Assert.assertTrue(counter.get() > 0L)
	}

	@Test
	fun `should be able to get tweets from other users`() {
		val username = "SpringCentral"
		val timeline = this.twitterClient.getUserTimeline(username)
		Assert.assertTrue(timeline.isNotEmpty())
		timeline.forEach {
			log.info(it)
		}
	}

	@Test
	fun `get a user profile `() {
		val userForProfile = this.twitterClient.getUserProfile(this.profileId)
		Assert.assertEquals(userForProfile.screenName, "honeycombio")
	}

	@Test
	fun `should be able to get a single Tweet`() {

		fun normalizeText(txt: String) =
				txt
						.toLowerCase()
						.chars()
						.filter { Character.isAlphabetic(it) || Character.isDigit(it) }
						.mapToObj { it.toChar().toString() }
						.asSequence()
						.asStream()
						.collect(Collectors.joining())
						.trim()


		val tweet = this.twitterClient.getTweet(1244080159726039041)!!
		println("${tweet.id} ${tweet.text}")
		val message =
				"""
			Alright, this SUCKS. @robdaemon  is one of the most brilliant and hardest working (and friendliest!) 
			engineers I’ve ever met. PLEASE hire him, someone? He’s an engineering leader, a great manager, and one of the 
			most well rounded, crafty and dependable engineers out there.
		""".trimIndent()
		println("tweet text: ${normalizeText(tweet.text)}")
		println("message text: ${normalizeText(message)}")
		Assert.assertTrue(normalizeText(tweet.text).contains(normalizeText(message)))

		Assert.assertTrue(tweet.entities.userMentions.isNotEmpty())
		Assert.assertTrue(tweet.entities.urls.isNotEmpty())
		Assert.assertTrue(tweet.inReplyToStatusId == null)
		Assert.assertTrue(tweet.user.screenName == "starbuxman")
	}

}