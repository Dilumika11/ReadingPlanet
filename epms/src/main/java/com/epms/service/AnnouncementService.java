package com.epms.service;

import com.epms.dto.request.AnnouncementRequest;
import com.epms.entity.Announcement;
import com.epms.enums.Role;

import java.util.List;

public interface AnnouncementService {

    List<Announcement> getAll();

    /** Published, not expired, and addressed to ALL or to the given role. */
    List<Announcement> getActiveFor(Role role);

    Announcement create(AnnouncementRequest request, Long createdBy);

    Announcement update(Long id, AnnouncementRequest request);

    void delete(Long id);
}
