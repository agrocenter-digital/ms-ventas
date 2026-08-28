package com.agrocenter.ms_ventas.service;

import com.agrocenter.ms_ventas.dto.ItemVentaRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

@Component
public class RequestHasher {

    public String hash(List<ItemVentaRequest> items) {
        String canonical = items.stream()
                .sorted(Comparator.comparing(ItemVentaRequest::productoId))
                .map(item -> item.productoId() + ":" + item.cantidad())
                .reduce((left, right) -> left + ";" + right)
                .orElse("");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no esta disponible", exception);
        }
    }
}
