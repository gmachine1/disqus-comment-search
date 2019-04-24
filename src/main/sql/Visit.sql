{\rtf1\ansi\ansicpg936\cocoartf1561\cocoasubrtf600
{\fonttbl\f0\fswiss\fcharset0 Helvetica;}
{\colortbl;\red255\green255\blue255;}
{\*\expandedcolortbl;;}
\margl1440\margr1440\vieww10800\viewh8400\viewkind0
\pard\tx720\tx1440\tx2160\tx2880\tx3600\tx4320\tx5040\tx5760\tx6480\tx7200\tx7920\tx8640\pardirnatural\partightenfactor0

\f0\fs24 \cf0 create table Visit (\
    city varchar(128),\
    name varchar(128),\
    ip varchar(128),\
    commentDownloadLimit int,\
    timestamp datetime not null,\
    email varchar(128),\
    username varchar(128),\
    matchAll boolean,\
    isp varchar(128),\
    latency double,\
    visitType int not null,\
    query varchar(128),\
    country varchar(128),\
    formValidated boolean not null,\
    id bigint primary key not null auto_increment,\
    province varchar(128),\
    message text,\
    referrer varchar(128),\
    foundUser boolean not null,\
    numResults int\
  );}