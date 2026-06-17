package com.wpanther.transcript.orchestrator.infrastructure.adapter.in.web;

import com.wpanther.transcript.orchestrator.application.dto.command.AssignItemsCommand;
import com.wpanther.transcript.orchestrator.application.dto.command.CreateBatchCommand;
import com.wpanther.transcript.orchestrator.application.dto.query.BatchDetail;
import com.wpanther.transcript.orchestrator.application.dto.query.BatchSummary;
import com.wpanther.transcript.orchestrator.application.dto.query.TranscriptItemSummary;
import com.wpanther.transcript.orchestrator.application.usecase.AssignItemsUseCase;
import com.wpanther.transcript.orchestrator.application.usecase.CloseBatchUseCase;
import com.wpanther.transcript.orchestrator.application.usecase.CreateBatchUseCase;
import com.wpanther.transcript.orchestrator.application.usecase.UnassignItemUseCase;
import com.wpanther.transcript.orchestrator.domain.exception.BatchNotFoundException;
import com.wpanther.transcript.orchestrator.domain.exception.EmptyBatchException;
import com.wpanther.transcript.orchestrator.domain.exception.InstitutionMismatchException;
import com.wpanther.transcript.orchestrator.domain.exception.InvalidBatchStateException;
import com.wpanther.transcript.orchestrator.domain.model.Batch;
import com.wpanther.transcript.orchestrator.domain.model.BatchStatus;
import com.wpanther.transcript.orchestrator.domain.repository.BatchRepository;
import com.wpanther.transcript.orchestrator.domain.repository.TranscriptItemRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/batches")
@RequiredArgsConstructor
public class BatchController {
    private final CreateBatchUseCase createBatchUseCase;
    private final AssignItemsUseCase assignItemsUseCase;
    private final UnassignItemUseCase unassignItemUseCase;
    private final CloseBatchUseCase closeBatchUseCase;
    // N3 note: query endpoints deliberately bypass the use case layer — reads
    // don't orchestrate domain logic, only project the aggregate for the API.
    private final BatchRepository batchRepository;
    private final TranscriptItemRepository itemRepository;

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody CreateBatchCommand cmd) {
        Batch b = createBatchUseCase.create(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(
            Map.of("batchId", b.getId(), "name", b.getName(), "status", b.getStatus()));
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<Void> assignItems(@PathVariable UUID id,
            @Valid @RequestBody AssignItemsCommand cmd) {
        assignItemsUseCase.assign(id, cmd.getItemIds());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ResponseEntity<Void> unassignItem(@PathVariable UUID id, @PathVariable UUID itemId) {
        unassignItemUseCase.unassign(id, itemId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<Map<String, Object>> close(@PathVariable UUID id,
            @RequestHeader(value = "X-Closed-By", defaultValue = "system") String closedBy) {
        Batch b = closeBatchUseCase.close(id, closedBy);
        return ResponseEntity.ok(Map.of("batchId", b.getId(), "status", b.getStatus()));
    }

    @GetMapping
    public ResponseEntity<List<BatchSummary>> list(@RequestParam(required = false) String status) {
        List<BatchStatus> statuses = status != null
            ? List.of(BatchStatus.valueOf(status)) : List.of(BatchStatus.values());
        return ResponseEntity.ok(
            batchRepository.findByStatusIn(statuses, 100).stream().map(BatchSummary::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BatchDetail> getDetail(@PathVariable UUID id) {
        Batch batch = batchRepository.findById(id)
            .orElseThrow(() -> new BatchNotFoundException(id.toString()));
        List<TranscriptItemSummary> items = itemRepository.findByBatchId(id).stream()
            .map(TranscriptItemSummary::from).toList();
        return ResponseEntity.ok(BatchDetail.from(batch, items));
    }

    @ExceptionHandler(BatchNotFoundException.class)
    ResponseEntity<Map<String, String>> handleNotFound(BatchNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler({InvalidBatchStateException.class, EmptyBatchException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> handleConflict(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(InstitutionMismatchException.class)
    ResponseEntity<Map<String, String>> handleForbidden(InstitutionMismatchException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }
}
