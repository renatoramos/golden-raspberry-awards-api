package com.outsera.movies;

/** CSV inválido: aborta a inicialização da aplicação com uma mensagem que aponta a linha com problema. */
class InvalidCsvException extends IllegalStateException {

    InvalidCsvException(String message) {
        super(message);
    }
}
