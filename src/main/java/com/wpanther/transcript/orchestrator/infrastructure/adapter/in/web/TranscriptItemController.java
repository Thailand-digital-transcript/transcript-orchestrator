package com.wpanther.transcript.orchestrator.infrastructure.adapter.in.web;

import com.wpanther.transcript.orchestrator.application.dto.query.TranscriptItemSummary;
import com.wpanther.transcript.orchestrator.application.port.out.XmlPresignPort;
import com.wpanther.transcript.orchestrator.application.port.out.XmlReadPort;
import com.wpanther.transcript.orchestrator.domain.exception.TranscriptItemNotFoundException;
import com.wpanther.transcript.orchestrator.domain.model.TranscriptItem;
import com.wpanther.transcript.orchestrator.domain.repository.TranscriptItemRepository;
import com.wpanther.transcript.orchestrator.infrastructure.config.CallerContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transcripts")
@RequiredArgsConstructor
public class TranscriptItemController {
    private final TranscriptItemRepository itemRepository;
    private final XmlPresignPort xmlPresignPort;
    private final XmlReadPort xmlReadPort;
    private final CallerContext caller;

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

    /**
     * Streams the item's current signing XML payload (the latest produced key)
     * from S3/MinIO as {@code application/xml} (A10). Used by the approval UI
     * to render the transcript inline without a presigned redirect.
     *
     * <p>Cross-institution JWT callers get a 404 indistinguishable from a
     * genuine not-found (privacy: spec §4.4). The {@link StreamingResponseBody}
     * lambda closes the {@code ResponseInputStream} via try-with-resources so
     * the underlying AWS SDK HTTP connection is released back to the pool
     * even on mid-stream client disconnects.
     */
    @GetMapping(value = "/{id}/content", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<StreamingResponseBody> content(@PathVariable UUID id) {
        TranscriptItem item = itemRepository.findById(id)
            .orElseThrow(() -> new TranscriptItemNotFoundException("Item not found: " + id));
        if (caller.institutionCode().filter(c -> !c.equals(item.getInstitutionCode())).isPresent()) {
            throw new TranscriptItemNotFoundException("Item not found: " + id);
        }
        String key = item.currentSigningStorageKey();
        if (key == null) {
            throw new TranscriptItemNotFoundException("No signing XML for item: " + id);
        }
        StreamingResponseBody body = out -> {
            // MUST close the S3 stream (releases the HTTP connection).
            try (var in = xmlReadPort.getObjectStream(key)) {
                in.transferTo(out);
            }
        };
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(body);
    }

    @ExceptionHandler(TranscriptItemNotFoundException.class)
    ResponseEntity<Void> handleItemNotFound(TranscriptItemNotFoundException ex) {
        return ResponseEntity.notFound().build();
    }
}
