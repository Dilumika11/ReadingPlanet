package com.epms.service;

import com.epms.exception.InvalidRequestException;
import com.epms.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Stores private documents (manuscripts, designs) outside the publicly
 * served uploads folder. Files are only ever returned through
 * authenticated API endpoints that check who may read them.
 */
@Service
public class DocumentStorageService {

    /** Manuscripts and supporting documents. */
    public static final Map<String, String> DOCUMENT_TYPES = Map.of(
            "application/pdf", "pdf",
            "application/msword", "doc",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx",
            "application/vnd.oasis.opendocument.text", "odt",
            "text/plain", "txt");

    /** Cover artwork. */
    public static final Map<String, String> IMAGE_TYPES = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");

    /** Layouts and print-ready files. */
    public static final Map<String, String> PDF_TYPES = Map.of("application/pdf", "pdf");

    private final Path root;

    public DocumentStorageService(@Value("${epms.documents.dir:${user.home}/.readingplanet/documents}") String dir) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
    }

    /**
     * Validates type and size and stores the file under {@code folder}.
     * Returns the path relative to the documents root, to keep in the database.
     */
    public String store(MultipartFile file, String folder, Map<String, String> allowedTypes, long maxBytes, String label) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Choose a " + label + " file to upload");
        }
        if (file.getSize() > maxBytes) {
            throw new InvalidRequestException(capitalize(label) + " files must be " + (maxBytes / (1024 * 1024)) + " MB or smaller");
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String ext = allowedTypes.get(type);
        if (ext == null) {
            ext = extensionFallback(file.getOriginalFilename(), allowedTypes);
        }
        if (ext == null) {
            throw new InvalidRequestException(capitalize(label) + " must be one of: "
                    + String.join(", ", allowedTypes.values()).toUpperCase(Locale.ROOT) + " (got " + type + ")");
        }
        String relative = folder + "/" + UUID.randomUUID().toString().substring(0, 12) + "." + ext;
        Path target = root.resolve(relative).normalize();
        try {
            Files.createDirectories(target.getParent());
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store the " + label, e);
        }
        return relative;
    }

    /** The stored file on disk; 404 if it is missing. */
    public Path resolve(String relative) {
        if (relative == null) {
            throw new ResourceNotFoundException("File not found");
        }
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root) || !Files.exists(path)) {
            throw new ResourceNotFoundException("File not found");
        }
        return path;
    }

    public static String contentTypeFor(String relative) {
        String name = relative == null ? "" : relative.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (name.endsWith(".doc")) return "application/msword";
        if (name.endsWith(".odt")) return "application/vnd.oasis.opendocument.text";
        if (name.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }

    // Browsers sometimes send application/octet-stream for .docx; trust a known extension then.
    private static String extensionFallback(String originalName, Map<String, String> allowed) {
        if (originalName == null || !originalName.contains(".")) return null;
        String ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if ("jpeg".equals(ext)) ext = "jpg";
        return allowed.containsValue(ext) ? ext : null;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
