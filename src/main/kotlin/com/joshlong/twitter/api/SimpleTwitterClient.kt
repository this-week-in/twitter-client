package com.joshlong.twitter.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.logging.LogFactory
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.lang.Boolean
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * A client to read tweets from a given user's timeline.
 *
 * @author Josh Long
 * @see <a href="https://developer.twitter.com/en/docs/basics/authentication/oauth-2-0/bearer-tokens">how to do OAuth authentication for Twitter's API</a>
 * @see <a href="https://developer.twitter.com/en/docs/tweets/timelines/com.joshlong.twitter.api-reference/get-statuses-user_timeline">the API for a user's timeline</a>
 */
open class SimpleTwitterClient(private val restTemplate: RestTemplate) : TwitterClient {

//	init {
//		TODO("need to implement follow(profileId)")
//	}

	private val log = LogFactory.getLog(SimpleTwitterClient::class.java)
	private val formatterString = "EEE MMM d HH:mm:ss ZZ yyyy"
	private val objectMapper = ObjectMapper()
	private val formatter = SimpleDateFormat(formatterString)

	override fun getFriends(profileId: Long): Iterator<User> {
		val url = "https://api.twitter.com/1.1/friends/list.json?user_id=${profileId}&count=200"
		return TwitterApiCursorIterator<User>(
				restTemplate = this.restTemplate,
				initialCollection = { mutableListOf() },
				url = url,
				mapper = { jsonNode -> jsonNode["users"].map { buildUser(it) } },
				shouldContinue = { true }
		)
	}


	override fun getUserProfile(profileId: Long): User {
		val userUrl = "https://api.twitter.com/1.1/users/show.json?user_id=${profileId}"
		val json = this.restTemplate.getForEntity(userUrl, JsonNode::class.java).body!!
		return buildUser(json)
	}

	override fun getUserTimeline(username: String, sinceId: Long): List<Tweet> {
		val userTimelineUrl = "https://api.twitter.com/1.1/statuses/user_timeline.json"
		val sinceIdParam = if (sinceId > 0) "&since_id=${sinceId}" else ""
		val uri = "${userTimelineUrl}?include_rts=1&tweet_mode=extended&count=200&screen_name=${username}${sinceIdParam}"
		return parseJson(restTemplate.getForEntity(uri, String::class.java).body!!)
	}

	// todo incorporate the rate limiter insight when returning requests.
	//  \for now the trick is to only run the SI poller every 15 minutes,
	//  \which is the rate limiter window time anyway

/* private fun getRateLimiterStatusForUserTimeline() =
			getRateLimiterStatusForFamily("statuses", "/statuses/user_timeline")

	private fun getRateLimiterStatusForFamily(family: String, endpoint: String): RateLimitStatus {
		val json = rateLimitStatusProducer()
		val jsonNode = objectMapper.readTree(json)
		val rlJson = jsonNode["resources"][family][endpoint]
		val limit = rlJson["limit"].intValue()
		val remaining = rlJson["remaining"].intValue()
		val reset = rlJson["reset"].longValue()
		return RateLimitStatus(limit, remaining, reset)
	}*/

	private fun <T> collectionFromAttribute(json: JsonNode, attribute: String, extractor: (JsonNode) -> T): List<T> =
			if (!json.has(attribute)) emptyList() else json[attribute].map { extractor(it) }

	private fun buildUserMentions(json: JsonNode) = collectionFromAttribute(json, "user_mentions") {
		UserMention(it["screen_name"].textValue(), it["name"].textValue(), java.lang.Long.parseLong(it["id_str"].textValue()))
	}

	private fun buildHashtags(json: JsonNode) = collectionFromAttribute(json, "hashtags") { Hashtag(it["text"].textValue()) }

	private fun buildUrls(json: JsonNode) = collectionFromAttribute(json, "urls") { URL(it["expanded_url"].textValue()) }

	private fun buildEntities(json: JsonNode) = Entities(buildHashtags(json), buildUserMentions(json), buildUrls(json))

	private fun buildUser(jsonNode: JsonNode): User {
		val url: String? = jsonNode["url"]?.textValue()
		return User(
				java.lang.Long.parseLong(jsonNode["id_str"].textValue()),
				jsonNode["name"].textValue(),
				jsonNode["screen_name"].textValue(),
				jsonNode["location"].textValue(),
				jsonNode["description"].textValue(),
				if (url != null) URL(url) else null
		)
	}

	private fun log(msg: String) {
		if (log.isDebugEnabled) log.debug(msg)
	}

	override fun getTweet(tweetId: Long): Tweet? =
			try {
				restTemplate
						.getForEntity("https://api.twitter.com/1.1/statuses/show.json?id={id}&tweet_mode=extended", String::class.java, tweetId)
						.let {
							if (it.statusCode.is2xxSuccessful) {
								return it.body!!.let { buildTweet(objectMapper.readTree(it)) }
							} else {
								null
							}
						}
			} catch (ex: HttpClientErrorException) {
				log.error("got an exception (of type ${ex.javaClass.name} when processing tweetId ${tweetId}", ex)
				null
			}

	private fun buildTweet(tweetNode: JsonNode): Tweet {

		val createdAt: Date =
				if (tweetNode.has("created_at")) {
					val textValue = tweetNode["created_at"].textValue()
					try {
						synchronized(this.formatter) {
							formatter.parse(textValue)
						}
					} catch (ex: Exception) {
						log("couldn't parse $textValue!")
						Date()
					}
				} else {
					log("there is no date!")
					Date()
				}

		return Tweet(
				createdAt,
				java.lang.Long.parseLong(tweetNode["id_str"].textValue()),
				tweetNode["full_text"].textValue(),
				Boolean.parseBoolean(tweetNode["truncated"].textValue()),
				tweetNode["in_reply_to_status_id_str"].textValue(),
				buildEntities(tweetNode["entities"]),
				buildUser(tweetNode["user"])
		)
	}

	private fun parseJson(json: String): List<Tweet> {
		val tweets = mutableListOf<Tweet>()
		val jsonNode: JsonNode = objectMapper.readTree(json)
		jsonNode.forEach { tweets.add(buildTweet(it)) }
		return tweets
	}
}

/**
 * TODO support rate limiting
 */
class TwitterApiCursorIterator<T>(
		private val restTemplate: RestTemplate,
		private val url: String,
		private val initialCollection: () -> List<T> = { listOf() },
		private val mapper: (JsonNode) -> List<T>,
		private val shouldContinue: (cursor: Long) -> kotlin.Boolean
) : Iterator<T> {

	private val log = LogFactory.getLog(javaClass)
	private val cursor = AtomicLong(-1)
	private val queue = LinkedBlockingQueue<T>().apply { addAll(initialCollection()) }
	private val monitor = Object()
	private val lastRefreshSize = AtomicLong(-1)

	override fun next(): T {
		synchronized(this.monitor) {
			val cursor: Long = this.cursor.get()
			if (this.queue.isEmpty()) {
				val urlWithCursor = "${url}&cursor=${cursor}"
				val response = restTemplate.getForEntity(urlWithCursor, JsonNode::class.java).body!!
				val results: List<T> = mapper(response)
				this.cursor.set(response.get("next_cursor").longValue())
				this.queue.addAll(results)
				this.lastRefreshSize.set(results.size * 1L)
			}
			return queue.poll()
		}
	}

	override fun hasNext(): kotlin.Boolean =
			synchronized(this.monitor) {
				return (cursor.get() > 0 || queue.size > 0 || cursor.get() == -1L)
			}
}