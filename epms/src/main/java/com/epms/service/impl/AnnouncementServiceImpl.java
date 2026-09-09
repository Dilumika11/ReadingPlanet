package com.epms.service.impl;

import com.epms.dto.request.AnnouncementRequest;
import com.epms.entity.Announcement;
import com.epms.exception.ResourceNotFoundException;
import com.epms.repository.AnnouncementRepository;
import com.epms.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnnouncementServiceImpl implements AnnouncementService {

    private final AnnouncementRepository announcementRepository;

    @Override
    public List<Announcement> getAll() {
        return announcementRepository.findAll();
    }

    @Override
    public Announcement create(AnnouncementRequest request, Long createdBy) {

        Announcement announcement = new Announcement();
        announcement.setTitle(request.getTitle());
        announcement.setContent(request.getContent());
        announcement.setCreatedBy(createdBy);
        announcement.setStatus("PUBLISHED");
        announcement.setPublishedAt(LocalDateTime.now());

        return announcementRepository.save(announcement);
    }

    @Override
    public Announcement update(Long id, AnnouncementRequest request) {

        Announcement announcement = getById(id);
        announcement.setTitle(request.getTitle());
        announcement.setContent(request.getContent());

        return announcementRepository.save(announcement);
    }

    @Override
    public Announcement archive(Long id) {

        Announcement announcement = getById(id);
        announcement.setStatus("ARCHIVED");

        return announcementRepository.save(announcement);
    }

    private Announcement getById(Long id) {
        return announcementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found: " + id));
    }
}
