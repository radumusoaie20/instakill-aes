package com.instakill.user.application;

import com.instakill.common.error.NotFoundException;
import com.instakill.user.domain.User;
import com.instakill.user.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    public User getById(UUID id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
    }

    public Map<UUID, User> getUsersByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return users.findAllById(ids).stream()
                .collect(Collectors.toMap(User::id, Function.identity(), (first, second) -> first));
    }
}
