package com.epms.service.impl;

import com.epms.dto.request.AnnouncementRequest;
import com.epms.entity.Announcement;
import com.epms.enums.Role;
import com.epms.exception.InvalidRequestException;
import com.epms.exception.ResourceNotFoundException;
import com.epms.repository.AnnouncementRepository;
import com.epms.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnnouncementServiceImpl implements AnnouncementService {

    private final AnnouncementRepository announcementRepository;

    @Override
    public List<Announcement> getAll() {
        return announcementRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public List<Announcement> getActiveFor(Role role) {
        return announcementRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(a -> "ACTIVE".equals(a.getStatus()))
                .filter(a -> "ALL".equals(a.getAudience()) || (role != null && role.name().equals(a.getAudience())))
                .collect(Collectors.toList());
    }

    @Override
    public Announcement create(AnnouncementRequest request, Long createdBy) {

        Announcement announcement = new Announcement();
        announcement.setCreatedBy(createdBy);
        apply(announcement, request);

        return announcementRepository.save(announcement);
    }

    @Override
    public Announcement update(Long id, AnnouncementRequest request) {

        Announcement announcement = getById(id);
        apply(announcement, request);

        return announcementRepository.save(announcement);
    }

    @Override
    public void delete(Long id) {
        announcementRepository.delete(getById(id));
    }

    private static void apply(Announcement announcement, AnnouncementRequest request) {
        String audience = request.getAudience() == null || request.getAudience().isBlank()
                ? "ALL" : request.getAudience().trim().toUpperCase();
        if (!"ALL".equals(audience) && Arrays.stream(Role.values()).noneMatch(r -> r.name().equals(audience))) {
            throw new InvalidRequestException("Audience must be ALL or a role name (e.g. AUTHOR, FINANCE_STAFF)");
        }
        if (request.getPublishFrom() != null && request.getPublishTo() != null
                && request.getPublishTo().isBefore(request.getPublishFrom())) {
            throw new InvalidRequestException("The last day shown cannot be before the first day");
        }

        announcement.setTitle(request.getTitle().trim());
        announcement.setContent(request.getContent().trim());
        announcement.setAudience(audience);
        if (request.isDraft()) {
            announcement.setPublishedAt(null);
        } else if (request.getPublishFrom() != null) {
            announcement.setPublishedAt(request.getPublishFrom().atStartOfDay());
        } else if (announcement.getPublishedAt() == null) {
            announcement.setPublishedAt(LocalDateTime.now());
        }
        announcement.setExpiresAt(request.getPublishTo() == null ? null : request.getPublishTo().atTime(LocalTime.of(23, 59, 59)));
    }

    private Announcement getById(Long id) {
        return announcementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found: " + id));
    }
}
