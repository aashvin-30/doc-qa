package com.docqa.document;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Document upload(@RequestParam("file") MultipartFile file,
                           @RequestParam(value = "conversationId", required = false) UUID conversationId)
            throws IOException {
        return service.upload(file, conversationId);
    }

    @GetMapping
    public List<Document> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public Document get(@PathVariable UUID id) {
        return service.get(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
