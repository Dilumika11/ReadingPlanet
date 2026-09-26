package com.epms.controller;

import com.epms.entity.BookDesign;
import com.epms.entity.ManuscriptFile;
import com.epms.entity.User;
import com.epms.exception.InvalidRequestException;
import com.epms.exception.ResourceNotFoundException;
import com.epms.repository.ManuscriptFileRepository;
import com.epms.security.service.CurrentUserService;
import com.epms.service.DesignProductionService;
import com.epms.service.DocumentStorageService;
import com.epms.service.ManuscriptWorkflow;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Downloads of private manuscript and design files. Every request checks
 * that the caller may see the manuscript (ManuscriptWorkflow.canRead).
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final ManuscriptFileRepository fileRepository;
    private final DesignProductionService designService;
    private final ManuscriptWorkflow workflow;
    private final DocumentStorageService storage;
    private final CurrentUserService currentUserService;

    @GetMapping("/manuscript-files/{fileId}")
    public ResponseEntity<Resource> manuscriptFile(@PathVariable Long fileId, Authentication auth) {
        ManuscriptFile f = fileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));
        User user = currentUserService.getCurrentUser(auth);
        workflow.requireReadable(user, workflow.get(f.getManuscriptId()));
        return send(f.getFilePath(), f.getFileName());
    }

    @GetMapping("/designs/{designId}/{kind}")
    public ResponseEntity<Resource> designFile(@PathVariable Long designId, @PathVariable String kind, Authentication auth) {
        BookDesign d = designService.design(designId);
        User user = currentUserService.getCurrentUser(auth);
        workflow.requireReadable(user, workflow.get(d.getManuscriptId()));
        String path = switch (kind) {
            case "cover" -> d.getCoverFilePath();
            case "layout" -> d.getLayoutFilePath();
            case "print" -> d.getPrintFilePath();
            default -> throw new InvalidRequestException("File kind must be cover, layout or print");
        };
        if (path == null) {
            throw new ResourceNotFoundException("This design version has no " + kind + " file");
        }
        return send(path, "design-v" + d.getDesignVersion() + "-" + kind + path.substring(path.lastIndexOf('.')));
    }

    private ResponseEntity<Resource> send(String relative, String downloadName) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(DocumentStorageService.contentTypeFor(relative)))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + downloadName.replace("\"", "") + "\"")
                .body(new FileSystemResource(storage.resolve(relative)));
    }
}
