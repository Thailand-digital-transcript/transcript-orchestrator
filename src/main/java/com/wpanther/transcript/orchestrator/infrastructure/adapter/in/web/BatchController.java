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
import com.wpanther.transcript.orchestrator.infrastructure.config.CallerContext;
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
import org.springframework.web.server.ResponseStatusException;
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
    private final CallerContext caller;

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

    /**
     * Pinned list contract (A10, spec §4.3 / B9 / B11):
     *
     * <ul>
     *   <li>If <em>either</em> {@code page} or {@code size} is present, ignore
     *       {@code status} and return ALL statuses paginated (defaults
     *       {@code page=0}, {@code size=20}). This serves the B11 monitor, which
     *       sends only {@code page&size}.</li>
     *   <li>Otherwise honour {@code status} (single status, or all statuses when
     *       {@code null}) capped at 100. This serves the B9 queue, which sends
     *       only {@code status}.</li>
     * </ul>
     *
     * <p>JWT callers (with an {@code institution_code} claim) are scoped to
     * their institution; callers without an institution claim see the unscoped
     * read (the existing service-side behaviour, retained for the monitor
     * consumer).
     */
    @GetMapping
    public ResponseEntity<List<BatchSummary>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        List<Batch> batches;
        var institution = caller.institutionCode();

        if (page != null || size != null) {
            // Paginated monitor read: ignore status, all statuses paginated.
            int p = page == null ? 0 : Math.max(page, 0);
            int s = size == null ? 20 : Math.max(size, 1);
            if (institution.isEmpty() && p > 0) {
                // Unscoped callers (no institution_code claim) have no real
                // paginated query — only a capped first-page read exists.
                // Rejecting page>0 avoids silently returning page 0 for every
                // page (which looks like working pagination but isn't).
                // Production monitor callers are JWT-scoped.
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Pagination beyond page 0 requires institution-scoped (JWT) authentication");
            }
            batches = institution
                .<List<Batch>>map(inst -> batchRepository.findByInstitutionCode(inst, p, s))
                .orElseGet(() -> batchRepository.findByStatusIn(List.of(BatchStatus.values()), s));
        } else {
            // Queue read: honour status (single or all), capped at 100.
            List<BatchStatus> statuses = status != null
                ? List.of(parseStatus(status)) : List.of(BatchStatus.values());
            batches = institution
                .<List<Batch>>map(inst -> batchRepository.findByStatusInAndInstitutionCode(statuses, inst, 100))
                .orElseGet(() -> batchRepository.findByStatusIn(statuses, 100));
        }
        return ResponseEntity.ok(batches.stream().map(BatchSummary::from).toList());
    }

    /** Parse a status query param, returning 400 (not 500) for an unknown value. */
    private static BatchStatus parseStatus(String status) {
        try {
            return BatchStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status: " + status);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<BatchDetail> getDetail(@PathVariable UUID id) {
        Batch batch = batchRepository.findById(id)
            .orElseThrow(() -> new BatchNotFoundException(id.toString()));
        // Cross-institution JWT callers get a 404 (BatchNotFoundException, mapped
        // below) so the detail cannot leak across institutions. Callers without
        // an institution_code claim and JWT callers in the matching institution
        // proceed. Privacy: spec §4.4 — 404 over 403 to avoid confirming existence.
        if (caller.institutionCode().filter(c -> !c.equals(batch.getInstitutionCode())).isPresent()) {
            throw new BatchNotFoundException(id.toString());
        }
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
