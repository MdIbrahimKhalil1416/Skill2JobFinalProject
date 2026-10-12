package com.skill2job.service;

import com.skill2job.audit.Audited;
import com.skill2job.dto.PageResponse;
import com.skill2job.dto.UserResponse;
import com.skill2job.entity.User;
import com.skill2job.enums.Role;
import com.skill2job.exception.ApiException;
import com.skill2job.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class AdminService {

    private final UserRepository userRepository;

    public AdminService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public PageResponse<UserResponse> users(Role role, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        PageRequest pr = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> result = role == null ? userRepository.findAll(pr) : userRepository.findByRole(role, pr);
        return PageResponse.of(result.getContent().stream().map(UserResponse::from).toList(),
                safePage, safeSize, result.getTotalElements());
    }

    /** Activate / deactivate a user. An admin cannot deactivate themselves or another admin. */
    @Audited(action = "USER_ACTIVE_CHANGE", targetType = "USER", targetArg = 1, detailArg = 2)
    public UserResponse setActive(String adminId, String userId, boolean active) {
        User user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User not found."));
        if (user.getId().equals(adminId)) {
            throw ApiException.badRequest("You cannot change your own account status.");
        }
        if (user.getRole() == Role.ADMIN) {
            throw ApiException.badRequest("Admin accounts cannot be deactivated here.");
        }
        user.setActive(active);
        return UserResponse.from(userRepository.save(user));
    }
}
