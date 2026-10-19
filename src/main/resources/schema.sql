-- Schema do módulo movies. Só chaves primárias: índices adicionais só entram com medição.
create table movie (
    id           bigint       not null,
    release_year int          not null,
    title        varchar(512) not null,
    studios      varchar(512),
    winner       boolean      not null,
    primary key (id)
);

create table movie_producer (
    movie_id bigint       not null,
    producer varchar(255) not null,
    primary key (movie_id, producer)
);
