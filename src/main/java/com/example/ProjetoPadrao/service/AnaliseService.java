package com.example.ProjetoPadrao.service;

import com.example.ProjetoPadrao.model.ColecaoInfo;
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

    @Autowired
    private ColecaoService colecaoService;

    // ── Análise por coleção ─────────────────────────────────────────────────

    public ResultadoAnalise analisar(String slug) throws IOException {
        return calcularResultado(
                excelService.lerPlanilha1(slug),
                excelService.lerPlanilha2(slug),
                excelService.arquivoExisteColecao(slug, "planilha3.xlsx")
                        ? excelService.lerPlanilha3(slug) : null,
                ""
        );
    }

    public ResultadoAnalise analisarColecaoCompleta(String slug) throws IOException {
        return calcularResultadoCC(
                excelService.lerPlanilha1(slug),
                excelService.lerPlanilha3(slug),
                ""
        );
    }

    // ── Contagem de referências por tecido (P3) ────────────────────────────

    public Map<String, Integer> contarReferenciasP3(String slug) throws IOException {
        return agruparReferencias(excelService.lerPlanilha3(slug));
    }

    public Map<String, Integer> contarReferenciasP3Todas() {
        Map<String, Set<String>> porCodigo = new LinkedHashMap<>();
        int[] idx = {0};
        for (ColecaoInfo c : colecaoService.listarColecoes()) {
            if (!c.p3Existe()) continue;
            try {
                for (TecidoUtilizado tu : excelService.lerPlanilha3(c.slug())) {
                    String cod = tu.codigoNormalizado();
                    if (!cod.isBlank()) {
                        String mod = tu.modelo().trim().toLowerCase();
                        String chave = mod.isBlank() ? "#" + idx[0] : mod;
                        porCodigo.computeIfAbsent(cod, k -> new LinkedHashSet<>()).add(chave);
                    }
                    idx[0]++;
                }
            } catch (IOException ignored) {}
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        porCodigo.forEach((k, v) -> result.put(k, v.size()));
        return result;
    }

    private Map<String, Integer> agruparReferencias(List<TecidoUtilizado> lista) {
        Map<String, Set<String>> porCodigo = new LinkedHashMap<>();
        for (int i = 0; i < lista.size(); i++) {
            TecidoUtilizado tu = lista.get(i);
            String cod = tu.codigoNormalizado();
            if (!cod.isBlank()) {
                String mod = tu.modelo().trim().toLowerCase();
                String chave = mod.isBlank() ? "#" + i : mod;
                porCodigo.computeIfAbsent(cod, k -> new LinkedHashSet<>()).add(chave);
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        porCodigo.forEach((k, v) -> result.put(k, v.size()));
        return result;
    }

    // ── Análise "Todas as coleções" ─────────────────────────────────────────

    public ResultadoAnalise analisarTodas() throws IOException {
        List<ItemRelatorio> excessos = new ArrayList<>();
        List<ItemRelatorio> semPlanejamento = new ArrayList<>();
        List<ItemRelatorio> nuncaUtilizados = new ArrayList<>();
        for (ColecaoInfo c : colecaoService.listarColecoes()) {
            if (!c.p1Existe() || !c.p2Existe()) continue;
            ResultadoAnalise r = calcularResultado(
                    excelService.lerPlanilha1(c.slug()),
                    excelService.lerPlanilha2(c.slug()),
                    c.p3Existe() ? excelService.lerPlanilha3(c.slug()) : null,
                    c.nomeOriginal()
            );
            excessos.addAll(r.excessos());
            semPlanejamento.addAll(r.semPlanejamento());
            nuncaUtilizados.addAll(r.nuncaUtilizados());
        }
        return new ResultadoAnalise(excessos, semPlanejamento, nuncaUtilizados);
    }

    public ResultadoAnalise analisarColecaoCompletaTodas() throws IOException {
        List<ItemRelatorio> excessos = new ArrayList<>();
        List<ItemRelatorio> semPlanejamento = new ArrayList<>();
        List<ItemRelatorio> nuncaUtilizados = new ArrayList<>();
        for (ColecaoInfo c : colecaoService.listarColecoes()) {
            if (!c.p1Existe() || !c.p3Existe()) continue;
            ResultadoAnalise r = calcularResultadoCC(
                    excelService.lerPlanilha1(c.slug()),
                    excelService.lerPlanilha3(c.slug()),
                    c.nomeOriginal()
            );
            excessos.addAll(r.excessos());
            semPlanejamento.addAll(r.semPlanejamento());
            nuncaUtilizados.addAll(r.nuncaUtilizados());
        }
        return new ResultadoAnalise(excessos, semPlanejamento, nuncaUtilizados);
    }

    // ── Métodos legados (lêem de uploads/ raiz) ─────────────────────────────

    public ResultadoAnalise analisar() throws IOException {
        return calcularResultado(
                excelService.lerPlanilha1(),
                excelService.lerPlanilha2(),
                excelService.arquivoExiste("planilha3.xlsx") ? excelService.lerPlanilha3() : null,
                ""
        );
    }

    public ResultadoAnalise analisarColecaoCompleta() throws IOException {
        return calcularResultadoCC(
                excelService.lerPlanilha1(),
                excelService.lerPlanilha3(),
                ""
        );
    }

    // ── Lógica central ──────────────────────────────────────────────────────

    private ResultadoAnalise calcularResultado(
            List<TecidoPlanejado> planejados,
            List<TecidoUtilizado> utilizados,
            List<TecidoUtilizado> utilizados3,
            String colecaoLabel) throws IOException {

        Map<String, TecidoPlanejado> mapPlanejado = new LinkedHashMap<>();
        Map<String, Set<String>> mapLinhas = new LinkedHashMap<>();
        for (TecidoPlanejado tp : planejados) {
            mapPlanejado.putIfAbsent(tp.codigoNormalizado(), tp);
            for (String marca : extrairMarcas(tp.linha())) {
                mapLinhas.computeIfAbsent(tp.codigoNormalizado(), k -> new LinkedHashSet<>()).add(marca);
            }
        }

        Map<String, Set<String>> mapUtilizados = new LinkedHashMap<>();
        Map<String, Set<String>> mapMarcas = new LinkedHashMap<>();
        for (TecidoUtilizado tu : utilizados) {
            String modeloNorm = tu.modelo().trim().toLowerCase();
            if (!modeloNorm.isBlank())
                mapUtilizados.computeIfAbsent(tu.codigoNormalizado(), k -> new LinkedHashSet<>()).add(modeloNorm);
            for (String marca : extrairMarcas(tu.marca()))
                mapMarcas.computeIfAbsent(tu.codigoNormalizado(), k -> new LinkedHashSet<>()).add(marca);
        }

        List<ItemRelatorio> excessos = new ArrayList<>();
        for (Map.Entry<String, TecidoPlanejado> entry : mapPlanejado.entrySet()) {
            String codigo = entry.getKey();
            TecidoPlanejado tp = entry.getValue();
            int totalUtilizado = mapUtilizados.getOrDefault(codigo, Collections.emptySet()).size();
            if (totalUtilizado > tp.totalAprovacaoTecido() && tp.totalAprovacaoTecido() > 0) {
                String marcas = String.join(", ", mapMarcas.getOrDefault(codigo, Collections.emptySet()));
                excessos.add(new ItemRelatorio(
                        tp.modelo(), tp.codigoSystextil(), tp.descricaoSystextil(),
                        tp.totalAprovacaoTecido(), tp.aprovCont(),
                        totalUtilizado, totalUtilizado - tp.totalAprovacaoTecido(),
                        false, marcas, "", colecaoLabel));
            }
        }
        excessos.sort(Comparator.comparingInt(ItemRelatorio::diferenca).reversed());

        List<ItemRelatorio> semPlanejamento = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : mapUtilizados.entrySet()) {
            String codigo = entry.getKey();
            if (!mapPlanejado.containsKey(codigo)) {
                String codigoOriginal = utilizados.stream()
                        .filter(u -> u.codigoNormalizado().equals(codigo))
                        .map(TecidoUtilizado::codigoSystextil)
                        .findFirst().orElse(codigo);
                semPlanejamento.add(new ItemRelatorio(
                        "", codigoOriginal, "", 0, "",
                        entry.getValue().size(), entry.getValue().size(), true, "", "", colecaoLabel));
            }
        }
        semPlanejamento.sort(Comparator.comparingInt(ItemRelatorio::totalModeloSomado).reversed());

        List<ItemRelatorio> nuncaUtilizados = new ArrayList<>();
        if (utilizados3 != null) {
            Map<String, Set<String>> mapUtilizados3 = new LinkedHashMap<>();
            for (TecidoUtilizado tu : utilizados3) {
                String modeloNorm = tu.modelo().trim().toLowerCase();
                if (!modeloNorm.isBlank())
                    mapUtilizados3.computeIfAbsent(tu.codigoNormalizado(), k -> new LinkedHashSet<>()).add(modeloNorm);
            }
            for (Map.Entry<String, TecidoPlanejado> entry : mapPlanejado.entrySet()) {
                String codigo = entry.getKey();
                if (!mapUtilizados3.containsKey(codigo)) {
                    TecidoPlanejado tp = entry.getValue();
                    String marcaNorm = String.join(", ", mapLinhas.getOrDefault(codigo, Collections.emptySet()));
                    if (marcaNorm.isBlank()) marcaNorm = tp.linha() != null ? tp.linha().trim() : "";
                    nuncaUtilizados.add(new ItemRelatorio(
                            tp.modelo(), tp.codigoSystextil(), tp.descricaoSystextil(),
                            tp.totalAprovacaoTecido(), tp.aprovCont(),
                            0, 0, false, "", marcaNorm, colecaoLabel));
                }
            }
            nuncaUtilizados.sort(Comparator.comparing(ItemRelatorio::codigoSystextil));
        }

        return new ResultadoAnalise(excessos, semPlanejamento, nuncaUtilizados);
    }

    private ResultadoAnalise calcularResultadoCC(
            List<TecidoPlanejado> planejados,
            List<TecidoUtilizado> utilizados,
            String colecaoLabel) throws IOException {

        Map<String, TecidoPlanejado> mapPlanejado = new LinkedHashMap<>();
        Map<String, Set<String>> mapLinhas = new LinkedHashMap<>();
        for (TecidoPlanejado tp : planejados) {
            mapPlanejado.putIfAbsent(tp.codigoNormalizado(), tp);
            for (String marca : extrairMarcas(tp.linha()))
                mapLinhas.computeIfAbsent(tp.codigoNormalizado(), k -> new LinkedHashSet<>()).add(marca);
        }

        Map<String, Set<String>> mapUtilizados = new LinkedHashMap<>();
        Map<String, Set<String>> mapMarcas = new LinkedHashMap<>();
        for (TecidoUtilizado tu : utilizados) {
            String modeloNorm = tu.modelo().trim().toLowerCase();
            if (!modeloNorm.isBlank())
                mapUtilizados.computeIfAbsent(tu.codigoNormalizado(), k -> new LinkedHashSet<>()).add(modeloNorm);
            for (String marca : extrairMarcas(tu.marca()))
                mapMarcas.computeIfAbsent(tu.codigoNormalizado(), k -> new LinkedHashSet<>()).add(marca);
        }

        List<ItemRelatorio> excessos = new ArrayList<>();
        for (Map.Entry<String, TecidoPlanejado> entry : mapPlanejado.entrySet()) {
            String codigo = entry.getKey();
            TecidoPlanejado tp = entry.getValue();
            int totalUtilizado = mapUtilizados.getOrDefault(codigo, Collections.emptySet()).size();
            if (totalUtilizado > tp.totalAprovacaoTecido() && tp.totalAprovacaoTecido() > 0) {
                String marcas = String.join(", ", mapMarcas.getOrDefault(codigo, Collections.emptySet()));
                excessos.add(new ItemRelatorio(
                        tp.modelo(), tp.codigoSystextil(), tp.descricaoSystextil(),
                        tp.totalAprovacaoTecido(), tp.aprovCont(),
                        totalUtilizado, totalUtilizado - tp.totalAprovacaoTecido(),
                        false, marcas, "", colecaoLabel));
            }
        }
        excessos.sort(Comparator.comparingInt(ItemRelatorio::diferenca).reversed());

        List<ItemRelatorio> semPlanejamento = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : mapUtilizados.entrySet()) {
            String codigo = entry.getKey();
            if (!mapPlanejado.containsKey(codigo)) {
                String codigoOriginal = utilizados.stream()
                        .filter(u -> u.codigoNormalizado().equals(codigo))
                        .map(TecidoUtilizado::codigoSystextil)
                        .findFirst().orElse(codigo);
                semPlanejamento.add(new ItemRelatorio(
                        "", codigoOriginal, "", 0, "",
                        entry.getValue().size(), entry.getValue().size(), true, "", "", colecaoLabel));
            }
        }
        semPlanejamento.sort(Comparator.comparingInt(ItemRelatorio::totalModeloSomado).reversed());

        List<ItemRelatorio> nuncaUtilizados = new ArrayList<>();
        for (Map.Entry<String, TecidoPlanejado> entry : mapPlanejado.entrySet()) {
            String codigo = entry.getKey();
            if (!mapUtilizados.containsKey(codigo)) {
                TecidoPlanejado tp = entry.getValue();
                String marcaNorm = String.join(", ", mapLinhas.getOrDefault(codigo, Collections.emptySet()));
                if (marcaNorm.isBlank()) marcaNorm = tp.linha() != null ? tp.linha().trim() : "";
                nuncaUtilizados.add(new ItemRelatorio(
                        tp.modelo(), tp.codigoSystextil(), tp.descricaoSystextil(),
                        tp.totalAprovacaoTecido(), tp.aprovCont(),
                        0, 0, false, "", marcaNorm, colecaoLabel));
            }
        }
        nuncaUtilizados.sort(Comparator.comparing(ItemRelatorio::codigoSystextil));

        return new ResultadoAnalise(excessos, semPlanejamento, nuncaUtilizados);
    }
}
