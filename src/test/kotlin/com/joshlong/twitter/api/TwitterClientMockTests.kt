package com.joshlong.twitter.api

import org.apache.commons.logging.LogFactory
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.web.client.RestTemplate
import java.io.InputStreamReader


@Ignore // todo i need to replace this with something using MockRestService or WireMock
class TwitterClientMockTests {

	private val fileTweetJsonProducer: (String, Long) -> String = { _, _ ->
		tweetsJsonFile.inputStream.use { inputStream ->
			InputStreamReader(inputStream).use {
				it.readText()
			}
		}
	}

	private val log = LogFactory.getLog(javaClass)
	private val tweetsJsonFile = ClassPathResource("/tweets.json")
	private val twitterClient = SimpleTwitterClient(RestTemplate())
	private val username = "starbuxman"
	private val timeline: List<Tweet>

	init {
		this.timeline = this.twitterClient.getUserTimeline(this.username).apply {
			forEach { log.info (it) }
		}
	}

	@Test
	fun `more than one record`() {
		Assert.assertTrue(timeline.isNotEmpty())
	}

	@Test
	fun `some tweets should have hashtags`() {
		Assert.assertTrue("there should be at least one tweet with hashtags",
				timeline.filter { it.entities.hashtags.isNotEmpty() }.isNotEmpty())
	}

	@Test
	fun `the tweets should be by me`() {
		Assert.assertTrue("the username for each tweet should be $username", timeline.filter { it.user.screenName == username }.isNotEmpty())
	}

	@Test
	fun `some tweets should have mentions`() {
		Assert.assertTrue("there must be at least one entity with more than 0 mentions involved",
				timeline.filter { it.entities.userMentions.isNotEmpty() }.isNotEmpty())
	}
}
