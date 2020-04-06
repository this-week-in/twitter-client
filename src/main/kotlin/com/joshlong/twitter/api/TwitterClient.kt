package com.joshlong.twitter.api

interface TwitterClient {

	fun getFriends(profileId: Long): Iterator<User>

	/**
	 * Returns all the tweets for a given user, optionally
	 * restricting it to a last message received.
	 */
	fun getUserTimeline(username: String, sinceId: Long = -1): List<Tweet>

	/**
	 * Returns a particular user's profile
	 */
	fun getUserProfile(profileId: Long): User?

	/**
	 * Returns the details of a particular Tweet message
	 */
	fun getTweet(tweetId: Long): Tweet?

}