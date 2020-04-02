create table if not exists twitter_pinboard_tags
(
    id       serial       not null primary key,
    tag_name varchar(255) not null
);