package com.epms.service;

import com.epms.dto.request.AnnouncementRequest;
import com.epms.entity.Announcement;

import java.util.List;

public interface AnnouncementService {

    List<Announcement> getAll();

    Announcement create(AnnouncementRequest request, Long createdBy);

    Announcement update(Long id, AnnouncementRequest request);

    void delete(Long id);
}
