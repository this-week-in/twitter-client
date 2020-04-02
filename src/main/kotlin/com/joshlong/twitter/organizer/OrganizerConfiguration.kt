package com.joshlong.twitter.organizer

import com.joshlong.twitter.TagResolver
import com.joshlong.twitter.TwitterPinboardOrganizerProperties
import com.joshlong.twitter.api.BearerTokenInterceptor
import com.joshlong.twitter.api.SimpleTwitterClient
import com.joshlong.twitter.api.TwitterClient
import org.apache.commons.logging.LogFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import pinboard.Bookmark
import pinboard.PinboardClient
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import java.util.concurrent.atomic.AtomicReference


/**
 * The goal of this program is to take a Pinboard
 * item that was saved and has the description.
 */
@Configuration
class OrganizerConfiguration(private val twitterOrganizerProperties: TwitterPinboardOrganizerProperties) {

	// todo extract out the RestTemplate and the BaseTwitterClient into a separate auto-configuration
	private val authenticatedRestTemplate = RestTemplate()
			.apply {
				val bearerTokenInterceptor = BearerTokenInterceptor(
						twitterOrganizerProperties.consumerKey, twitterOrganizerProperties.consumerSecret)
				interceptors.add(bearerTokenInterceptor)
			}

	@Bean
	fun twitterClient(twitterOrganizerProperties: TwitterPinboardOrganizerProperties): TwitterClient =
			SimpleTwitterClient(this.authenticatedRestTemplate)
}

@Component
class TwitterOrganizer(
		private val tagResolver: TagResolver,
		private val twitterClient: TwitterClient,
		private val pinboardClient: PinboardClient) :
		ApplicationListener<ApplicationReadyEvent> {

	private val log = LogFactory.getLog(javaClass)

	private fun enrichBookmarksFor(tag: String, starting: Date, until: Date) {
		log.info("Enriching the bookmarks for the tag $tag from date $starting until $until ")
		val tweetKey = "tweet"
		val bookmarkKey = "bookmark"
		this.pinboardClient
				.getAllPosts(tag = arrayOf(tag), todt = until, fromdt = starting)
				.filter { it.description!!.trim() == "twitter.com" }
				.filter { it.href!!.contains("/status/") }
				.map {
					val url = it.href!!
					var found = false
					val idStr = url.split("/status/")[1].filter {
						if (it == '?') {
							found = true
						}
						!found && Character.isDigit(it)
					}
					mapOf(tweetKey to java.lang.Long.parseLong(idStr), bookmarkKey to it)
				}
				.parallelStream()
				.forEach {
					log.info("processing on ${Thread.currentThread().name}")
					val bookmark = it[bookmarkKey] as Bookmark
					val tweet = this.twitterClient.getTweet(it[tweetKey] as Long)
					if (tweet != null) {
						log.info("updated the bookmark ${bookmark.href} to ${tweet.id} and ${tweet.text}")
						this.pinboardClient.updatePost(
								url = bookmark.href!!,
								description = tweet.text,
								extended = tweet.text,
								tags = bookmark.tags,
								dt = bookmark.time!!,
								shared = bookmark.shared,
								toread = bookmark.toread
						)
						log.info("updated the bookmark ${bookmark.href} to ${tweet.id} and ${tweet.text}")
					} else {
						log.info("the tweet was null")
					}
				}
	}

	private fun forEachNDaysBetween(begin: LocalDate, end: LocalDate, stepInDays: Int, handler: (date: LocalDate) -> Unit) {
		val iterator = LocalDateIterator(begin, end)
		var counter = 0
		iterator.forEachRemaining {
			if (counter % stepInDays == 0) {
				handler(it)
			}
			counter += 1
		}
	}

	private fun dateFromLocalDate(ld: LocalDate) = Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant())

	override fun onApplicationEvent(event: ApplicationReadyEvent) {
		val stepInDays: Int = 10
		val begin = LocalDate.of(2018, 1, 1)
		forEachNDaysBetween(begin, LocalDate.now(), stepInDays) { currentDate ->
			tagResolver.loadTags().forEach { tag ->
				enrichBookmarksFor(tag, dateFromLocalDate(currentDate), dateFromLocalDate(currentDate.plusDays(stepInDays.toLong())))
			}
		}
	}
}

class LocalDateIterator(start: LocalDate, private val stop: LocalDate) : Iterator<LocalDate> {
	private val current = AtomicReference(start.minusDays(1))
	override fun hasNext(): Boolean = this.current.get().isBefore(this.stop)
	override fun next(): LocalDate = this.current.updateAndGet { it.plusDays(1) }
}