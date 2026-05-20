package com.example.ProjetoPadrao.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ObservacaoService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private Path getPath() {
        return Paths.get(System.getProperty("user.dir"), uploadDir, "observacoes.txt");
    }

    public Map<String, String> carregar() throws IOException {
        Path p = getPath();
        Map<String, String> map = new LinkedHashMap<>();
        if (!Files.exists(p)) return map;
        for (String line : Files.readAllLines(p)) {
            int sep = line.indexOf('=');
            if (sep > 0) {
                map.put(line.substring(0, sep), line.substring(sep + 1));
            }
        }
        return map;
    }

    public void salvar(String codigo, String texto) throws IOException {
        Files.createDirectories(getPath().getParent());
        Map<String, String> map = carregar();
        if (texto == null || texto.isBlank()) {
            map.remove(codigo);
        } else {
            map.put(codigo, texto.trim());
        }
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, String> e : map.entrySet()) {
            lines.add(e.getKey() + "=" + e.getValue());
        }
        Files.write(getPath(), lines);
    }
}
