package com.example.ProjetoPadrao.model;

public record TecidoPlanejado(
        String modelo,
        String codigoSystextil,
        String codigoNormalizado,
        String descricaoSystextil,
        int totalAprovacaoTecido,
        String aprovCont,
        String linha) {
}
