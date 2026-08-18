package pravCode.SortKut.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pravCode.SortKut.dto.PasteRequest;
import pravCode.SortKut.dto.PasteResponse;
import pravCode.SortKut.entity.Paste;
import pravCode.SortKut.service.PasteService;

@RestController
@RequestMapping("/api/paste")
public class PasteRestController {

    private final PasteService pasteService;

    @Autowired
    public PasteRestController(PasteService pasteService) {
        this.pasteService = pasteService;
    }

    @PostMapping
    public ResponseEntity<?> createPaste(@Valid @RequestBody PasteRequest request) {
        try {
            Paste paste = pasteService.createPaste(request);
            return ResponseEntity.ok(new PasteResponse(paste.getSlug()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to share paste: " + e.getMessage());
        }
    }
}
