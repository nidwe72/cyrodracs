package sciens.cyrodracs;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class HelloController {

    private final Random random = new Random();

    @GetMapping("/hello")
    public Map<String, Object> hello() {
        int number = random.nextInt(10000);
        return Map.of(
                "message", "Hello World",
                "number", number
        );
    }

    private static final String[] PARENT_WORDS = {
        "apple", "banana", "cherry", "grape", "mango",
        "orange", "peach", "pear", "plum", "strawberry",
        "book", "card", "chair", "door", "house",
        "lamp", "table", "window", "arrow", "button",
        "cable", "diamond", "engine", "filter", "garden",
        "hammer", "island", "jacket", "kettle", "ladder",
        "mirror", "needle", "pillow", "rocket", "socket",
        "tunnel", "umbrella", "valley", "wallet", "zipper"
    };

    private static final String[] CHILD_WORDS = {
        "alpha", "beta", "gamma", "delta", "epsilon"
    };

    @GetMapping("/tree")
    public TestNode tree() {
        List<TestNode> children = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String parentWord = PARENT_WORDS[i % PARENT_WORDS.length];
            List<TestNode> grandchildren = new ArrayList<>();
            for (int j = 0; j < 5; j++) {
                grandchildren.add(new TestNode("%s-%d".formatted(CHILD_WORDS[j], i), List.of()));
            }
            children.add(new TestNode("%s-%d".formatted(parentWord, i), grandchildren));
        }
        return new TestNode("root", children);
    }
}
