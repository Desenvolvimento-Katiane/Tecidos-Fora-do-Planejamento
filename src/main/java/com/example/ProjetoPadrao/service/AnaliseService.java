package com.example.ProjetoPadrao.service;

import com.example.ProjetoPadrao.model.ItemRelatorio;
import com.example.ProjetoPadrao.model.ResultadoAnalise;
import com.example.ProjetoPadrao.model.TecidoPlanejado;
import com.example.ProjetoPadrao.model.TecidoUtilizado;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.Normalizer;
import java.util.*;

@Service
public class AnaliseService {

    private static final List<String> MARCAS_VALIDAS = List.of(
        "Animê Kids", "Animê Petite", "Animê Bebê",
        "Momi Kids",  "Momi Bebê",   "Momi Mini",
        "Authoria",   "Youccie",
        "Bimbi Menina", "Bimbi Menino"
    );

    public List<String> getMarcasValidas() {
        return MARCAS_VALIDAS;
    }

    private static String normalizarTexto(String s) {
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
    }

    private static Set<String> extrairMarcas(String rawMarca) {
        if (rawMarca == null || rawMarca.isBlank()) return Collections.emptySet();
        String inputNorm = normalizarTexto(rawMarca);
        Set<String> encontradas = new LinkedHashSet<>();
        for (String marca : MARCAS_VALIDAS) {
            if (inputNorm.contains(normalizarTexto(marca))) {
                encontradas.add(marca);
            }
        }
        if (encontradas.isEmpty()) {
            encontradas.add(rawMarca.trim());
        }
        return encontradas;
    }

    @Autowired
    private ExcelService excelService;

    public ResultadoAnalise analisar() throws IOException {
        List<TecidoPlanejado> planejados = excelService.lerPlanilha1();
        List<TecidoUtilizado> utilizados = excelService.lerPlanilha2();

        // código normalizado → TecidoPlanejado (primeira ocorrência em caso de duplicata)
        Map<String, TecidoPlanejado> mapPlanejado = new LinkedHashMap<>();
        for (TecidoPlanejado tp : planejados) {
            mapPlanejado.putIfAbsent(tp.codigoNormalizado(), tp);
        }

        // código normalizado → Set de modelos distintos (normalizados)
        Map<String, Set<String>> mapUtilizados = new LinkedHashMap<>();
        Map<String, Set<String>> mapMarcas = new LinkedHashMap<>();
        for (TecidoUtilizado tu : utilizados) {
            String modeloNorm = tu.modelo().trim().toLowerCase();
            if (!modeloNorm.isBlank()) {
                mapUtilizados
                        .computeIfAbsent(tu.codigoNormalizado(), k -> new LinkedHashSet<>())
                        .add(modeloNorm);
            }
            for (String marca : extrairMarcas(tu.marca())) {
                mapMarcas
                        .computeIfAbsent(tu.codigoNormalizado(), k -> new LinkedHashSet<>())
                        .add(marca);
            }
        }

        // Identificar excessos: total utilizado > total aprovado
        List<ItemRelatorio> excessos = new ArrayList<>();
        for (Map.Entry<String, TecidoPlanejado> entry : mapPlanejado.entrySet()) {
            String codigo = entry.getKey();
            TecidoPlanejado tp = entry.getValue();
            int totalUtilizado = mapUtilizados.getOrDefault(codigo, Collections.emptySet()).size();
            if (totalUtilizado > tp.totalAprovacaoTecido() && tp.totalAprovacaoTecido() > 0) {
                String marcas = String.join(", ", mapMarcas.getOrDefault(codigo, Collections.emptySet()));
                excessos.add(new ItemRelatorio(
                        tp.modelo(),
                        tp.codigoSystextil(),
                        tp.descricaoSystextil(),
                        tp.totalAprovacaoTecido(),
                        tp.aprovCont(),
                        totalUtilizado,
                        totalUtilizado - tp.totalAprovacaoTecido(),
                        false,
                        marcas, ""
                ));
            }
        }
        excessos.sort(Comparator.comparingInt(ItemRelatorio::diferenca).reversed());

        // Identificar códigos da Planilha 2 sem registro na Planilha 1
        List<ItemRelatorio> semPlanejamento = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : mapUtilizados.entrySet()) {
            String codigo = entry.getKey();
            if (!mapPlanejado.containsKey(codigo)) {
                String codigoOriginal = utilizados.stream()
                        .filter(u -> u.codigoNormalizado().equals(codigo))
                        .map(TecidoUtilizado::codigoSystextil)
                        .findFirst()
                        .orElse(codigo);
                semPlanejamento.add(new ItemRelatorio(
                        "", codigoOriginal, "", 0, "",
                        entry.getValue().size(), entry.getValue().size(), true, "", ""
                ));
            }
        }
        semPlanejamento.sort(Comparator.comparingInt(ItemRelatorio::totalModeloSomado).reversed());

        // Identificar códigos da Planilha 1 que nunca foram utilizados em P3
        List<ItemRelatorio> nuncaUtilizados = new ArrayList<>();
        if (excelService.arquivoExiste("planilha3.xlsx")) {
            List<TecidoUtilizado> utilizados3 = excelService.lerPlanilha3();
            Map<String, Set<String>> mapUtilizados3 = new LinkedHashMap<>();
            for (TecidoUtilizado tu : utilizados3) {
                String modeloNorm = tu.modelo().trim().toLowerCase();
                if (!modeloNorm.isBlank()) {
                    mapUtilizados3
                            .computeIfAbsent(tu.codigoNormalizado(), k -> new LinkedHashSet<>())
                            .add(modeloNorm);
                }
            }
            for (Map.Entry<String, TecidoPlanejado> entry : mapPlanejado.entrySet()) {
                String codigo = entry.getKey();
                if (!mapUtilizados3.containsKey(codigo)) {
                    TecidoPlanejado tp = entry.getValue();
                    String marcaNorm = extrairMarcas(tp.linha()).stream()
                            .findFirst()
                            .orElse(tp.linha() != null ? tp.linha().trim() : "");
                    nuncaUtilizados.add(new ItemRelatorio(
                            tp.modelo(),
                            tp.codigoSystextil(),
                            tp.descricaoSystextil(),
                            tp.totalAprovacaoTecido(),
                            tp.aprovCont(),
                            0, 0, false, "", marcaNorm));
                }
            }
            nuncaUtilizados.sort(Comparator.comparing(ItemRelatorio::codigoSystextil));
        }

        return new ResultadoAnalise(excessos, semPlanejamento, nuncaUtilizados);
    }
}
