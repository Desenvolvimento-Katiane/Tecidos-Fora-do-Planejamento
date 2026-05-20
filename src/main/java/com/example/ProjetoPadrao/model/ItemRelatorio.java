package com.example.ProjetoPadrao.model;

public record ItemRelatorio(
        String modelo,
        String codigoSystextil,
        String descricaoSystextil,
        int totalAprovacaoTecido,
        String aprovCont,
        int totalModeloSomado,
        int diferenca,
        boolean semPlanejamento,
        String marcas,
        String linha) {
}
