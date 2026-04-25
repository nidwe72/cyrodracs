package sciens.cyrodracs.user;

import org.springframework.stereotype.Service;

@Service
public class UserService {

    public static final String DEFAULT_USERNAME = "default";

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getCurrentUser() {
        return userRepository.findByUsername(DEFAULT_USERNAME)
                .orElseThrow(() -> new IllegalStateException(
                        "DEFAULT_USER row not present — UserSeeder must run before UserService is used."));
    }

    public User findById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }
}
