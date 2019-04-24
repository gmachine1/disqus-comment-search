create table Visit (
    city varchar(128),
    name varchar(128),
    ip varchar(128),
    commentDownloadLimit int,
    timestamp datetime not null,
    email varchar(128),
    username varchar(128),
    matchAll boolean,
    isp varchar(128),
    latency double,
    visitType int not null,
    query varchar(128),
    country varchar(128),
    formValidated boolean not null,
    id bigint primary key not null auto_increment,
    province varchar(128),
    message text,
    referrer varchar(128),
    foundUser boolean not null,
    numResults int
  )