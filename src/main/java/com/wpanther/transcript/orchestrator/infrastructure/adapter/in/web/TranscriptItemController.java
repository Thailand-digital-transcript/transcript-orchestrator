package com.wpanther.transcript.orchestrator.infrastructure.adapter.in.web;

import com.wpanther.transcript.orchestrator.application.dto.query.TranscriptItemSummary;
import com.wpanther.transcript.orchestrator.application.port.out.XmlPresignPort;
import com.wpanther.transcript.orchestrator.domain.model.TranscriptItem;
import com.wpanther.transcript.orchestrator.domain.repository.TranscriptItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/transcripts") @RequiredArgsConstructor
public class TranscriptItemController {
    private final TranscriptItemRepository itemRepository;
    private final XmlPresignPort xmlPresignPort;

    @GetMapping
    public ResponseEntity<List<TranscriptItemSummary>> pool(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(itemRepository.findUnassigned(size, page * size).stream()
            .map(TranscriptItemSummary::from).toList());
    }

    @GetMapping("/{id}/xml")
    public ResponseEntity<Map<String, String>> xmlUrl(@PathVariable UUID id) {
        TranscriptItem item = itemRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Item not found: " + id));
        String key = item.currentSigningStorageKey();
        if (key == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("url", xmlPresignPort.presign(key)));
    }
}
