package sciens.cyrodracs.user;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class UserSeeder {

    private final UserRepository userRepository;

    public UserSeeder(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostConstruct
    @Transactional
    public void seedDefaultUser() {
        if (userRepository.findByUsername(UserService.DEFAULT_USERNAME).isPresent()) {
            return;
        }
        Instant now = Instant.now();
        User user = new User();
        user.setUsername(UserService.DEFAULT_USERNAME);
        user.setDisplayName("Default User");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
    }
}
