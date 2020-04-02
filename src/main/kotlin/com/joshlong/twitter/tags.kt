package com.joshlong.twitter

import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component


interface TagResolver {
	fun loadTags(): List<String>
}

@Component
@Profile("ci", "default")
class DefaultTagResolver(private val environment: Environment) : TagResolver {

	companion object {
		const val TAGS_KEY = "twitter.organizer.tags"
		const val TAGS_DELIM = ","
	}

	override fun loadTags() = mutableListOf<String>()
			.apply {
				if (environment.containsProperty(TAGS_KEY)) {
					environment.getProperty(TAGS_KEY)?.let { tags ->
						if (tags.contains(TAGS_DELIM)) {
							addAll(tags.split(TAGS_DELIM).map { it.trim() })
						} else {
							add(tags.trim())
						}
					}
				}
			}

}

@Component
@Profile("cloud")
class JdbcTagResolver(private val jdbcTemplate: JdbcTemplate) : TagResolver {

	override fun loadTags(): List<String> =
			this.jdbcTemplate.query("select * from twitter_pinboard_tags") { rs, _ -> rs.getString("tag_name") }
}